package pro.sketchware.activities.preview;

import android.util.Log;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

/** Small in-memory log stream shared by the native XML preview surfaces. */
public final class PreviewLog {
    public interface Listener {
        void onLogChanged(List<String> lines);
    }

    private static final String TAG = "DayGreenPreview";
    private static final int MAX_LINES = 80;
    private static final Deque<String> lines = new ArrayDeque<>();
    private static Listener listener;

    private PreviewLog() { }

    public static synchronized void setListener(Listener next) {
        listener = next;
        if (next != null) next.onLogChanged(snapshot());
    }

    public static synchronized void clear() {
        lines.clear();
        notifyListener();
    }

    public static synchronized void log(String message) {
        String value = message == null ? "" : message.trim();
        if (value.isEmpty()) return;
        String line = "[" + android.text.format.DateFormat.format("HH:mm:ss", System.currentTimeMillis()) + "] " + value;
        if (lines.size() >= MAX_LINES) lines.removeFirst();
        lines.addLast(line);
        Log.d(TAG, value);
        notifyListener();
    }

    public static synchronized List<String> snapshot() {
        return Collections.unmodifiableList(new ArrayList<>(lines));
    }

    private static void notifyListener() {
        if (listener != null) listener.onLogChanged(snapshot());
    }
}
