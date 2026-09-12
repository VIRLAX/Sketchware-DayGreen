package mod.hey.studios.code;

import static pro.sketchware.utility.GsonUtils.getGson;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Pair;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.rosemoe.sora.event.ContentChangeEvent;
import io.github.rosemoe.sora.langs.java.JavaLanguage;
import io.github.rosemoe.sora.widget.CodeEditor;
import io.github.rosemoe.sora.widget.component.EditorAutoCompletion;
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme;
import io.github.rosemoe.sora.widget.schemes.SchemeDarcula;
import io.github.rosemoe.sora.widget.schemes.SchemeEclipse;
import io.github.rosemoe.sora.widget.schemes.SchemeGitHub;
import io.github.rosemoe.sora.widget.schemes.SchemeNotepadXX;
import io.github.rosemoe.sora.widget.schemes.SchemeVS2019;

import mod.hey.studios.util.Helper;
import mod.jbk.code.CodeEditorColorSchemes;
import mod.jbk.code.CodeEditorLanguages;
import pro.sketchware.R;
import pro.sketchware.activities.preview.LayoutPreviewActivity;
import pro.sketchware.databinding.CodeEditorHsBinding;
import pro.sketchware.editor.FloatingPreviewService;
import pro.sketchware.editor.diagnostics.DiagnosticsAdapter;
import pro.sketchware.utility.EditorUtils;
import pro.sketchware.utility.FileUtil;
import pro.sketchware.utility.RealtimeDiagnostics;
import pro.sketchware.utility.SketchwareUtil;
import pro.sketchware.utility.ThemeUtils;
import pro.sketchware.utility.UI;
import pro.sketchware.utility.XmlSyncHelper;

public class SrcCodeEditor extends BaseAppCompatActivity {
    public static final String FLAG_FROM_ANDROID_MANIFEST = "from_android_manifest";
    public static final List<Pair<String, Class<? extends EditorColorScheme>>> KNOWN_COLOR_SCHEMES = List.of(
            new Pair<>("Default", EditorColorScheme.class), new Pair<>("GitHub", SchemeGitHub.class),
            new Pair<>("Eclipse", SchemeEclipse.class), new Pair<>("Darcula", SchemeDarcula.class),
            new Pair<>("VS2019", SchemeVS2019.class), new Pair<>("NotepadXX", SchemeNotepadXX.class));
    public static SharedPreferences pref;
    public static int languageId;

    private static final int ACTION_UNDO = 1001;
    private static final int ACTION_REDO = 1002;
    private static final int ACTION_SAVE = 1003;
    private static final int ACTION_DIAGNOSTICS = 1004;
    private static final int ACTION_WORD_WRAP = 1005;
    private static final int ACTION_PRETTY_PRINT = 1006;
    private static final int ACTION_SELECT_LANGUAGE = 1007;
    private static final int ACTION_SELECT_THEME = 1008;
    private static final int ACTION_AUTOCOMPLETE = 1009;
    private static final int ACTION_SYMBOL_PAIR = 1010;
    private static final int ACTION_LAYOUT_PREVIEW = 1011;

    private final Handler diagnosticsHandler = new Handler(Looper.getMainLooper());
    private final Map<String, String> workingContents = new HashMap<>();
    private CodeEditorHsBinding binding;
    private boolean fromAndroidManifest;
    private String scId, activityName, beforeContent = "", currentPath, currentTitle;
    private String lastAnalyzedSource;
    private RealtimeDiagnostics.Kind lastAnalyzedKind;
    private MenuItem diagnosticsMenuItem;
    private DiagnosticsAdapter diagnosticsAdapter;
    private final Runnable diagnosticsRunnable = new Runnable() {
        @Override public void run() {
            refreshDiagnostics();
            diagnosticsHandler.postDelayed(this, 280);
        }
    };

    public static void loadCESettings(Context c, CodeEditor e, String p) { loadCESettings(c, e, p, false); }

    public static void loadCESettings(Context c, CodeEditor e, String p, boolean theme) {
        pref = c.getSharedPreferences("hsce", Activity.MODE_PRIVATE);
        e.setTextSize(pref.getInt(p + "_ts", 12));
        e.setWordwrap(pref.getBoolean(p + "_ww", false));
        e.getProps().symbolPairAutoCompletion = pref.getBoolean(p + "_acsp", true);
        e.getComponent(EditorAutoCompletion.class).setEnabled(pref.getBoolean(p + "_ac", true));
        if (theme) selectTheme(e, 0);
    }

