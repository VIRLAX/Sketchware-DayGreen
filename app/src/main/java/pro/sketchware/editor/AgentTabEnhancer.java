package pro.sketchware.editor;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.tabs.TabLayout;

import java.util.Map;
import java.util.WeakHashMap;

import pro.sketchware.R;

/**
 * Installs Dayven AI Agent with fullscreen immersive support (under system status bar/control center).
 */
public final class AgentTabEnhancer implements Application.ActivityLifecycleCallbacks {
    private static final String FRAGMENT_TAG = "daygreen_gemini_agent_tab";
    private static final Map<Activity, AgentTabEnhancer> INSTANCES = new WeakHashMap<>();
    private final Map<Activity, Integer> attachedContainers = new WeakHashMap<>();

    @Override public void onActivityCreated(@NonNull Activity activity, Bundle state) {
        if (!(activity instanceof FragmentActivity) || !"com.besome.sketch.design.DesignActivity".equals(activity.getClass().getName())) return;
        INSTANCES.put(activity, this);
        activity.getWindow().getDecorView().post(() -> attach((FragmentActivity) activity));
    }

    public static void exitAgent(Activity activity) {
        AgentTabEnhancer hook = INSTANCES.get(activity);
        if (hook != null) hook.restore(activity);
    }

    public static void setExpanded(Activity activity, boolean expanded) {
        if (activity == null) return;
        View appBar = activity.findViewById(R.id.tab_layout);
        View bottomBar = activity.findViewById(R.id.bottom_bar);
        View parentAppBar = appBar != null && appBar.getParent() instanceof View ? (View) appBar.getParent() : null;
        View parentBottomBar = bottomBar != null && bottomBar.getParent() instanceof View ? (View) bottomBar.getParent() : null;

        if (parentAppBar != null) {
            parentAppBar.setVisibility(expanded ? View.GONE : View.VISIBLE);
        }
        if (parentBottomBar != null) {
            parentBottomBar.setVisibility(expanded ? View.GONE : View.VISIBLE);
        }
    }

    private void attach(FragmentActivity activity) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        activity.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        TabLayout tabs = activity.findViewById(R.id.tab_layout);
        ViewPager pager = activity.findViewById(R.id.viewpager);
        ViewGroup rootContainer = activity.findViewById(R.id.container);
        if (rootContainer == null) rootContainer = activity.findViewById(R.id.layout_coordinator);
        if (tabs == null || pager == null || rootContainer == null || attachedContainers.containsKey(activity)) return;

        FrameLayout agentContainer = new FrameLayout(activity);
        agentContainer.setId(View.generateViewId());
        agentContainer.setVisibility(View.GONE);
        agentContainer.setClipChildren(true);
        agentContainer.setClipToPadding(true);
        agentContainer.setFitsSystemWindows(false);
        rootContainer.addView(agentContainer, new ViewGroup.LayoutParams(-1, -1));
        attachedContainers.put(activity, agentContainer.getId());

        String projectId = activity.getIntent().getStringExtra("sc_id");
        TextView fileName = activity.findViewById(R.id.file_name);
        String selectedFile = fileName == null ? "" : fileName.getText().toString();
        FragmentManager manager = activity.getSupportFragmentManager();
        Fragment old = manager.findFragmentByTag(FRAGMENT_TAG);
        if (old != null) manager.beginTransaction().remove(old).commitNowAllowingStateLoss();
        manager.beginTransaction().replace(agentContainer.getId(), AgentFragmentV2.newInstance(projectId, selectedFile), FRAGMENT_TAG).commitNowAllowingStateLoss();

        int currentPage = pager.getCurrentItem();
        CharSequence[] titles = new CharSequence[Math.min(3, tabs.getTabCount())];
        for (int i = 0; i < titles.length; i++) {
            TabLayout.Tab tab = tabs.getTabAt(i);
            titles[i] = tab == null || tab.getText() == null ? String.valueOf(i + 1) : tab.getText();
        }
        tabs.setupWithViewPager(null);
        tabs.removeAllTabs();
        for (CharSequence title : titles) tabs.addTab(tabs.newTab().setText(title), false);
        TabLayout.Tab agentTab = tabs.newTab().setText("Agent");
        tabs.addTab(agentTab, false);
        final boolean[] syncing = {false};
        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) {
                if (syncing[0]) return;
                if (tab == agentTab) {
                    pager.setVisibility(View.GONE);
                    agentContainer.setVisibility(View.VISIBLE);
                    setExpanded(activity, true);
                } else {
                    agentContainer.setVisibility(View.GONE);
                    pager.setVisibility(View.VISIBLE);
                    setExpanded(activity, false);
                    pager.setCurrentItem(Math.min(tab.getPosition(), Math.max(0, pager.getAdapter() == null ? 0 : pager.getAdapter().getCount() - 1)), false);
                }
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) { 
                if (tab == agentTab) { 
                    agentContainer.setVisibility(View.GONE); 
                    setExpanded(activity, false);
                } 
            }
            @Override public void onTabReselected(TabLayout.Tab tab) { }
        });

        pager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override public void onPageSelected(int position) {
                if (position < 0 || position >= tabs.getTabCount() - 1) return;
                syncing[0] = true;
                TabLayout.Tab tab = tabs.getTabAt(position);
                if (tab != null) tab.select();
                syncing[0] = false;
                agentContainer.setVisibility(View.GONE);
                setExpanded(activity, false);
                pager.setVisibility(View.VISIBLE);
            }
        });
        TabLayout.Tab selected = tabs.getTabAt(Math.min(currentPage, tabs.getTabCount() - 2));
        if (selected != null) selected.select();
    }

    private void restore(Activity activity) {
        setExpanded(activity, false);
        TabLayout tabs = activity.findViewById(R.id.tab_layout);
        ViewPager pager = activity.findViewById(R.id.viewpager);
        if (tabs != null && tabs.getTabCount() > 1) {
            TabLayout.Tab normal = tabs.getTabAt(Math.min(pager == null ? 0 : pager.getCurrentItem(), tabs.getTabCount() - 2));
            if (normal != null) normal.select();
        }
    }

    private static int dp(Activity activity, int value) { return Math.round(value * activity.getResources().getDisplayMetrics().density); }
    @Override public void onActivityStarted(@NonNull Activity activity) { }
    @Override public void onActivityResumed(@NonNull Activity activity) { }
    @Override public void onActivityPaused(@NonNull Activity activity) { }
    @Override public void onActivityStopped(@NonNull Activity activity) { }
    @Override public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle state) { }
    @Override public void onActivityDestroyed(@NonNull Activity activity) { attachedContainers.remove(activity); INSTANCES.remove(activity); }
}
