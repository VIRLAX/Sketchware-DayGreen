package pro.sketchware.editor;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Dayven AI v3.0 layered memory.
 *
 * <p>Keeps a persistent, multi level snapshot stack per project so the agent can roll back
 * grouped (multi file) changes exactly like a real IDE undo history, and exposes a compact
 * digest of everything it has already touched so the model never loses project awareness
 * between turns.</p>
 */
public final class AgentMemoryEngine {
    private static final String PREFS = "daygreen_agent_memory_v1";
    private static final String STACK = "stack_";
    private static final String LOG = "log_";
    private static final int MAX_DEPTH = 24;
    private static final int MAX_LOG = 60;

    private static final Pattern ROLLBACK = Pattern.compile(
            "(?i)(^|\\s)(undo|rollback|revert|balikin|balik ke|kembalikan|restore|batalkan perubahan|"
                    + "seperti semula|kayak semula|seperti sebelumnya|versi sebelumnya|ke yang tadi|yang tadi deh)(\\s|$|[.!?,])");
    private static final Pattern NEGATED = Pattern.compile("(?i)\\b(jangan|tanpa|kecuali)\\b");
    private static final Pattern CHANGELOG = Pattern.compile(
            "(?i)^\\s*(changelog|riwayat perubahan|history perubahan|apa saja yang sudah diubah|list perubahan)\\s*[.!?]?\\s*$");

    private final SharedPreferences prefs;

    public AgentMemoryEngine(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** One file level snapshot captured right before a write. */
    public static final class Entry {
        public String path = "";
        public String display = "";
        public boolean existed;
        public String previous = "";

        public Entry() { }

        public Entry(String path, String display, boolean existed, String previous) {
            this.path = path == null ? "" : path;
            this.display = display == null ? this.path : display;
            this.existed = existed;
            this.previous = previous == null ? "" : previous;
        }
    }

    public static Entry entry(String path, String display, boolean existed, String previous) {
        return new Entry(path, display, existed, previous);
    }

    /** True when the user is asking to restore the previous version of their code. */
    public static boolean isRollbackIntent(String prompt) {
        if (prompt == null) return false;
        String p = prompt.trim();
        if (p.isEmpty() || p.length() > 160) return false;
        if (NEGATED.matcher(p).find()) return false;
        return ROLLBACK.matcher(p).find();
    }

    /** True when the user asks for the internal changelog of this session. */
    public static boolean isChangelogIntent(String prompt) {
        return prompt != null && CHANGELOG.matcher(prompt).find();
    }

    /** Stores one grouped change (can hold many files) on top of the rollback stack. */
    public synchronized void pushBatch(String scId, List<Entry> entries, String label) {
        if (scId == null || scId.trim().isEmpty() || entries == null || entries.isEmpty()) return;
        try {
            JSONArray stack = stack(scId);
            JSONArray files = new JSONArray();
            StringBuilder names = new StringBuilder();
            for (Entry e : entries) {
                JSONObject o = new JSONObject();
                o.put("path", e.path);
                o.put("display", e.display);
                o.put("existed", e.existed);
                o.put("previous", encode(e.previous));
                files.put(o);
                if (names.length() > 0) names.append(", ");
                names.append(e.display);
            }
            JSONObject batch = new JSONObject();
            batch.put("label", label == null || label.trim().isEmpty() ? names.toString() : label.trim());
            batch.put("files", files);
            batch.put("names", names.toString());
            batch.put("time", System.currentTimeMillis());
            stack.put(batch);
            while (stack.length() > MAX_DEPTH) stack.remove(0);
            prefs.edit().putString(STACK + scId, stack.toString()).commit();
            appendLog(scId, batch.optString("label") + " -> " + names);
        } catch (Exception ignored) { }
    }

    /** Human readable label of the change that would be reverted next, or null when empty. */
    public synchronized String peekLabel(String scId) {
        JSONArray stack = stack(scId);
        if (stack.length() == 0) return null;
        JSONObject last = stack.optJSONObject(stack.length() - 1);
        if (last == null) return null;
        String names = last.optString("names", "");
        String label = last.optString("label", "");
        if (names.isEmpty()) return label;
        return label.equals(names) ? names : label + " (" + names + ")";
    }

    /** Number of restorable steps left for this project. */
    public synchronized int depth(String scId) {
        return stack(scId).length();
    }

    /** Files that would come back if the user accepts a rollback right now. */
    public synchronized List<String> peekFiles(String scId) {
        List<String> out = new ArrayList<>();
        JSONArray stack = stack(scId);
        if (stack.length() == 0) return out;
        JSONObject last = stack.optJSONObject(stack.length() - 1);
        JSONArray files = last == null ? null : last.optJSONArray("files");
        if (files == null) return out;
        for (int i = 0; i < files.length(); i++) {
            JSONObject o = files.optJSONObject(i);
            if (o != null) out.add(o.optString("display", o.optString("path", "?")));
        }
        return out;
    }

    /** Content of a file as it was before the last change, for preview purposes. */
    public synchronized String peekPrevious(String scId, int index) {
        JSONArray stack = stack(scId);
        if (stack.length() == 0) return "";
        JSONObject last = stack.optJSONObject(stack.length() - 1);
        JSONArray files = last == null ? null : last.optJSONArray("files");
        if (files == null || index < 0 || index >= files.length()) return "";
        JSONObject o = files.optJSONObject(index);
        return o == null ? "" : decode(o.optString("previous", ""));
    }

    /** Restores every file captured in the newest snapshot batch and pops it off the stack. */
    public synchronized String rollbackLast(String scId) {
        if (scId == null || scId.trim().isEmpty()) return "Belum ada project yang dipilih.";
        JSONArray stack = stack(scId);
        if (stack.length() == 0) return "Belum ada perubahan yang bisa dikembalikan.";
        JSONObject last = stack.optJSONObject(stack.length() - 1);
        if (last == null) {
            stack.remove(stack.length() - 1);
            prefs.edit().putString(STACK + scId, stack.toString()).commit();
            return "Snapshot terakhir tidak valid, sudah dibersihkan.";
        }

        JSONArray files = last.optJSONArray("files");
        int restored = 0;
        int removed = 0;
        StringBuilder failed = new StringBuilder();
        if (files != null) {
            for (int i = 0; i < files.length(); i++) {
                JSONObject o = files.optJSONObject(i);
                if (o == null) continue;
                File target = new File(o.optString("path", ""));
                try {
                    if (o.optBoolean("existed", false)) {
                        File parent = target.getParentFile();
                        if (parent != null && !parent.exists()) parent.mkdirs();
                        Files.write(target.toPath(), decode(o.optString("previous", "")).getBytes(StandardCharsets.UTF_8));
                        restored++;
                    } else if (target.isFile() && target.delete()) {
                        removed++;
                    }
                } catch (Exception e) {
                    if (failed.length() > 0) failed.append(", ");
                    failed.append(o.optString("display", target.getName()));
                }
            }
        }

        stack.remove(stack.length() - 1);
        prefs.edit().putString(STACK + scId, stack.toString()).commit();
        appendLog(scId, "ROLLBACK " + last.optString("names", ""));

        StringBuilder msg = new StringBuilder();
        msg.append("Rollback selesai: ").append(restored).append(" file dikembalikan");
        if (removed > 0) msg.append(", ").append(removed).append(" file baru dihapus");
        if (failed.length() > 0) msg.append(". Gagal: ").append(failed);
        msg.append(". Sisa ").append(stack.length()).append(" langkah undo.");
        return msg.toString();
    }

    /** Drops the whole rollback stack and changelog for this project. */
    public synchronized void clear(String scId) {
        if (scId == null) return;
        prefs.edit().remove(STACK + scId).remove(LOG + scId).commit();
    }

    /** Internal changelog lines, newest last. */
    public synchronized List<String> changelog(String scId) {
        List<String> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(prefs.getString(LOG + (scId == null ? "" : scId), "[]"));
            for (int i = 0; i < arr.length(); i++) out.add(arr.optString(i, ""));
        } catch (Exception ignored) { }
        return out;
    }