    public static void selectTheme(CodeEditor e, int ignored) {
        String theme = ThemeUtils.isDarkThemeEnabled(e.getContext())
                ? CodeEditorColorSchemes.THEME_GITHUB_DARK : CodeEditorColorSchemes.THEME_GITHUB_LIGHT;
        e.setColorScheme(CodeEditorColorSchemes.loadTextMateColorScheme(theme));
        EditorUtils.getMaterialStyledScheme(e, true);
        e.rerunAnalysis();
    }

    public static void selectLanguage(CodeEditor e, int which) {
        if (which == 1) {
            e.setEditorLanguage(CodeEditorLanguages.loadTextMateLanguage(CodeEditorLanguages.SCOPE_NAME_KOTLIN));
            languageId = 1;
        } else if (which == 2) {
            e.setEditorLanguage(CodeEditorLanguages.loadTextMateLanguage(CodeEditorLanguages.SCOPE_NAME_XML));
            languageId = 2;
        } else {
            e.setEditorLanguage(new JavaLanguage());
            languageId = 0;
        }
    }

    public static String prettifyXml(String x, int i, Intent e) { return x == null ? null : x.trim(); }
    public static void a(StringBuilder b, int n) { for (int i = 0; i < n; i++) b.append('\t'); }

    public static void showSwitchThemeDialog(Activity a, CodeEditor e, DialogInterface.OnClickListener l) {
        new MaterialAlertDialogBuilder(a).setTitle("Select Theme")
                .setSingleChoiceItems(new String[]{"Default (Dynamic)", "GitHub Light", "GitHub Dark", "Eclipse", "VS2019", "NotepadXX"}, 0, l)
                .setNegativeButton(R.string.common_word_cancel, null).show();
    }

    public static void showSwitchLanguageDialog(Activity a, CodeEditor e, DialogInterface.OnClickListener l) {
        new MaterialAlertDialogBuilder(a).setTitle("Select Language")
                .setSingleChoiceItems(new CharSequence[]{"Java", "Kotlin", "XML"}, languageId, l)
                .setNegativeButton(R.string.common_word_cancel, null).show();
    }

    @Override public void onCreate(@Nullable Bundle s) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(s);
        binding = CodeEditorHsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        pref = getSharedPreferences("hsce", Activity.MODE_PRIVATE);
        fromAndroidManifest = getIntent().getBooleanExtra(FLAG_FROM_ANDROID_MANIFEST, false);
        scId = getIntent().getStringExtra("sc_id");
        activityName = getIntent().getStringExtra("activity_name");
        currentPath = getIntent().getStringExtra("content");
        currentTitle = getIntent().getStringExtra("title");
        binding.editor.setScId(scId);
        binding.editor.setTypefaceText(EditorUtils.getTypeface(this));
        binding.editor.setTextSize(16);
        beforeContent = loadInitialContent();
        binding.editor.setText(beforeContent);
        loadLanguageForTitle(currentTitle);
        binding.toolbar.setTitle(currentTitle);
        binding.toolbar.setNavigationOnClickListener(v -> onBackPressed());
        binding.editor.subscribeAlways(ContentChangeEvent.class, event -> {
            lastAnalyzedSource = null;
            refreshDiagnostics();
            updateFloatingPreview();
        });

