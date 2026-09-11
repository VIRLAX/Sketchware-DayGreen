package pro.sketchware.editor.diagnostics;

import android.os.Handler;
import android.os.Looper;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.rosemoe.sora.event.ContentChangeEvent;
import io.github.rosemoe.sora.lang.styling.Span;
import io.github.rosemoe.sora.lang.styling.Spans;
import io.github.rosemoe.sora.lang.styling.Styles;
import io.github.rosemoe.sora.lang.styling.span.SpanExtAttrs;
import io.github.rosemoe.sora.widget.CodeEditor;

import pro.sketchware.utility.RealtimeDiagnostics;
import pro.sketchware.utility.RealtimeDiagnostics.Issue;
import pro.sketchware.utility.RealtimeDiagnostics.Kind;

/**
 * Manages real-time diagnostics for the code editor.
 * Runs analysis on a background thread with debouncing (300ms),
 * then applies squiggly underlines and updates the diagnostics panel.
 *
 * Usage:
 *   LiveDiagnosticsManager mgr = new LiveDiagnosticsManager(codeEditor, diagnosticsRecyclerView, badgeView);
 *   mgr.setLanguage(Kind.JAVA);
 *   mgr.attach();
 *   mgr.detach();
 */
public class LiveDiagnosticsManager {

    private static final long DEBOUNCE_MS = 300;

    private final CodeEditor editor;
    private final RecyclerView diagnosticsList;
    @Nullable private final View errorBadge;
    @Nullable private final android.widget.TextView errorCountText;

    private Kind currentKind = Kind.JAVA;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicInteger version = new AtomicInteger(0);
    private final Runnable debounceRunnable = this::scheduleAnalysis;

    private DiagnosticsAdapter adapter;
    private List<Issue> latestIssues = Collections.emptyList();
    private boolean attached = false;

    @Nullable
    private OnDiagnosticsUpdateListener listener;

    public interface OnDiagnosticsUpdateListener {
        void onDiagnosticsUpdated(int errorCount, int warningCount);
    }

    public LiveDiagnosticsManager(@NonNull CodeEditor editor,
                                   @NonNull RecyclerView diagnosticsList,
                                   @Nullable View errorBadge,
                                   @Nullable android.widget.TextView errorCountText) {
        this.editor = editor;
        this.diagnosticsList = diagnosticsList;
        this.errorBadge = errorBadge;
        this.errorCountText = errorCountText;
    }

    public void setLanguage(Kind kind) {
        this.currentKind = kind;
        if (attached) triggerAnalysis();
    }

    public void setOnDiagnosticsUpdateListener(@Nullable OnDiagnosticsUpdateListener listener) {
        this.listener = listener;
    }

    public void attach() {
        if (attached) return;
        attached = true;

        adapter = new DiagnosticsAdapter(issue -> {
            if (issue.line() > 0 && issue.line() <= editor.getText().getLineCount()) {
                int line = issue.line() - 1;
                int col = Math.max(0, issue.column() - 1);
                int lineLen = editor.getText().getColumnCount(line);
                col = Math.min(col, lineLen);
                editor.setSelection(line, col);
                editor.ensurePositionVisible(line, col);
            }
        });
        diagnosticsList.setLayoutManager(new LinearLayoutManager(editor.getContext()));
        diagnosticsList.setAdapter(adapter);

        editor.subscribeEvent(ContentChangeEvent.class, (event, unsubscribe) -> {
            if (!attached) {
                unsubscribe.unsubscribe();
                return;
            }
            mainHandler.removeCallbacks(debounceRunnable);
            mainHandler.postDelayed(debounceRunnable, DEBOUNCE_MS);
        });

        triggerAnalysis();
    }

    public void detach() {
        attached = false;
        mainHandler.removeCallbacks(debounceRunnable);
        executor.shutdownNow();
    }

    public List<Issue> getLatestIssues() {
        return latestIssues;
    }

    public void triggerAnalysis() {
        mainHandler.removeCallbacks(debounceRunnable);
        scheduleAnalysis();
    }

    private void scheduleAnalysis() {
        int thisVersion = version.incrementAndGet();
        String source = editor.getText().toString();
        Kind kind = currentKind;

        executor.submit(() -> {
            if (thisVersion != version.get()) return;

            List<Issue> issues = RealtimeDiagnostics.check(kind, source);

            if (thisVersion != version.get()) return;

            mainHandler.post(() -> {
                if (thisVersion != version.get()) return;
                latestIssues = issues;
                applySquigglyLines(issues);
                updatePanel(issues);
            });
        });
    }

    private void applySquigglyLines(List<Issue> issues) {
        Styles styles = editor.getStyles();
        if (styles == null || styles.spans == null) return;

        Spans.Reader reader = styles.spans.read();
        Spans.Modifier modifier = styles.spans.supportsModify() ? styles.spans.modify() : null;
        if (modifier == null) return;

        int lineCount = editor.getText().getLineCount();

        for (int line = 0; line < lineCount; line++) {
            List<Span> spans = new ArrayList<>(reader.getSpansOnLine(line));
            boolean changed = false;
            for (Span span : spans) {
                Object ext = span.getSpanExt(SpanExtAttrs.EXT_EXTERNAL_RENDERER);
                if (ext instanceof SquigglyLineRenderer) {
                    span.setSpanExt(SpanExtAttrs.EXT_EXTERNAL_RENDERER, null);
                    changed = true;
                }
            }
            if (changed) modifier.setSpansOnLine(line, spans);
        }

        for (Issue issue : issues) {
            int line = issue.line() - 1;
            if (line < 0 || line >= lineCount) continue;

            List<Span> lineSpans = new ArrayList<>(reader.getSpansOnLine(line));
            int severity = "error".equals(issue.severity())
                    ? SquigglyLineRenderer.SEVERITY_ERROR
                    : "warning".equals(issue.severity())
                    ? SquigglyLineRenderer.SEVERITY_WARNING
                    : SquigglyLineRenderer.SEVERITY_INFO;

            SquigglyLineRenderer renderer = new SquigglyLineRenderer(severity, issue.message());

            for (Span span : lineSpans) {
                Object existing = span.getSpanExt(SpanExtAttrs.EXT_EXTERNAL_RENDERER);
                if (existing == null || !(existing instanceof pro.sketchware.editor.ColorPreviewRenderer)) {
                    span.setSpanExt(SpanExtAttrs.EXT_EXTERNAL_RENDERER, renderer);
                }
            }
            modifier.setSpansOnLine(line, lineSpans);
        }

        styles.finishBuilding();
        editor.invalidate();
    }

    private void updatePanel(List<Issue> issues) {
        if (adapter != null) {
            adapter.submitList(issues);
        }

        int errorCount = 0;
        int warningCount = 0;
        for (Issue issue : issues) {
            if ("error".equals(issue.severity())) errorCount++;
            else warningCount++;
        }

        if (errorBadge != null) {
            errorBadge.setVisibility(issues.isEmpty() ? View.GONE : View.VISIBLE);
        }
        if (errorCountText != null) {
            errorCountText.setText(errorCount + " errors, " + warningCount + " warnings");
            errorCountText.setVisibility(issues.isEmpty() ? View.GONE : View.VISIBLE);
        }

        if (listener != null) {
            listener.onDiagnosticsUpdated(errorCount, warningCount);
        }
    }
}
