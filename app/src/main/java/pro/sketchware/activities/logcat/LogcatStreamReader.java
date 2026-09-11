package pro.sketchware.activities.logcat;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Reads logcat output in real-time on a background thread.
 * Buffers entries and dispatches them to the UI at ~10fps intervals.
 *
 * Features:
 *  - Package name filtering (tag/message based)
 *  - Minimum log level filter
 *  - Text search filter
 *  - Crash-only mode
 *  - Buffered batched dispatch for smooth UI
 */
public class LogcatStreamReader {

    private static final long DISPATCH_INTERVAL_MS = 100;

    public interface Callback {
        void onNewEntries(@NonNull List<LogEntry> entries);
        void onStreamError(@NonNull String error);
        void onStreamStarted();
    }

    private final Callback callback;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final List<LogEntry> buffer = new ArrayList<>();

    @Nullable private Thread readerThread;
    @Nullable private Process logcatProcess;
    @Nullable private String packageFilter;
    @Nullable private String searchFilter;
    private LogEntry.Level minLevel = LogEntry.Level.VERBOSE;
    private boolean crashOnlyMode = false;

    public LogcatStreamReader(@NonNull Callback callback) {
        this.callback = callback;
    }

    public void setPackageFilter(@Nullable String packageName) { this.packageFilter = packageName; }
    public void setSearchFilter(@Nullable String query) { this.searchFilter = query; }
    public void setMinLevel(@NonNull LogEntry.Level level) { this.minLevel = level; }
    public void setCrashOnlyMode(boolean crashOnly) { this.crashOnlyMode = crashOnly; }
    public boolean isRunning() { return running.get(); }

    public void start() {
        if (running.getAndSet(true)) return;

        readerThread = new Thread(() -> {
            try {
                Runtime.getRuntime().exec(new String[]{"logcat", "-c"}).waitFor();

                List<String> cmd = new ArrayList<>();
                cmd.add("logcat");
                cmd.add("-v");
                cmd.add("threadtime");
                if (minLevel.ordinal() > LogEntry.Level.VERBOSE.ordinal()) {
                    cmd.add("*:" + minLevel.letter);
                }

                ProcessBuilder pb = new ProcessBuilder(cmd);
                pb.redirectErrorStream(true);
                logcatProcess = pb.start();

                mainHandler.post(callback::onStreamStarted);

                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(logcatProcess.getInputStream()));
                mainHandler.post(dispatchRunnable);

                String line;
                while (running.get() && (line = reader.readLine()) != null) {
                    if (line.startsWith("---") || line.isEmpty()) continue;
                    LogEntry entry = LogEntry.parse(line);
                    if (!passesFilters(entry)) continue;
                    synchronized (buffer) {
                        buffer.add(entry);
                    }
                }
                reader.close();
            } catch (IOException | InterruptedException e) {
                if (running.get()) {
                    mainHandler.post(() -> callback.onStreamError(
                            e.getMessage() != null ? e.getMessage() : "Logcat stream interrupted"));
                }
            } finally {
                running.set(false);
                mainHandler.removeCallbacks(dispatchRunnable);
                mainHandler.post(this::flushBuffer);
            }
        }, "LogcatStreamReader");
        readerThread.setDaemon(true);
        readerThread.start();
    }

    public void stop() {
        running.set(false);
        mainHandler.removeCallbacks(dispatchRunnable);
        if (logcatProcess != null) { logcatProcess.destroy(); logcatProcess = null; }
        if (readerThread != null) { readerThread.interrupt(); readerThread = null; }
    }

    private boolean passesFilters(LogEntry entry) {
        if (entry.level().ordinal() < minLevel.ordinal()) return false;
        if (crashOnlyMode && !entry.isCrashRelated()) return false;
        if (packageFilter != null && !packageFilter.isEmpty()) {
            if (!entry.tag().toLowerCase().contains(packageFilter.toLowerCase()) &&
                !entry.message().toLowerCase().contains(packageFilter.toLowerCase())) {
                if (!entry.tag().equals("AndroidRuntime") && !entry.isCrashRelated()) return false;
            }
        }
        if (searchFilter != null && !searchFilter.isEmpty()) {
            String lower = searchFilter.toLowerCase();
            return entry.tag().toLowerCase().contains(lower) ||
                    entry.message().toLowerCase().contains(lower);
        }
        return true;
    }

    private final Runnable dispatchRunnable = new Runnable() {
        @Override
        public void run() {
            flushBuffer();
            if (running.get()) mainHandler.postDelayed(this, DISPATCH_INTERVAL_MS);
        }
    };

    private void flushBuffer() {
        List<LogEntry> batch;
        synchronized (buffer) {
            if (buffer.isEmpty()) return;
            batch = new ArrayList<>(buffer);
            buffer.clear();
        }
        callback.onNewEntries(batch);
    }
}
