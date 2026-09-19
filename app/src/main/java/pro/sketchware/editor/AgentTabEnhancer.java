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
import androidx.core.graphics.Insets;
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

    public static void exitAgent(Activity activity) { AgentTabEnhancer hook = INSTANCES.get(activity); if (hook != null) hook.restore(activity); }

    private void attach(FragmentActivity activity) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        activity.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        TabLayout tabs = activity.findViewById(R.id.tab_layout);
        ViewPager pager = activity.findViewById(R.id.viewpager);
        ViewGroup host = activity.findViewById(R.id.layout_coordinator);
        if (tabs == null || pager == null || host == null || attachedContainers.containsKey(activity)) return;

        FrameLayout agentContainer = new FrameLayout(activity);
        agentContainer.setId(View.generateViewId());
        agentContainer.setVisibility(View.GONE);
        agentContainer.setClipToPadding(true);
        agentContainer.setClipChildren(true);
        agentContainer.setFitsSystemWindows(false);
        host.addView(agentContainer, new ViewGroup.LayoutParams(-1, -1));
        attachedContainers.put(activity, agentContainer.getId());

        String projectId = activity.getIntent().getStringExtra("sc_id");
        TextView fileName = activity.findViewById(R.id.file_name);
        String selectedFile = fileName == null ? "" : fileName.getText().toString();
        FragmentManager manager = activity.getSupportFragmentManager();
        Fragment existing = manager.findFragmentByTag(FRAGMENT_TAG);
        if (existing != null) manager.beginTransaction().remove(existing).commitNowAllowingStateLoss();
        manager.beginTransaction().replace(agentContainer.getId(), AgentFragmentV2.newInstance(projectId, selectedFile), FRAGMENT_TAG).commitNowAllowingStateLoss();

        MaterialButton undo = new MaterialButton(activity);
        undo.setText("↶");
        undo.setTextSize(18);
        undo.setTextColor(ColorCompat.text(activity));
        undo.setContentDescription("Undo last Agent change");
        undo.setMinWidth(dp(activity, 48));
        undo.setMinHeight(dp(activity, 48));
        undo.setPadding(0, 0, 0, 0);
        undo.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xCC1D2723));
        FrameLayout.LayoutParams undoParams = new FrameLayout.LayoutParams(dp(activity, 48), dp(activity, 48), Gravity.TOP | Gravity.END);
        undoParams.setMargins(0, dp(activity, 8), dp(activity, 8), 0);
        agentContainer.addView(undo, undoParams);

        ViewCompat.setOnApplyWindowInsetsListener(agentContainer, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.statusBars() | WindowInsetsCompat.Type.displayCutout());
            agentContainer.setPadding(0, 0, 0, 0);
            ViewGroup.LayoutParams raw = undo.getLayoutParams();
            if (raw instanceof FrameLayout.LayoutParams) {
                FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) raw;
                params.topMargin = bars.top + dp(activity, 40);
                params.rightMargin = dp(activity, 8);
                undo.setLayoutParams(params);
            }
            return insets;
        });
        ViewCompat.requestApplyInsets(agentContainer);
        undo.setVisibility(View.GONE);

        int currentPage = pager.getCurrentItem();
        CharSequence[] titles = new CharSequence[Math.min(3, tabs.getTabCount())];
        for (int i = 0; i < titles.length; i++) {
            TabLayout.Tab t = tabs.getTabAt(i);
            titles[i] = t == null || t.getText() == null ? String.valueOf(i + 1) : t.getText();
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
                int position = tab.getPosition();
                if (tab == agentTab) {
                    pager.setVisibility(View.GONE);
                    agentContainer.setVisibility(View.VISIBLE);
                    undo.setVisibility(View.VISIBLE);
                    enterImmersive(activity);
                    ViewCompat.requestApplyInsets(agentContainer);
                } else {
                    agentContainer.setVisibility(View.GONE);
                    pager.setVisibility(View.VISIBLE);
                    undo.setVisibility(View.GONE);
                    pager.setCurrentItem(Math.min(position, Math.max(0, pager.getAdapter() == null ? 0 : pager.getAdapter().getCount() - 1)), false);
                    restoreChrome(activity);
                }
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) { if (tab == agentTab) { agentContainer.setVisibility(View.GONE); undo.setVisibility(View.GONE); } }
            @Override public void onTabReselected(TabLayout.Tab tab) { }
        });
        String undoProjectId = projectId;
        undo.setOnClickListener(v -> { String result = AgentActionExecutor.revertLast(activity, undoProjectId); Toast.makeText(activity, result, Toast.LENGTH_SHORT).show(); });
        pager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override public void onPageSelected(int position) {
                if (position < 0 || position >= tabs.getTabCount() - 1) return;
                syncing[0] = true;
                TabLayout.Tab t = tabs.getTabAt(position);
                if (t != null) t.select();
                syncing[0] = false;
                agentContainer.setVisibility(View.GONE);
                undo.setVisibility(View.GONE);
                pager.setVisibility(View.VISIBLE);
                restoreChrome(activity);
            }
        });
        TabLayout.Tab selected = tabs.getTabAt(Math.min(currentPage, tabs.getTabCount() - 2));
        if (selected != null) selected.select();
    }

    private void enterImmersive(Activity activity) { immersive.put(activity, true); View toolbar = activity.findViewById(R.id.toolbar); View tabs = activity.findViewById(R.id.tab_layout); View bottom = activity.findViewById(R.id.bottom_bar); if (toolbar != null) toolbar.setVisibility(View.GONE); if (tabs != null) tabs.setVisibility(View.GONE); if (bottom != null) bottom.setVisibility(View.GONE); }
    private void restoreChrome(Activity activity) { immersive.put(activity, false); View toolbar = activity.findViewById(R.id.toolbar); View tabs = activity.findViewById(R.id.tab_layout); View bottom = activity.findViewById(R.id.bottom_bar); if (toolbar != null) toolbar.setVisibility(View.VISIBLE); if (tabs != null) tabs.setVisibility(View.VISIBLE); if (bottom != null) bottom.setVisibility(View.VISIBLE); }
    private void restore(Activity activity) { restoreChrome(activity); TabLayout tabs = activity.findViewById(R.id.tab_layout); ViewPager pager = activity.findViewById(R.id.viewpager); if (tabs != null && tabs.getTabCount() > 1) { TabLayout.Tab normal = tabs.getTabAt(Math.min(pager == null ? 0 : pager.getCurrentItem(), tabs.getTabCount() - 2)); if (normal != null) normal.select(); } }
    private static int dp(Activity a, int value) { return Math.round(value * a.getResources().getDisplayMetrics().density); }
    private static final class ColorCompat { static int text(Activity a) { return 0xFFE4EDE7; } }
    @Override public void onActivityStarted(@NonNull Activity activity) { }
    @Override public void onActivityResumed(@NonNull Activity activity) { }
    @Override public void onActivityPaused(@NonNull Activity activity) { }
    @Override public void onActivityStopped(@NonNull Activity activity) { }
    @Override public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) { }
    @Override public void onActivityDestroyed(@NonNull Activity activity) { attachedContainers.remove(activity); immersive.remove(activity); INSTANCES.remove(activity); }
}
