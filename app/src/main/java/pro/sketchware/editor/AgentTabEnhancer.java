package pro.sketchware.editor;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.tabs.TabLayout;

import java.util.Map;
import java.util.WeakHashMap;

import pro.sketchware.R;

/** Adds the Agent page beside Components and switches it into a clean immersive workspace. */
public final class AgentTabEnhancer implements Application.ActivityLifecycleCallbacks {
    private static final String FRAGMENT_TAG = "daygreen_gemini_agent_tab";
    private static final Map<Activity, AgentTabEnhancer> INSTANCES = new WeakHashMap<>();
    private final Map<Activity, Integer> attachedContainers = new WeakHashMap<>();
    private final Map<Activity, Boolean> immersive = new WeakHashMap<>();

    @Override public void onActivityCreated(@NonNull Activity activity, Bundle state) {
        if (!(activity instanceof FragmentActivity) || !"com.besome.sketch.design.DesignActivity".equals(activity.getClass().getName())) return;
        INSTANCES.put(activity, this);
        activity.getWindow().getDecorView().post(() -> attach((FragmentActivity) activity));
    }

    public static void exitAgent(Activity activity) {
        AgentTabEnhancer hook = INSTANCES.get(activity);
        if (hook != null) hook.restore(activity);
    }

    private void attach(FragmentActivity activity) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        TabLayout tabs = activity.findViewById(R.id.tab_layout); ViewPager pager = activity.findViewById(R.id.viewpager); ViewGroup host = activity.findViewById(R.id.layout_coordinator);
        if (tabs == null || pager == null || host == null || attachedContainers.containsKey(activity)) return;
        FrameLayout agentContainer = new FrameLayout(activity); agentContainer.setId(View.generateViewId()); agentContainer.setVisibility(View.GONE); host.addView(agentContainer, new ViewGroup.LayoutParams(-1, -1)); attachedContainers.put(activity, agentContainer.getId());
        String projectId = activity.getIntent().getStringExtra("sc_id"); TextView fileName = activity.findViewById(R.id.file_name); String selectedFile = fileName == null ? "" : fileName.getText().toString();
        FragmentManager manager = activity.getSupportFragmentManager(); Fragment existing = manager.findFragmentByTag(FRAGMENT_TAG); if (existing != null) manager.beginTransaction().remove(existing).commitNowAllowingStateLoss(); manager.beginTransaction().replace(agentContainer.getId(), AgentFragmentV2.newInstance(projectId, selectedFile), FRAGMENT_TAG).commitNowAllowingStateLoss();
        int currentPage = pager.getCurrentItem(); CharSequence[] titles = new CharSequence[Math.min(3, tabs.getTabCount())]; for (int i = 0; i < titles.length; i++) { TabLayout.Tab t = tabs.getTabAt(i); titles[i] = t == null || t.getText() == null ? String.valueOf(i + 1) : t.getText(); }
        tabs.setupWithViewPager(null); tabs.removeAllTabs(); for (CharSequence title : titles) tabs.addTab(tabs.newTab().setText(title), false); TabLayout.Tab agentTab = tabs.newTab().setText("Agent"); tabs.addTab(agentTab, false);
        final boolean[] syncing = {false};
        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) {
                if (syncing[0]) return; int position = tab.getPosition();
                if (tab == agentTab) { pager.setVisibility(View.GONE); agentContainer.setVisibility(View.VISIBLE); enterImmersive(activity); }
                else { agentContainer.setVisibility(View.GONE); pager.setVisibility(View.VISIBLE); pager.setCurrentItem(Math.min(position, Math.max(0, pager.getAdapter() == null ? 0 : pager.getAdapter().getCount() - 1)), false); restoreChrome(activity); }
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) { if (tab == agentTab) agentContainer.setVisibility(View.GONE); }
            @Override public void onTabReselected(TabLayout.Tab tab) { }
        });
        pager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() { @Override public void onPageSelected(int position) { if (position < 0 || position >= tabs.getTabCount() - 1) return; syncing[0] = true; TabLayout.Tab t = tabs.getTabAt(position); if (t != null) t.select(); syncing[0] = false; agentContainer.setVisibility(View.GONE); pager.setVisibility(View.VISIBLE); restoreChrome(activity); } });
        TabLayout.Tab selected = tabs.getTabAt(Math.min(currentPage, tabs.getTabCount() - 2)); if (selected != null) selected.select();
    }

    private void enterImmersive(Activity activity) { immersive.put(activity, true); View toolbar = activity.findViewById(R.id.toolbar); View tabs = activity.findViewById(R.id.tab_layout); View bottom = activity.findViewById(R.id.bottom_bar); if (toolbar != null) toolbar.setVisibility(View.GONE); if (tabs != null) tabs.setVisibility(View.GONE); if (bottom != null) bottom.setVisibility(View.GONE); }
    private void restoreChrome(Activity activity) { immersive.put(activity, false); View toolbar = activity.findViewById(R.id.toolbar); View tabs = activity.findViewById(R.id.tab_layout); View bottom = activity.findViewById(R.id.bottom_bar); if (toolbar != null) toolbar.setVisibility(View.VISIBLE); if (tabs != null) tabs.setVisibility(View.VISIBLE); if (bottom != null) bottom.setVisibility(View.VISIBLE); }
    private void restore(Activity activity) { restoreChrome(activity); TabLayout tabs = activity.findViewById(R.id.tab_layout); ViewPager pager = activity.findViewById(R.id.viewpager); if (tabs != null && tabs.getTabCount() > 1) { TabLayout.Tab normal = tabs.getTabAt(Math.min(pager == null ? 0 : pager.getCurrentItem(), tabs.getTabCount() - 2)); if (normal != null) normal.select(); } }

    @Override public void onActivityStarted(@NonNull Activity activity) { }
    @Override public void onActivityResumed(@NonNull Activity activity) { }
    @Override public void onActivityPaused(@NonNull Activity activity) { }
    @Override public void onActivityStopped(@NonNull Activity activity) { }
    @Override public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) { }
    @Override public void onActivityDestroyed(@NonNull Activity activity) { attachedContainers.remove(activity); immersive.remove(activity); INSTANCES.remove(activity); }
}
