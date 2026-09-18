package pro.sketchware.editor;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import com.besome.sketch.beans.BlockBean;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;

import a.a.a.Fx;
import a.a.a.jC;
import a.a.a.yq;
import pro.sketchware.R;

/** Adds the Java bridge and live status to every Logic Editor event activity. */
final class LogicEditorEnhancer implements Application.ActivityLifecycleCallbacks {
    private static final long POLL_MS = 650L;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<com.besome.sketch.editor.LogicEditorActivity, State> states = new WeakHashMap<>();

    @Override public void onActivityCreated(@NonNull Activity activity, Bundle savedInstanceState) {
        if (activity instanceof com.besome.sketch.editor.LogicEditorActivity editor) attach(editor);
    }

    private void attach(com.besome.sketch.editor.LogicEditorActivity activity) {
        if (states.containsKey(activity)) return;
        View toolbarView = activity.findViewById(R.id.toolbar);
        if (!(toolbarView instanceof androidx.appcompat.widget.Toolbar toolbar)) return;

        State state = new State(activity, toolbar, toolbar.getSubtitle() == null ? "" : toolbar.getSubtitle().toString());
        states.put(activity, state);
        state.poll = new Runnable() {
            @Override public void run() {
                if (!states.containsKey(activity) || activity.isFinishing()) return;
                ensureJavaBridgeMenu(state);
                updateStatus(state);
                handler.postDelayed(this, POLL_MS);
            }
        };
        handler.postDelayed(state.poll, 250L);
    }

    private void ensureJavaBridgeMenu(State state) {
        Menu menu = state.toolbar.getMenu();
        if (menu.findItem(state.menuId) != null) return;
        menu.add(Menu.NONE, state.menuId, 90, "Java to Blocks")
                .setIcon(R.drawable.ic_mtrl_java)
                .setContentDescription("Convert Java code to blocks")
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
                .setOnMenuItemClickListener(item -> {
                    showJavaBridge(state);
                    return true;
                });
        state.toolbar.invalidate();
    }

    private void updateStatus(State state) {
        try {
            String source = buildSource(state.activity);
            JavaToBlockBridge.Analysis analysis = JavaToBlockBridge.analyze(source);
            String suffix;
            int color;
            if (!analysis.valid) {
                suffix = "  •  ✕ Java syntax error at line " + analysis.line;
                color = 0xFFFF6B6B;
            } else if (!analysis.unsupported.isEmpty()) {
                suffix = "  •  ⚠ Syntax valid, " + analysis.unsupported.size() + " unsupported";
                color = 0xFFFFC857;
            } else {
                suffix = "  •  ✓ Blocks valid";
                color = 0xFF8BE28B;
            }
            state.toolbar.setSubtitle(state.baseSubtitle + suffix);
            state.toolbar.setSubtitleTextColor(color);
        } catch (Throwable ignored) {
            state.toolbar.setSubtitle(state.baseSubtitle + "  •  … Checking blocks");
        }
    }

    private void showJavaBridge(State state) {
        com.besome.sketch.editor.LogicEditorActivity activity = state.activity;
        EditText editor = new EditText(activity);
        editor.setMinLines(12);
        editor.setGravity(Gravity.TOP | Gravity.START);
        editor.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        editor.setText(buildSource(activity));
        editor.setSelection(editor.length());

        TextView status = new TextView(activity);
        status.setTextSize(12);
        status.setPadding(dp(activity, 4), dp(activity, 4), dp(activity, 4), dp(activity, 8));
        status.setTextColor(0xFF8BE28B);

        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(activity, 20), dp(activity, 4), dp(activity, 20), 0);
        content.addView(status, new LinearLayout.LayoutParams(-1, -2));
        content.addView(editor, new LinearLayout.LayoutParams(-1, 0, 1));

        AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                .setTitle("Java to Blocks")
                .setMessage("Supported: View setters, text, visibility, state, alpha, transforms, and Toast. Unsupported statements stay untouched.")
                .setView(content)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Convert", null)
                .create();

        Runnable refresh = () -> {
            JavaToBlockBridge.Analysis result = JavaToBlockBridge.analyze(editor.getText().toString());
            if (!result.valid) {
                status.setText("✕ Syntax error at line " + result.line + ": " + result.message);
                status.setTextColor(0xFFFF6B6B);
            } else if (!result.unsupported.isEmpty()) {
                status.setText("⚠ Syntax valid, " + result.unsupported.size() + " statement(s) will be skipped");
                status.setTextColor(0xFFFFC857);
            } else {
                status.setText("✓ Syntax valid, ready to create blocks");
                status.setTextColor(0xFF8BE28B);
            }
        };
        editor.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { refresh.run(); }
            @Override public void afterTextChanged(Editable s) { }
        });
        dialog.setOnShowListener(ignored -> {
            refresh.run();
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                JavaToBlockBridge.ImportResult result = JavaToBlockBridge.toBlocks(editor.getText().toString());
                if (!result.analysis.valid) {
                    refresh.run();
                    Toast.makeText(activity, "Fix the Java syntax first", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (result.blocks.isEmpty()) {
                    Toast.makeText(activity, "No supported statements found", Toast.LENGTH_SHORT).show();
                    return;
                }
                jC.a(activity.scId).a(activity.M.getJavaName(), activity.id + "_" + activity.eventName, result.blocks);
                dialog.dismiss();
                activity.recreate();
            });
        });
        dialog.show();
    }

    private static String buildSource(com.besome.sketch.editor.LogicEditorActivity activity) {
        try {
            yq project = new yq(activity, activity.scId);
            project.a(jC.c(activity.scId), jC.b(activity.scId), jC.a(activity.scId));
            return new Fx(activity.M.getActivityName(), project.N, activity.o.getBlocks(), false).a();
        } catch (Throwable error) {
            return "";
        }
    }

    private static int dp(Activity activity, int value) {
        return (int) (value * activity.getResources().getDisplayMetrics().density + .5f);
    }

    @Override public void onActivityDestroyed(@NonNull Activity activity) {
        State state = states.remove(activity);
        if (state != null && state.poll != null) handler.removeCallbacks(state.poll);
    }
    @Override public void onActivityStarted(@NonNull Activity activity) { }
    @Override public void onActivityResumed(@NonNull Activity activity) { }
    @Override public void onActivityPaused(@NonNull Activity activity) { }
    @Override public void onActivityStopped(@NonNull Activity activity) { }
    @Override public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) { }

    private static final class State {
        final com.besome.sketch.editor.LogicEditorActivity activity;
        final androidx.appcompat.widget.Toolbar toolbar;
        final String baseSubtitle;
        final int menuId = View.generateViewId();
        Runnable poll;
        State(com.besome.sketch.editor.LogicEditorActivity activity, androidx.appcompat.widget.Toolbar toolbar, String baseSubtitle) {
            this.activity = activity;
            this.toolbar = toolbar;
            this.baseSubtitle = baseSubtitle;
        }
    }
}
