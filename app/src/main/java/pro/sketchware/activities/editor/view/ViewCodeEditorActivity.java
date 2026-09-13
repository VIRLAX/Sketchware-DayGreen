package pro.sketchware.activities.editor.view;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;

import com.besome.sketch.beans.HistoryViewBean;
import com.besome.sketch.beans.ProjectFileBean;
import com.besome.sketch.beans.ProjectLibraryBean;
import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import a.a.a.cC;
import a.a.a.jC;
import io.github.rosemoe.sora.event.ContentChangeEvent;
import mod.hey.studios.util.Helper;
import pro.sketchware.R;
import pro.sketchware.editor.SketchwareCodeEditor;
import pro.sketchware.activities.appcompat.ManageAppCompatActivity;
import pro.sketchware.activities.preview.LayoutPreviewActivity;
import pro.sketchware.databinding.ViewCodeEditorBinding;
import pro.sketchware.managers.inject.InjectRootLayoutManager;
import pro.sketchware.tools.ViewBeanParser;
import pro.sketchware.utility.EditorUtils;
import pro.sketchware.utility.SketchwareUtil;
import pro.sketchware.utility.XmlSyncHelper;

public class ViewCodeEditorActivity extends BaseAppCompatActivity {
    private ViewCodeEditorBinding binding;
    private SketchwareCodeEditor editor;
    private SharedPreferences prefs;
    private String sc_id;
    private String content;
    private boolean isEdited = false;
    private ProjectFileBean projectFile;
    private ProjectLibraryBean projectLibrary;
    private InjectRootLayoutManager rootLayoutManager;
    private String livePreviewKey;

    private final OnBackPressedCallback onBackPressedCallback = new OnBackPressedCallback(true) {
        @Override public void handleOnBackPressed() {
            if (isContentModified()) {
                new MaterialAlertDialogBuilder(ViewCodeEditorActivity.this)
                        .setIcon(R.drawable.ic_warning_96dp)
                        .setTitle(Helper.getResString(R.string.common_word_warning))
                        .setMessage(Helper.getResString(R.string.src_code_editor_unsaved_changes_dialog_warning_message))
                        .setPositiveButton(Helper.getResString(R.string.common_word_exit), (v, which) -> {
                            exitWithEditedContent();
                            finish();
                        })
                        .setNegativeButton(Helper.getResString(R.string.common_word_cancel), null)
                        .show();
            } else if (isEdited) {
                exitWithEditedContent();
                finish();
            } else {
                setEnabled(false);
                getOnBackPressedDispatcher().onBackPressed();
            }
        }
    };

