package pro.sketchware.editor;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.tabs.TabLayout;
import androidx.viewpager.widget.ViewPager;

import java.util.WeakHashMap;

import pro.sketchware.R;

/** Adds the Agent page beside Components while preserving the existing three-page editor pager. */
public final class AgentTabEnhancer implements Application.ActivityLifecycleCallbacks {
    private static final String FRAGMENT_TAG = "daygreen_gemini_agent_tab";
    private final WeakHashMap<Activity, Integer> attachedContainers = new WeakHashMap<>();

    @Override
    public void onActivityCreated(@NonNull Activity activity, Bundle state) {
        if (!(activity instanceof FragmentActivity) || !"com.besome.sketch.design.DesignActivity".equals(activity.getClass().getName())) return;
        activity.getWindow().getDecorView().post(() -> attach((FragmentActivity) activity));
    }

    private void attach(FragmentActivity activity) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        TabLayout tabs = activity.findViewById(R.id.tab_layout);
        ViewPager pager = activity.findViewById(R.id.viewpager);
        ViewGroup host = activity.findViewById(R.id.layout_coordinator);
        if (tabs == null || pager == null || host == null || attachedContainers.containsKey(activity)) return;

        FrameLayout agentContainer = new FrameLayout(activity);
        agentContainer.setId(View.generateViewId());
        agentContainer.setVisibility(View.GONE);
        host.addView(agentContainer, new ViewGroup.LayoutParams(-1, -1));
        attachedContainers.put(activity, agentContainer.getId());

        FragmentManager manager = activity.getSupportFragmentManager();
        Fragment existing = manager.findFragmentByTag(FRAGMENT_TAG);
        if (existing != null) manager.beginTransaction().remove(existing).commitNowAllowingStateLoss();
        manager.beginTransaction().replace(agentContainer.getId(), AgentFragment.newInstance(activity.getIntent().getStringExtra("sc_id")), FRAGMENT_TAG).commitNowAllowingStateLoss();

        // setupWithViewPager only knows the original 3 pages. Detach it, keep those tabs, and
        // route the four visible tabs manually so selecting Agent never asks the pager for page 3.
        tabs.setupWithViewPager(null);
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
                } else {
                    agentContainer.setVisibility(View.GONE);
                    pager.setVisibility(View.VISIBLE);
                    pager.setCurrentItem(Math.min(position, Math.max(0, pager.getAdapter() == null ? 0 : pager.getAdapter().getCount() - 1)), false);
                }
            }

            @Override public void onTabUnselected(TabLayout.Tab tab) {
                if (tab == agentTab) agentContainer.setVisibility(View.GONE);
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
                pager.setVisibility(View.VISIBLE);
            }
        });
        TabLayout.Tab selected = tabs.getTabAt(Math.min(pager.getCurrentItem(), tabs.getTabCount() - 2));
        if (selected != null) selected.select();
    }

    @Override public void onActivityStarted(@NonNull Activity activity) { }
    @Override public void onActivityResumed(@NonNull Activity activity) { }
    @Override public void onActivityPaused(@NonNull Activity activity) { }
    @Override public void onActivityStopped(@NonNull Activity activity) { }
    @Override public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) { }
    @Override public void onActivityDestroyed(@NonNull Activity activity) { attachedContainers.remove(activity); }
}
