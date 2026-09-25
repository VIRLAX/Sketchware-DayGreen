package mod.hey.studios.project.proguard;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.CompoundButton;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;

import extensions.anbui.daydream.library.DRFeatureManager;
import mod.agus.jcoderz.editor.manage.library.locallibrary.ManageLocalLibrary;
import mod.hey.studios.code.SrcCodeEditor;
import mod.hey.studios.project.stringfog.StringfogHandler;
import pro.sketchware.R;
import pro.sketchware.ai.autonomous.DayvenAutonomousSuite;
import pro.sketchware.databinding.ManageProguardBinding;
import pro.sketchware.utility.FileUtil;
import pro.sketchware.utility.SketchwareUtil;

public class ManageProguardActivity extends BaseAppCompatActivity
        implements View.OnClickListener, CompoundButton.OnCheckedChangeListener {

    private ProguardHandler pg;
    private StringfogHandler sf;
    private String scId;
    private ManageProguardBinding binding;

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.ig_toolbar_back) {
            finish();
        } else if (id == R.id.ln_pg_rules) {
            Intent intent = new Intent(this, SrcCodeEditor.class);
            intent.putExtra("title", "proguard-rules.pro");
            intent.putExtra("content", pg.getCustomProguardRules());
            intent.putExtra("sc_id", scId);
            startActivity(intent);
        } else if (id == R.id.ln_pg_fm) {
            fmDialog();
        } else if (id == R.id.ln_pg_enabled) {
            binding.swPgEnabled.setChecked(!binding.swPgEnabled.isChecked());
        } else if (id == R.id.ln_r8_enabled) {
            binding.r8Enabled.setChecked(!binding.r8Enabled.isChecked());
        } else if (id == R.id.ln_sw_pg_debug) {
            binding.swPgDebug.setChecked(!binding.swPgDebug.isChecked());
        } else if (id == R.id.ln_stringfog_enabled) {
            binding.swStringfogEnabled.setChecked(!binding.swStringfogEnabled.isChecked());
        } else if (id == R.id.ln_combo_click || id == R.id.btn_activate_combo) {
            showProtectionComboDialog();
        }
    }

    private void showProtectionComboDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("🛡️ Protection Combo (Score 95/100)")
                .setMessage("Terapkan Full Defense Combo Suite:\n\n" +
                        "1. Aktifkan R8 Optimizer & Obfuscator\n" +
                        "2. Auto-Inject Maximum ProGuard Rules (7 passes, repackage, keep rules)\n" +
                        "3. Aktifkan StringFog (AES-256 GCM authenticated)\n" +
                        "4. Injeksi Security Guards (SignatureGuard, AntiDebug, EnvironmentGuard, TamperAwareOutput, CustomClassLoader)\n\n" +
                        "Jalankan one-tap combo sekarang?")
                .setPositiveButton("Aktifkan Combo", (dialog, which) -> applyProtectionCombo())
                .setNegativeButton(R.string.common_word_cancel, null)
                .show();
    }

    private void applyProtectionCombo() {
        // 1. Enable R8 and Shrinking
        pg.setProguardEnabled(true);
        pg.setR8Enabled(true);
        binding.swPgEnabled.setChecked(true);
        binding.r8Enabled.setChecked(true);
        allOptionsUIController(true);

        // 2. Inject Maximum ProGuard Rules
        String rulesPath = pg.getCustomProguardRules();
        String maxRules = DayvenAutonomousSuite.ProGuardInjectorEngine.generateMaximalRules(false);
        FileUtil.writeFile(rulesPath, maxRules);

        // 3. Enable StringFog directly
        if (sf != null) {
            sf.setStringfogEnabled(true);
            binding.swStringfogEnabled.setChecked(true);
        }

        // 4. Inject Java Protection Classes if project source dir exists
        try {
            String javaDir = FileUtil.getExternalStorageDir() + "/.sketchware/mysc/" + scId + "/app/src/main/java";
            if (FileUtil.isExistFile(javaDir)) {
                String pkg = "com.protection.security";
                String targetDir = javaDir + "/" + pkg.replace(".", "/");
                FileUtil.makeDir(targetDir);
                FileUtil.writeFile(targetDir + "/SignatureGuard.java", DayvenAutonomousSuite.ProtectionSuiteEngine.generateSignatureGuard(pkg, "A1:B2:C3:D4"));
                FileUtil.writeFile(targetDir + "/AntiDebug.java", DayvenAutonomousSuite.ProtectionSuiteEngine.generateAntiDebug(pkg));
                FileUtil.writeFile(targetDir + "/EnvironmentGuard.java", DayvenAutonomousSuite.ProtectionSuiteEngine.generateEnvironmentGuard(pkg));
                FileUtil.writeFile(targetDir + "/TamperAwareOutput.java", DayvenAutonomousSuite.ProtectionSuiteEngine.generateTamperAwareOutput(pkg));
                FileUtil.writeFile(targetDir + "/CustomClassLoader.java", DayvenAutonomousSuite.ProtectionSuiteEngine.generateDEXClassLoader(pkg));
            }
        } catch (Exception ignored) {
        }

        SketchwareUtil.toast("🛡️ Protection Combo Berhasil Diterapkan! Score 95/100");
    }

    private void fmDialog() {
        ManageLocalLibrary mll = new ManageLocalLibrary(scId);

        String[] libraries = new String[mll.list.size()];
        boolean[] enabledLibraries = new boolean[mll.list.size()];

        for (int i = 0; i < mll.list.size(); i++) {
            HashMap<String, Object> current = mll.list.get(i);

            Object name = current.get("name");
            if (name instanceof String) {
                libraries[i] = (String) name;
                enabledLibraries[i] = pg.libIsProguardFMEnabled(libraries[i]);
            } else {
                libraries[i] = "(broken library configuration)";
                enabledLibraries[i] = false;
            }
        }

        MaterialAlertDialogBuilder bld = new MaterialAlertDialogBuilder(this);
        bld.setTitle("Select Local libraries");
        bld.setMultiChoiceItems(
                libraries,
                enabledLibraries,
                (dialog, which, isChecked) -> enabledLibraries[which] = isChecked);
        bld.setPositiveButton(
                R.string.common_word_save,
                (dialog, which) -> {
                    ArrayList<String> finalList = new ArrayList<>();

                    for (int i = 0; i < libraries.length; i++) {
                        if (enabledLibraries[i]) {
                            finalList.add(libraries[i]);
                        }
                    }

                    pg.setProguardFMLibs(finalList);
                });
        bld.setNegativeButton(R.string.common_word_cancel, null);
        bld.create().show();
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        int id = buttonView.getId();
        if (id == binding.swPgEnabled.getId()) {
            pg.setProguardEnabled(isChecked);
            allOptionsUIController(isChecked);
        } else if (id == binding.r8Enabled.getId()) {
            pg.setR8Enabled(isChecked);
        } else if (id == binding.swPgDebug.getId()) {
            pg.setDebugEnabled(isChecked);
        } else if (id == binding.swStringfogEnabled.getId()) {
            if (sf != null) {
                sf.setStringfogEnabled(isChecked);
            }
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        enableEdgeToEdgeNoContrast();
        binding = ManageProguardBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        scId = getIntent().getStringExtra("sc_id");
        initialize();
        initializeLogic();

        if (!DRFeatureManager.isAllowR8()) {
            binding.lnPgEnabled.setAlpha(0.5f);
            binding.lnPgEnabled.setEnabled(false);
        }
    }

    private void initialize() {
        binding.swPgEnabled.setOnCheckedChangeListener(this);
        binding.lnPgRules.setOnClickListener(this);
        binding.r8Enabled.setOnCheckedChangeListener(this);
        binding.swPgDebug.setOnCheckedChangeListener(this);
        binding.lnPgFm.setOnClickListener(this);
        binding.lnPgEnabled.setOnClickListener(this);
        binding.lnR8Enabled.setOnClickListener(this);
        binding.lnSwPgDebug.setOnClickListener(this);

        binding.lnStringfogEnabled.setOnClickListener(this);
        binding.swStringfogEnabled.setOnCheckedChangeListener(this);
        binding.lnComboClick.setOnClickListener(this);
        binding.btnActivateCombo.setOnClickListener(this);
    }

    private void initializeLogic() {
        _initToolbar();
        pg = new ProguardHandler(scId);
        sf = new StringfogHandler(scId);

        binding.swPgEnabled.setChecked(pg.isShrinkingEnabled());
        binding.swPgDebug.setChecked(pg.isDebugFilesEnabled());
        binding.r8Enabled.setChecked(pg.isR8Enabled());
        binding.swStringfogEnabled.setChecked(sf.isStringfogEnabled());
    }

    private void _initToolbar() {
        setSupportActionBar(binding.toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        binding.toolbar.setNavigationOnClickListener(view -> onBackPressed());
        binding.toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 9001) {
                showProtectionComboDialog();
                return true;
            }
            return false;
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuItem item = menu.add(0, 9001, 0, "Protection Combo");
        item.setIcon(R.drawable.ic_mtrl_shield_lock);
        item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        return true;
    }

    private void allOptionsUIController(boolean isEnabled) {
        if (isEnabled) {
            binding.lnAllOptions.setAlpha(1f);
        } else {
            binding.lnAllOptions.setAlpha(0.5f);
        }

        binding.lnPgRules.setEnabled(isEnabled);
        binding.lnPgFm.setEnabled(isEnabled);
        binding.r8Enabled.setEnabled(isEnabled);
        binding.swPgDebug.setEnabled(isEnabled);
        binding.lnR8Enabled.setEnabled(isEnabled);
        binding.lnSwPgDebug.setEnabled(isEnabled);
        binding.lnStringfogEnabled.setEnabled(isEnabled);
        binding.swStringfogEnabled.setEnabled(isEnabled);
    }
}
