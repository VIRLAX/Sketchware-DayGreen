package pro.sketchware.activities.logcat;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.besome.sketch.lib.base.BaseAppCompatActivity;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import pro.sketchware.R;
import pro.sketchware.databinding.ActivityRealtimeLogcatBinding;
import pro.sketchware.utility.FileUtil;
import pro.sketchware.utility.SketchwareUtil;
import pro.sketchware.utility.UI;

/**
 * Real-time Logcat Viewer with live streaming, level filtering,
 * crash detection, search highlighting, pause/resume, and export.
 *
 * Launch with optional intent extras:
 *   - "package_name" (String): filter logs by package
 *
 * Register in AndroidManifest.xml:
 *   <activity android:name=".activities.logcat.RealtimeLogcatActivity" />
 */
public class RealtimeLogcatActivity extends BaseAppCompatActivity implements LogcatStreamReader.Callback {

    private ActivityRealtimeLogcatBinding binding;
    private LogcatStreamReader reader;
    private LogcatAdapter adapter;
    private boolean isPaused = false;
    private int totalEntries = 0;
    private int errorCount = 0;
    private int warningCount = 0;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);
        binding = ActivityRealtimeLogcatBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setupToolbar();
        setupRecyclerView();
        setupFilters();
        setupSearch();
        binding.liveIndicator.setText("\u25cf LIVE");
        binding.liveIndicator.setTextColor(0xFF66BB6A);

        UI.addSystemWindowInsetToPadding(binding.appBarLayout, true, true, true, false);
        startLogcat();
    }

    private void setupToolbar() {
        binding.topAppBar.setNavigationOnClickListener(v -> onBackPressed());
        Menu menu = binding.topAppBar.getMenu();
        menu.clear();
        menu.add(Menu.NONE, 1, 0, "Pause").setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        menu.add(Menu.NONE, 2, 1, "Clear").setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        menu.add(Menu.NONE, 3, 2, "Export Log");
        menu.add(Menu.NONE, 4, 3, "Crash Only").setCheckable(true);
        menu.add(Menu.NONE, 5, 4, "Auto-scroll").setCheckable(true).setChecked(true);

        binding.topAppBar.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1: togglePause(item); return true;
                case 2: clearLog(); return true;
                case 3: exportLog(); return true;
                case 4:
                    item.setChecked(!item.isChecked());
                    reader.setCrashOnlyMode(item.isChecked());
                    restartLogcat();
                    return true;
                case 5:
                    item.setChecked(!item.isChecked());
                    adapter.setAutoScroll(item.isChecked());
                    return true;
            }
            return false;
        });
    }

    private void setupRecyclerView() {
        adapter = new LogcatAdapter();
        LinearLayoutManager lm = new LinearLayoutManager(this);
        lm.setStackFromEnd(true);
        binding.logsRecyclerView.setLayoutManager(lm);
        binding.logsRecyclerView.setAdapter(adapter);
        binding.logsRecyclerView.setItemAnimator(null);

        binding.logsRecyclerView.addOnScrollListener(new androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull androidx.recyclerview.widget.RecyclerView rv, int newState) {
                if (newState == androidx.recyclerview.widget.RecyclerView.SCROLL_STATE_DRAGGING) {
                    adapter.setAutoScroll(false);
                    binding.scrollToBottomFab.setVisibility(View.VISIBLE);
                }
            }
        });

        binding.scrollToBottomFab.setOnClickListener(v -> {
            adapter.setAutoScroll(true);
            binding.scrollToBottomFab.setVisibility(View.GONE);
            if (adapter.getItemCount() > 0)
                binding.logsRecyclerView.scrollToPosition(adapter.getItemCount() - 1);
        });
    }

    private void setupFilters() {
        binding.chipVerbose.setOnCheckedChangeListener((btn, c) -> updateLevelFilter());
        binding.chipDebug.setOnCheckedChangeListener((btn, c) -> updateLevelFilter());
        binding.chipInfo.setOnCheckedChangeListener((btn, c) -> updateLevelFilter());
        binding.chipWarn.setOnCheckedChangeListener((btn, c) -> updateLevelFilter());
        binding.chipError.setOnCheckedChangeListener((btn, c) -> updateLevelFilter());

        String pkg = getIntent().getStringExtra("package_name");
        if (pkg != null && !pkg.isEmpty()) {
            binding.packageFilterChip.setText(pkg);
            binding.packageFilterChip.setVisibility(View.VISIBLE);
            binding.packageFilterChip.setOnCloseIconClickListener(v -> {
                binding.packageFilterChip.setVisibility(View.GONE);
                reader.setPackageFilter(null);
                restartLogcat();
            });
        }
    }

    private void updateLevelFilter() {
        LogEntry.Level min = LogEntry.Level.VERBOSE;
        if (!binding.chipVerbose.isChecked()) min = LogEntry.Level.DEBUG;
        if (!binding.chipDebug.isChecked() && min.ordinal() <= LogEntry.Level.DEBUG.ordinal()) min = LogEntry.Level.INFO;
        if (!binding.chipInfo.isChecked() && min.ordinal() <= LogEntry.Level.INFO.ordinal()) min = LogEntry.Level.WARN;
        if (!binding.chipWarn.isChecked() && min.ordinal() <= LogEntry.Level.WARN.ordinal()) min = LogEntry.Level.ERROR;
        reader.setMinLevel(min);
        restartLogcat();
    }

    private void setupSearch() {
        binding.searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                String q = s.toString().trim();
                adapter.setHighlightQuery(q.isEmpty() ? null : q);
                reader.setSearchFilter(q.isEmpty() ? null : q);
            }
        });
    }

    private void startLogcat() {
        reader = new LogcatStreamReader(this);
        String pkg = getIntent().getStringExtra("package_name");
        if (pkg != null) reader.setPackageFilter(pkg);
        reader.start();
        binding.noContentLayout.setVisibility(View.VISIBLE);
        binding.logsRecyclerView.setVisibility(View.GONE);
    }

    private void restartLogcat() {
        if (reader != null) reader.stop();
        adapter.clear();
        totalEntries = errorCount = warningCount = 0;
        updateStats();
        startLogcat();
    }

    private void togglePause(MenuItem item) {
        isPaused = !isPaused;
        if (isPaused) {
            reader.stop();
            item.setTitle("Resume");
            binding.liveIndicator.setText("\u25cf PAUSED");
            binding.liveIndicator.setTextColor(0xFFFFA726);
        } else {
            item.setTitle("Pause");
            binding.liveIndicator.setText("\u25cf LIVE");
            binding.liveIndicator.setTextColor(0xFF66BB6A);
            startLogcat();
        }
    }

    private void clearLog() {
        adapter.clear();
        totalEntries = errorCount = warningCount = 0;
        updateStats();
        binding.noContentLayout.setVisibility(View.VISIBLE);
        binding.logsRecyclerView.setVisibility(View.GONE);
    }

    private void exportLog() {
        List<LogEntry> entries = adapter.getEntries();
        if (entries.isEmpty()) { SketchwareUtil.toast("No logs to export"); return; }
        String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        String dir = FileUtil.getExternalStorageDir() + "/.sketchware/logs/";
        new File(dir).mkdirs();
        File file = new File(dir, "logcat_" + ts + ".txt");
        try (FileWriter w = new FileWriter(file)) {
            w.write("=== Sketchware DayGreen Logcat Export ===\n");
            w.write("Date: " + new Date() + "\n");
            w.write("Entries: " + entries.size() + " | Errors: " + errorCount + " | Warnings: " + warningCount + "\n");
            w.write("=========================================\n\n");
            for (LogEntry e : entries) w.write(e.raw() + "\n");
            SketchwareUtil.toast("Exported to logcat_" + ts + ".txt");
        } catch (IOException e) {
            SketchwareUtil.toastError("Export failed: " + e.getMessage());
        }
    }

    private void updateStats() {
        binding.statsText.setText(totalEntries + " entries | " + errorCount + " errors | " + warningCount + " warnings");
    }

    @Override
    public void onNewEntries(@NonNull List<LogEntry> entries) {
        if (isPaused) return;
        binding.noContentLayout.setVisibility(View.GONE);
        binding.logsRecyclerView.setVisibility(View.VISIBLE);
        adapter.addEntries(entries);
        totalEntries += entries.size();
        for (LogEntry e : entries) {
            if (e.level() == LogEntry.Level.ERROR || e.level() == LogEntry.Level.FATAL) errorCount++;
            if (e.level() == LogEntry.Level.WARN) warningCount++;
        }
        updateStats();
        if (errorCount > 0) {
            binding.errorBadge.setVisibility(View.VISIBLE);
            binding.errorBadge.setText(String.valueOf(errorCount));
        }
    }

    @Override
    public void onStreamError(@NonNull String error) {
        SketchwareUtil.toastError("Logcat: " + error, Toast.LENGTH_LONG);
    }

    @Override
    public void onStreamStarted() {
        binding.liveIndicator.setText("\u25cf LIVE");
        binding.liveIndicator.setTextColor(0xFF66BB6A);
    }

    @Override
    protected void onDestroy() {
        if (reader != null) reader.stop();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (reader != null) reader.stop();
        super.onBackPressed();
    }
}
