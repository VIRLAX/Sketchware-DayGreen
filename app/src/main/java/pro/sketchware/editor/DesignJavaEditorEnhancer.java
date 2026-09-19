package pro.sketchware.editor;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;

import com.besome.sketch.beans.ProjectFileBean;
import com.besome.sketch.design.DesignActivity;

import java.io.File;
import java.util.Map;
import java.util.WeakHashMap;

import a.a.a.jC;
import a.a.a.yq;
import mod.hey.studios.code.SrcCodeEditor;
import pro.sketchware.R;
import pro.sketchware.utility.FilePathUtil;
import pro.sketchware.utility.FileUtil;

/** Adds the reference generated-class editor to the Design screen overflow menu. */
final class DesignJavaEditorEnhancer implements Application.ActivityLifecycleCallbacks {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<DesignActivity, Integer> attached = new WeakHashMap<>();

    @Override public void onActivityResumed(@NonNull Activity activity) {
        if (activity instanceof DesignActivity design) handler.post(() -> attach(design));
    }

    private void attach(DesignActivity activity) {
        if (attached.containsKey(activity)) return;
        View toolbarView = activity.findViewById(R.id.toolbar);
        if (!(toolbarView instanceof Toolbar toolbar)) return;
        int menuId = View.generateViewId();
        MenuItem item = toolbar.getMenu().add(Menu.NONE, menuId, 93, "Edit generated Java");
        item.setIcon(R.drawable.ic_mtrl_code);
        item.setContentDescription("Edit generated Java class for this layout");
        item.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        item.setOnMenuItemClickListener(clicked -> {
            openGeneratedJava(activity);
            return true;
        });
        attached.put(activity, menuId);
        toolbar.invalidate();
    }

    private static void openGeneratedJava(DesignActivity activity) {
        try {
            TextView fileNameView = activity.findViewById(R.id.file_name);
            String xmlName = fileNameView == null ? "" : String.valueOf(fileNameView.getText());
            ProjectFileBean file = jC.b(DesignActivity.sc_id).b(xmlName);
            if (file == null || file.getJavaName() == null || file.getJavaName().isEmpty()) {
                android.widget.Toast.makeText(activity, "Select an Activity layout first", android.widget.Toast.LENGTH_SHORT).show();
                return;
            }

            jC.a(DesignActivity.sc_id).k();
            yq project = new yq(activity, DesignActivity.sc_id);
            String generated = project.getFileSrc(file.getJavaName(), jC.b(DesignActivity.sc_id),
                    jC.a(DesignActivity.sc_id), jC.c(DesignActivity.sc_id));
            String overridePath = new FilePathUtil().getPathJava(DesignActivity.sc_id)
                    + File.separator + project.packageName.replace(".", File.separator)
                    + File.separator + file.getJavaName();
            if (!FileUtil.isExistFile(overridePath)) {
                File parent = new File(overridePath).getParentFile();
                if (parent != null && !parent.exists()) FileUtil.makeDir(parent.getAbsolutePath());
                FileUtil.writeFile(overridePath, generated);
            }

            Intent intent = new Intent(activity, SrcCodeEditor.class);
            intent.putExtra("sc_id", DesignActivity.sc_id);
            intent.putExtra("title", file.getJavaName());
            intent.putExtra("content", overridePath);
            intent.putExtra("java", true);
            activity.startActivity(intent);
        } catch (Throwable error) {
            android.widget.Toast.makeText(activity, "Failed to open generated Java: " + error.getMessage(), android.widget.Toast.LENGTH_LONG).show();
        }
    }

    @Override public void onActivityDestroyed(@NonNull Activity activity) {
        if (activity instanceof DesignActivity) attached.remove(activity);
    }
    @Override public void onActivityCreated(@NonNull Activity activity, android.os.Bundle state) { }
    @Override public void onActivityStarted(@NonNull Activity activity) { }
    @Override public void onActivityPaused(@NonNull Activity activity) { }
    @Override public void onActivityStopped(@NonNull Activity activity) { }
    @Override public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull android.os.Bundle state) { }
}