    @Override public void onCreate(Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);
        binding = ViewCodeEditorBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        prefs = getSharedPreferences("dce", Activity.MODE_PRIVATE);
        sc_id = savedInstanceState == null ? getIntent().getStringExtra("sc_id") : savedInstanceState.getString("sc_id");
        rootLayoutManager = new InjectRootLayoutManager(sc_id);
        String title = getIntent().getStringExtra("title");
        projectFile = jC.b(sc_id).b(title);
        projectLibrary = jC.c(sc_id).c();
        livePreviewKey = "layout:" + sc_id + ":" + title;
        getOnBackPressedDispatcher().addCallback(this, onBackPressedCallback);
        setSupportActionBar(binding.toolbar);
        getSupportActionBar().setTitle("XML Editor");
        getSupportActionBar().setSubtitle(title);
        binding.toolbar.setNavigationOnClickListener(v -> onBackPressedCallback.handleOnBackPressed());
        content = getIntent().getStringExtra("content");
        editor = binding.editor;
        editor.setScId(sc_id);
        editor.setTypefaceText(EditorUtils.getTypeface(this));
        editor.setTextSize(14);
        editor.setText(content);
        EditorUtils.loadXmlConfig(editor, "layout/" + title);
        publishLiveSource(content);
        editor.subscribeAlways(ContentChangeEvent.class, event -> publishLiveSource(editor.getText().toString()));
        if (projectFile.fileType == ProjectFileBean.PROJECT_FILE_TYPE_ACTIVITY && projectLibrary.isEnabled()) {
            setNote("Use AppCompat Manager to modify attributes for CoordinatorLayout, Toolbar, and other appcompat layout/widget.");
        }
        binding.close.setOnClickListener(v -> {
            prefs.edit().putInt("note_" + sc_id, 1).apply();
            setNote(null);
        });
        binding.noteCard.setOnClickListener(v -> toAppCompat());
    }

    private void publishLiveSource(String xml) {
        getSharedPreferences("xml_preview_live", MODE_PRIVATE).edit().putString(livePreviewKey, xml == null ? "" : xml).apply();
    }

    @Override public void onSaveInstanceState(Bundle outState) {
        outState.putString("sc_id", sc_id);
        super.onSaveInstanceState(outState);
    }

    @Override public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(Menu.NONE, 0, Menu.NONE, "Undo").setIcon(R.drawable.ic_mtrl_undo).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        menu.add(Menu.NONE, 1, Menu.NONE, "Redo").setIcon(R.drawable.ic_mtrl_redo).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        menu.add(Menu.NONE, 2, Menu.NONE, "Save").setIcon(R.drawable.ic_mtrl_save).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        if (projectFile.fileType == ProjectFileBean.PROJECT_FILE_TYPE_ACTIVITY && projectLibrary.isEnabled()) menu.add(Menu.NONE, 3, Menu.NONE, "Edit AppCompat");
        menu.add(Menu.NONE, 5, Menu.NONE, "Layout Preview");
        return true;
    }

    @Override public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case 0: editor.undo(); return true;
            case 1: editor.redo(); return true;
            case 2: save(); return true;
            case 3: toAppCompat(); return true;
            case 5: toLayoutPreview(); return true;
            default: return super.onOptionsItemSelected(item);
        }
    }

    private void toAppCompat() {
        Intent intent = new Intent(getApplicationContext(), ManageAppCompatActivity.class);
        intent.putExtra("sc_id", sc_id);
        intent.putExtra("file_name", getIntent().getStringExtra("title"));
        startActivity(intent);
    }

    private void toLayoutPreview() {
        Intent intent = new Intent(getApplicationContext(), LayoutPreviewActivity.class);
        intent.putExtras(getIntent());
        intent.putExtra("xml", editor.getText().toString());
        intent.putExtra("live_key", livePreviewKey);
        startActivity(intent);
    }

    private void setNote(String note) {
        if (prefs.getInt("note_" + sc_id, 0) < 1 && note != null && !note.isEmpty()) {
            binding.noteCard.setVisibility(View.VISIBLE);
            binding.note.setText(note);
        } else binding.noteCard.setVisibility(View.GONE);
    }

    private void save() {
        try {
            if (!isContentModified()) {
                SketchwareUtil.toast("No changes to save");
                return;
            }
            String title = getIntent().getStringExtra("title");
            if (title != null && title.endsWith(".xml")) XmlSyncHelper.syncXml(this, sc_id, title, editor.getText().toString());
            content = editor.getText().toString();
            publishLiveSource(content);
            isEdited = true;
            SketchwareUtil.toast("Saved");
        } catch (Exception e) {
            SketchwareUtil.toastError(e.toString());
        }
    }

    private boolean isContentModified() { return content == null || !content.equals(editor.getText().toString()); }

    private void exitWithEditedContent() {
        String filename = getIntent().getStringExtra("title");
        try {
            ViewBeanParser parser = new ViewBeanParser(editor.getText().toString());
            parser.setSkipRoot(true);
            java.util.ArrayList<com.besome.sketch.beans.ViewBean> parsedLayout = parser.parse();
            if (parser.getRootAttributes() != null) rootLayoutManager.set(filename, InjectRootLayoutManager.toRoot(parser.getRootAttributes()));
            HistoryViewBean bean = new HistoryViewBean();
            bean.actionOverride(parsedLayout, jC.a(sc_id).d(filename));
            cC cc = cC.c(sc_id);
            if (!cc.c.containsKey(filename)) cc.e(filename);
            cc.a(filename);
            cc.a(filename, bean);
            jC.a(sc_id).c.put(filename, parsedLayout);
            setResult(RESULT_OK);
        } catch (Exception e) {
            SketchwareUtil.toastError(e.toString());
        }
    }
}
