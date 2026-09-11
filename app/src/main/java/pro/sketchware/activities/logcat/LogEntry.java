package pro.sketchware.activities.logcat;

import androidx.annotation.NonNull;

/**
 * Represents a single parsed logcat entry.
 * Supports threadtime format parsing and crash detection.
 */
public record LogEntry(
        long timestamp,
        int pid,
        int tid,
        @NonNull Level level,
        @NonNull String tag,
        @NonNull String message,
        @NonNull String raw
) {
    public enum Level {
        VERBOSE("V", 0xFF9E9E9E),
        DEBUG("D", 0xFF42A5F5),
        INFO("I", 0xFF66BB6A),
        WARN("W", 0xFFFFA726),
        ERROR("E", 0xFFEF5350),
        FATAL("F", 0xFFFF1744),
        SILENT("S", 0xFF757575);

        public final String letter;
        public final int color;

        Level(String letter, int color) {
            this.letter = letter;
            this.color = color;
        }

        public static Level fromLetter(String letter) {
            for (Level l : values()) {
                if (l.letter.equals(letter)) return l;
            }
            return VERBOSE;
        }
    }

    /** Parses a raw logcat line (threadtime format). */
    public static LogEntry parse(String raw) {
        if (raw == null || raw.length() < 30) {
            return new LogEntry(System.currentTimeMillis(), 0, 0, Level.VERBOSE, "?", raw != null ? raw : "", raw != null ? raw : "");
        }
        try {
            String[] parts = raw.split("\\s+", 6);
            if (parts.length < 6) {
                return new LogEntry(System.currentTimeMillis(), 0, 0, Level.VERBOSE, "?", raw, raw);
            }
            int pid = safeParseInt(parts[2]);
            int tid = safeParseInt(parts[3]);
            Level level = Level.fromLetter(parts[4]);
            String rest = parts[5];
            int colonIdx = rest.indexOf(':');
            String tag = colonIdx >= 0 ? rest.substring(0, colonIdx).trim() : "?";
            String msg = colonIdx >= 0 && colonIdx + 1 < rest.length() ? rest.substring(colonIdx + 1).trim() : rest;
            return new LogEntry(System.currentTimeMillis(), pid, tid, level, tag, msg, raw);
        } catch (Exception e) {
            return new LogEntry(System.currentTimeMillis(), 0, 0, Level.VERBOSE, "?", raw, raw);
        }
    }

    private static int safeParseInt(String s) {
        try { return Integer.parseInt(s.trim()); }
        catch (Exception e) { return 0; }
    }

    /** Checks if this entry is crash/exception related. */
    public boolean isCrashRelated() {
        return level == Level.ERROR || level == Level.FATAL ||
                message.contains("Exception") ||
                message.contains("FATAL EXCEPTION") ||
                (message.contains("at ") && message.contains("(") && message.contains(")")) ||
                tag.equals("AndroidRuntime") ||
                tag.equals("FATAL");
    }
}