        diagnosticsAdapter = new DiagnosticsAdapter(issue -> {
            binding.diagnosticsPanel.setVisibility(View.GONE);
            binding.editor.setSelection(Math.max(0, issue.line() - 1), Math.max(0, issue.column() - 1));
            binding.editor.requestFocus();
        });
        binding.diagnosticsPanel.setLayoutManager(new LinearLayoutManager(this));
        binding.diagnosticsPanel.setAdapter(diagnosticsAdapter);
        buildFileTabs();
        loadToolbar();
        UI.addSystemWindowInsetToPadding(binding.appBarLayout, true, true, true, false);
        UI.addSystemWindowInsetToMargin(binding.editor, true, false, true, true);
    }

    private void updateFloatingPreview() {
        if (!isXmlFile() || !FloatingPreviewService.isRunning()) return;
        FloatingPreviewService.updatePreview(this, binding.editor.getText().toString(), currentTitle, scId);
    }

    private boolean isXmlFile() {
        return !fromAndroidManifest && currentTitle != null && currentTitle.endsWith(".xml")
                && currentPath != null && currentPath.contains("/resource/layout/");
    }

    private String loadInitialContent() {
        if (fromAndroidManifest) return readManifestContent();
        return currentPath == null ? getIntent().getStringExtra("code") : FileUtil.readFile(currentPath);
    }

    private String readManifestContent() {
        String p = FileUtil.getExternalStorageDir() + "/.sketchware/data/" + scId + "/Injection/androidmanifest/activities_components.json";
        if (!FileUtil.isExistFile(p)) return "";
        ArrayList<HashMap<String, Object>> list = getGson().fromJson(FileUtil.readFile(p), Helper.TYPE_MAP_LIST);
        for (HashMap<String, Object> item : list) if (String.valueOf(item.get("name")).equals(activityName)) return String.valueOf(item.get("value"));
        return "";
    }

    private void buildFileTabs() {
        binding.fileTabs.removeAllViews();
        if (fromAndroidManifest || currentPath == null || currentTitle == null
                || (!currentTitle.endsWith(".java") && !currentTitle.endsWith(".kt"))) {
            binding.tabScroll.setVisibility(View.GONE);
            return;
        }
        File parent = new File(currentPath).getParentFile();
        File[] files = parent == null ? null : parent.listFiles(f -> f.isFile()
                && (f.getName().endsWith(".java") || f.getName().endsWith(".kt")));
        if (files == null || files.length < 2) {
            binding.tabScroll.setVisibility(View.GONE);
            return;
        }
        Arrays.sort(files, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        binding.tabScroll.setVisibility(View.VISIBLE);
        for (File f : files) {
            TextView tab = new TextView(this);
            tab.setText(f.getName());
            tab.setTextSize(13);
            tab.setSingleLine(true);
            tab.setGravity(17);
            tab.setPadding(28, 0, 28, 0);
            tab.setMinWidth(120);
            tab.setOnClickListener(v -> switchTab(f));
            binding.fileTabs.addView(tab);
        }
        updateTabStyles();
    }

    private void updateTabStyles() {
        for (int i = 0; i < binding.fileTabs.getChildCount(); i++) {
            TextView tab = (TextView) binding.fileTabs.getChildAt(i);
            boolean selected = tab.getText().toString().equals(currentTitle);
            tab.setBackgroundColor(selected ? 0xFF263238 : 0xFF172024);
            tab.setTextColor(selected ? 0xFF42A5F5 : 0xFF90A4AE);
        }
    }

    private void switchTab(File f) {
        if (f.getAbsolutePath().equals(currentPath)) return;
        workingContents.put(currentPath, binding.editor.getText().toString());
        currentPath = f.getAbsolutePath();
        currentTitle = f.getName();
        beforeContent = FileUtil.readFile(currentPath);
        binding.editor.setText(workingContents.containsKey(currentPath)
                ? workingContents.get(currentPath) : beforeContent);
        binding.toolbar.setTitle(currentTitle);
        loadLanguageForTitle(currentTitle);
        updateTabStyles();
        lastAnalyzedSource = null;
        lastAnalyzedKind = null;
        refreshDiagnostics();
    }

    private void loadLanguageForTitle(String title) {
        if (title == null) return;
        if (fromAndroidManifest || title.endsWith(".xml")) {
            EditorUtils.loadXmlConfig(binding.editor, fromAndroidManifest ? "AndroidManifest.xml" : currentPath);
            languageId = 2;
        } else if (title.endsWith(".java")) {
            EditorUtils.loadJavaConfig(binding.editor);
            languageId = 0;
        } else if (title.endsWith(".kt")) {
            binding.editor.setEditorLanguage(CodeEditorLanguages.loadTextMateLanguage(CodeEditorLanguages.SCOPE_NAME_KOTLIN));
            selectTheme(binding.editor, 0);
            languageId = 1;
        } else selectLanguage(binding.editor, 0);
    }

    private RealtimeDiagnostics.Kind currentKind() {
        if (fromAndroidManifest || (currentTitle != null && currentTitle.endsWith(".xml"))) return RealtimeDiagnostics.Kind.XML;
        if (currentTitle != null && currentTitle.endsWith(".kt")) return RealtimeDiagnostics.Kind.KOTLIN;
        return RealtimeDiagnostics.Kind.JAVA;
    }

    private void refreshDiagnostics() {
        if (binding == null || diagnosticsAdapter == null) return;
        RealtimeDiagnostics.Kind kind = currentKind();
        String source = binding.editor.getText().toString();
        if (source.equals(lastAnalyzedSource) && kind == lastAnalyzedKind) return;
        lastAnalyzedSource = source;
        lastAnalyzedKind = kind;
        List<RealtimeDiagnostics.Issue> issues = RealtimeDiagnostics.check(kind, source);
        binding.editor.setDiagnostics(issues);
        diagnosticsAdapter.submitList(new ArrayList<>(issues));
        if (diagnosticsMenuItem != null) diagnosticsMenuItem.setTitle("Errors (" + issues.size() + ")");
    }

    private void loadToolbar() {
        Menu toolbarMenu = binding.toolbar.getMenu();
        toolbarMenu.clear();

        toolbarMenu.add(Menu.NONE, ACTION_UNDO, Menu.NONE, "Undo")
                .setIcon(R.drawable.ic_mtrl_undo).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        toolbarMenu.add(Menu.NONE, ACTION_REDO, Menu.NONE, "Redo")
                .setIcon(R.drawable.ic_mtrl_redo).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        toolbarMenu.add(Menu.NONE, ACTION_SAVE, Menu.NONE, "Save")
                .setIcon(R.drawable.ic_mtrl_save).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        diagnosticsMenuItem = toolbarMenu.add(Menu.NONE, ACTION_DIAGNOSTICS, Menu.NONE, "Errors (0)");
        diagnosticsMenuItem.setIcon(R.drawable.ic_mtrl_bug_report)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);

        if (isFileInLayoutFolder()) {
            toolbarMenu.add(Menu.NONE, ACTION_LAYOUT_PREVIEW, Menu.NONE, "Layout Preview")
                    .setIcon(R.drawable.ic_floating_preview)
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        }
        toolbarMenu.add(Menu.NONE, Menu.NONE, Menu.NONE, "Find & Replace");
        toolbarMenu.add(Menu.NONE, ACTION_WORD_WRAP, Menu.NONE, "Word wrap")
                .setCheckable(true).setChecked(pref.getBoolean("act_ww", false));
        toolbarMenu.add(Menu.NONE, ACTION_PRETTY_PRINT, Menu.NONE, "Pretty print");
        toolbarMenu.add(Menu.NONE, ACTION_SELECT_LANGUAGE, Menu.NONE, "Select language");
        toolbarMenu.add(Menu.NONE, ACTION_SELECT_THEME, Menu.NONE, "Select theme");
        toolbarMenu.add(Menu.NONE, ACTION_AUTOCOMPLETE, Menu.NONE, "Auto complete")
                .setCheckable(true).setChecked(pref.getBoolean("act_ac", true));
        toolbarMenu.add(Menu.NONE, ACTION_SYMBOL_PAIR, Menu.NONE, "Auto complete symbol pair")
                .setCheckable(true).setChecked(pref.getBoolean("act_acsp", true));

        binding.toolbar.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == ACTION_UNDO) binding.editor.undo();
            else if (id == ACTION_REDO) binding.editor.redo();
            else if (id == ACTION_SAVE) save();
            else if (id == ACTION_DIAGNOSTICS) {
                binding.diagnosticsPanel.setVisibility(binding.diagnosticsPanel.getVisibility() == View.VISIBLE
                        ? View.GONE : View.VISIBLE);
            } else if (id == ACTION_LAYOUT_PREVIEW) {
                toggleFloatingPreview();
            } else if (id == ACTION_WORD_WRAP) {
                item.setChecked(!item.isChecked());
                binding.editor.setWordwrap(item.isChecked());
                pref.edit().putBoolean("act_ww", item.isChecked()).apply();
            } else if (id == ACTION_AUTOCOMPLETE) {
                item.setChecked(!item.isChecked());
                binding.editor.getComponent(EditorAutoCompletion.class).setEnabled(item.isChecked());
                pref.edit().putBoolean("act_ac", item.isChecked()).apply();
            } else if (id == ACTION_SYMBOL_PAIR) {
                item.setChecked(!item.isChecked());
                binding.editor.getProps().symbolPairAutoCompletion = item.isChecked();
                pref.edit().putBoolean("act_acsp", item.isChecked()).apply();
            } else if (id == ACTION_SELECT_LANGUAGE) {
                showSwitchLanguageDialog(this, binding.editor, (dialog, which) -> {
                    selectLanguage(binding.editor, which);
                    dialog.dismiss();
                    lastAnalyzedSource = null;
                    lastAnalyzedKind = null;
                    refreshDiagnostics();
                });
            } else if (id == ACTION_SELECT_THEME) {
                showSwitchThemeDialog(this, binding.editor, (dialog, which) -> {
                    selectTheme(binding.editor, which);
                    dialog.dismiss();
                });
            }
            return true;
        });
        refreshDiagnostics();
    }

    private void toggleFloatingPreview() {
        if (!isXmlFile()) {
            SketchwareUtil.toast("Floating Preview hanya tersedia untuk XML layout");
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            SketchwareUtil.toast("Aktifkan izin tampil di atas aplikasi untuk memakai preview");
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())));
            return;
        }
        if (FloatingPreviewService.isRunning()) {
            FloatingPreviewService.stop(this);
        } else {
            FloatingPreviewService.showOrUpdate(this, binding.editor.getText().toString(), currentTitle, scId);
        }
    }

    private void save() {
        String text = binding.editor.getText().toString();
        if (fromAndroidManifest) {
            saveManifest(text);
            beforeContent = text;
            SketchwareUtil.toast("Saved");
            return;
        }
        if (currentPath == null) {
            SketchwareUtil.toastError("File path is missing");
            return;
        }
        FileUtil.writeFile(currentPath, text);
        workingContents.remove(currentPath);
        beforeContent = text;
        if (currentTitle != null && currentTitle.endsWith(".xml")) XmlSyncHelper.syncXml(this, scId, currentTitle, text);
        SketchwareUtil.toast("Saved");
        refreshDiagnostics();
    }

    private void saveManifest(String text) {
        String p = FileUtil.getExternalStorageDir() + "/.sketchware/data/" + scId + "/Injection/androidmanifest/activities_components.json";
        ArrayList<HashMap<String, Object>> list = FileUtil.isExistFile(p)
                ? getGson().fromJson(FileUtil.readFile(p), Helper.TYPE_MAP_LIST) : new ArrayList<>();
        boolean found = false;
        for (HashMap<String, Object> item : list) if (String.valueOf(item.get("name")).equals(activityName)) {
            item.put("value", text);
            found = true;
        }
        if (!found) {
            HashMap<String, Object> item = new HashMap<>();
            item.put("name", activityName);
            item.put("value", text);
            list.add(item);
        }
        FileUtil.writeFile(p, getGson().toJson(list));
    }

    private boolean isFileInLayoutFolder() {
        return currentPath != null && currentPath.contains("/resource/layout/") && currentPath.endsWith(".xml");
    }

    private boolean isXmlFile() {
        return !fromAndroidManifest && currentTitle != null && currentTitle.endsWith(".xml")
                && currentPath != null && currentPath.contains("/resource/layout/");
    }

    private void toLayoutPreview() {
        Intent intent = new Intent(this, LayoutPreviewActivity.class);
        intent.putExtras(getIntent());
        intent.putExtra("xml", binding.editor.getText().toString());
        startActivity(intent);
    }

    @Override public void onResume() {
        super.onResume();
        diagnosticsHandler.removeCallbacks(diagnosticsRunnable);
        diagnosticsHandler.post(diagnosticsRunnable);
    }

    @Override public void onPause() {
        diagnosticsHandler.removeCallbacks(diagnosticsRunnable);
        super.onPause();
    }

    @Override protected void onStop() {
        super.onStop();
        if (pref != null) pref.edit().putInt("act_ts", (int) (binding.editor.getTextSizePx() / getResources().getDisplayMetrics().scaledDensity)).apply();
    }
}