    /**
     * Compact memory block injected into every prompt so the model keeps full awareness of
     * the files it already created or edited in this project.
     */
    public synchronized String digest(String scId) {
        JSONArray stack = stack(scId);
        if (stack.length() == 0) return "";
        StringBuilder sb = new StringBuilder();
        sb.append("MEMORI PERUBAHAN (terbaru di bawah, ").append(stack.length()).append(" langkah undo tersedia):\n");
        int from = Math.max(0, stack.length() - 8);
        for (int i = from; i < stack.length(); i++) {
            JSONObject o = stack.optJSONObject(i);
            if (o == null) continue;
            sb.append("- ").append(o.optString("names", "?"));
            String label = o.optString("label", "");
            if (!label.isEmpty() && !label.equals(o.optString("names", ""))) sb.append(" [").append(label).append("]");
            sb.append('\n');
        }
        return sb.toString().trim();
    }

    private JSONArray stack(String scId) {
        try {
            return new JSONArray(prefs.getString(STACK + (scId == null ? "" : scId), "[]"));
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    private void appendLog(String scId, String line) {
        if (scId == null) return;
        try {
            JSONArray arr = new JSONArray(prefs.getString(LOG + scId, "[]"));
            arr.put(line);
            while (arr.length() > MAX_LOG) arr.remove(0);
            prefs.edit().putString(LOG + scId, arr.toString()).commit();
        } catch (Exception ignored) { }
    }

    private static String encode(String value) {
        return Base64.encodeToString((value == null ? "" : value).getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
    }

    private static String decode(String value) {
        try {
            return new String(Base64.decode(value == null ? "" : value, Base64.DEFAULT), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }
}
