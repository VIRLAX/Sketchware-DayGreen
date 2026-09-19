package pro.sketchware.editor;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.tabs.TabLayout;

import java.util.WeakHashMap;

import pro.sketchware.R;

/** Adds the Agent page without forking the legacy DesignActivity pager adapter. */
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
        FrameLayout host = activity.findViewById(R.id.layout_coordinator);
        View pager = activity.findViewById(R.id.viewpager);
        if (tabs == null || host == null || pager == null || attachedContainers.containsKey(activity)) return;

        FrameLayout agentContainer = new FrameLayout(activity);
        agentContainer.setId(View.generateViewId());
        agentContainer.setVisibility(View.GONE);
        host.addView(agentContainer, new android.view.ViewGroup.LayoutParams(-1, -1));
        attachedContainers.put(activity, agentContainer.getId());

        FragmentManager manager = activity.getSupportFragmentManager();
        Fragment existing = manager.findFragmentByTag(FRAGMENT_TAG);
        if (existing != null) manager.beginTransaction().remove(existing).commitNowAllowingStateLoss();
        manager.beginTransaction().replace(agentContainer.getId(), AgentFragment.newInstance(activity.getIntent().getStringExtra("sc_id")), FRAGMENT_TAG).commitNowAllowingStateLoss();

        TabLayout.Tab agentTab = tabs.newTab().setText("Agent");
        tabs.addTab(agentTab, false);
        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) {
                if (tab != agentTab) return;
                pager.setVisibility(View.GONE);
                agentContainer.setVisibility(View.VISIBLE);
            }

            @Override public void onTabUnselected(TabLayout.Tab tab) {
                if (tab == agentTab) agentContainer.setVisibility(View.GONE);
            }

            @Override public void onTabReselected(TabLayout.Tab tab) { }
        });
    }

    @Override public void onActivityStarted(@NonNull Activity activity) { }
    @Override public void onActivityResumed(@NonNull Activity activity) { }
    @Override public void onActivityPaused(@NonNull Activity activity) { }
    @Override public void onActivityStopped(@NonNull Activity activity) { }
    @Override public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) { }
    @Override public void onActivityDestroyed(@NonNull Activity activity) { attachedContainers.remove(activity); }
}
