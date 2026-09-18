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
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

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
        if (activity instanceof com.besome.sketch.editor.LogicEditorActivity editor) {
            handler.post(() -> attach(editor));
        }
    }

    @Override public void onActivityResumed(@NonNull Activity activity) {
        if (activity instanceof com.besome.sketch.editor.LogicEditorActivity editor) attach(editor);
    }

    private void attach(com.besome.sketch.editor.LogicEditorActivity activity) {
        if (states.containsKey(activity)) return;
        View toolbarView = activity.findViewById(R.id.toolbar);
        if (!(toolbarView instanceof androidx.appcompat.widget.Toolbar toolbar)) return;

        TextView statusView = new TextView(activity);
        statusView.setTextSize(11);
        statusView.setMaxLines(3);
        statusView.setGravity(Gravity.CENTER_VERTICAL);
        statusView.setPadding(dp(activity, 12), dp(activity, 4), dp(activity, 12), dp(activity, 4));
        statusView.setTextColor(0xFF8BE28B);
        statusView.setText("Blocks syntax: checking…");
        ViewGroup parent = (ViewGroup) toolbar.getParent();
        int toolbarIndex = parent.indexOfChild(toolbar);
        parent.addView(statusView, toolbarIndex + 1, new ViewGroup.LayoutParams(-1, -2));

        State state = new State(activity, toolbar, statusView, toolbar.getSubtitle() == null ? "" : toolbar.getSubtitle().toString());
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
        MenuItem item = menu.add(Menu.NONE, state.menuId, 90, "Java to Blocks");
        item.setIcon(R.drawable.ic_mtrl_java);
        item.setContentDescription("Convert Java code to blocks");
        item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        item.setOnMenuItemClickListener(clickedItem -> {
            showJavaBridge(state);
            return true;
        });
        state.toolbar.invalidate();
    }

    private void updateStatus(State state) {
        try {
            String source = buildSource(state.activity);
            JavaToBlockBridge.Analysis analysis = JavaToBlockBridge.analyze(source);
            String subtitle;
            String banner;
            int color;
            if (!analysis.valid) {
                subtitle = "  •  ✕ Java syntax error at line " + analysis.line;
                banner = "✕ Blocks syntax error at line " + analysis.line + ", column " + analysis.column + ": " + analysis.message;
                color = 0xFFFF6B6B;
            } else if (!analysis.unsupported.isEmpty()) {
                subtitle = "  •  ⚠ Syntax valid, " + analysis.unsupported.size() + " raw Java block(s)";
                banner = "⚠ Syntax valid. " + analysis.unsupported.size() + " statement(s) will be preserved as raw Java blocks";
                color = 0xFFFFC857;
            } else {
                subtitle = "  •  ✓ Blocks valid";
                banner = "✓ Blocks syntax valid";
                color = 0xFF8BE28B;
            }
            state.toolbar.setSubtitle(state.baseSubtitle + subtitle);
            state.toolbar.setSubtitleTextColor(color);
            state.statusView.setText(banner);
            state.statusView.setTextColor(color);
        } catch (Throwable error) {
            state.toolbar.setSubtitle(state.baseSubtitle + "  •  … Checking blocks");
            state.statusView.setText("… Syntax check unavailable: " + error.getClass().getSimpleName());
            state.statusView.setTextColor(0xFFFFC857);
        }
    }

    private void showJavaBridge(State state) {
        com.besome.sketch.editor.LogicEditorActivity activity = state.activity;
        EditText editor = new EditText(activity);
        editor.setMinLines(24);
        editor.setGravity(Gravity.TOP | Gravity.START);
        editor.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        editor.setHorizontallyScrolling(false);
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
                .setTitle("Direct Java Editor")
                .setMessage("Edit the complete event body. Native statements become normal blocks; unsupported statements are preserved as raw Java blocks, so conversion never silently drops code.")
                .setView(content)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Convert and install", null)
                .create();

        Runnable refresh = () -> {
            JavaToBlockBridge.Analysis result = JavaToBlockBridge.analyze(editor.getText().toString());
            if (!result.valid) {
                status.setText("✕ Syntax error at line " + result.line + ", column " + result.column + ": " + result.message);
                status.setTextColor(0xFFFF6B6B);
            } else if (!result.unsupported.isEmpty()) {
                status.setText("⚠ Syntax valid. " + result.unsupported.size() + " statement(s) will be preserved as raw Java blocks");
                status.setTextColor(0xFFFFC857);
            } else {
                status.setText("✓ Syntax valid, ready to install blocks");
                status.setTextColor(0xFF8BE28B);
            }
        };
        editor.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { refresh.run(); }
            @Override public void afterTextChanged(Editable s) { }
        });
        dialog.setOnShowListener(ignored -> {
            Window window = dialog.getWindow();
            if (window != null) {
                window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
                window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            }
            refresh.run();
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                JavaToBlockBridge.ImportResult result = JavaToBlockBridge.toBlocks(editor.getText().toString());
                if (!result.analysis.valid) {
                    refresh.run();
                    Toast.makeText(activity, "Fix the Java syntax first", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (result.blocks.isEmpty()) {
                    Toast.makeText(activity, "No statements found", Toast.LENGTH_SHORT).show();
                    return;
                }
                jC.a(activity.scId).a(activity.M.getJavaName(), activity.id + "_" + activity.eventName, result.blocks);
                jC.a(activity.scId).h();
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
    @Override public void onActivityPaused(@NonNull Activity activity) { }
    @Override public void onActivityStopped(@NonNull Activity activity) { }
    @Override public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) { }

    private static final class State {
        final com.besome.sketch.editor.LogicEditorActivity activity;
        final androidx.appcompat.widget.Toolbar toolbar;
        final TextView statusView;
        final String baseSubtitle;
        final int menuId = View.generateViewId();
        Runnable poll;
        State(com.besome.sketch.editor.LogicEditorActivity activity, androidx.appcompat.widget.Toolbar toolbar, TextView statusView, String baseSubtitle) {
            this.activity = activity;
            this.toolbar = toolbar;
            this.statusView = statusView;
            this.baseSubtitle = baseSubtitle;
        }
    }
}
