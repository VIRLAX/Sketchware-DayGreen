package pro.sketchware.editor;

import android.content.Context;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import pro.sketchware.utility.FilePathUtil;

/**
 * Parses Dayven AI's explicit action protocol and writes only after the user taps Accept.
 *
 * <p>v3.0 understands many TARGET + fenced block pairs in a single response, so one instruction
 * can deliver a complete feature (layout + activity + resources) that is applied as one
 * reversible snapshot batch.</p>
 */
public final class AgentActionExecutor {
    private static final Pattern TARGET = Pattern.compile("(?im)^\\s*(?:TARGET|FILE)\\s*:\\s*([^\\r\\n]+)");
    private static final Pattern FENCE = Pattern.compile("(?is)```([a-zA-Z0-9_+#.-]*)\\s*\\r?\\n(.*?)```");
    private static final Pattern MD_LINK = Pattern.compile("^\\[([^\\]]+)\\]\\([^)]*\\)$");

    public static final class Action {
        public final String target;
        public final String language;
        public final String content;

        private Action(String target, String language, String content) {
            this.target = target;
            this.language = language;
            this.content = content;
        }

        public boolean isXml() { return target.toLowerCase(Locale.US).endsWith(".xml") || "xml".equals(language); }
        public boolean isBlocks() { return target.toLowerCase(Locale.US).startsWith("blocks/"); }

        /** Rough size badge used by the review card. */
        public String badge() {
            int lines = content.isEmpty() ? 0 : content.split("\\r?\\n", -1).length;
            return lines + " baris";
        }
    }

    private AgentActionExecutor() { }

    /** First action in the response, kept for backwards compatibility. */
    public static Action parse(String response) {
        List<Action> all = parseAll(response);
        return all.isEmpty() ? null : all.get(0);
    }

    /** Every TARGET + fenced code block pair in the response, in order. */
    public static List<Action> parseAll(String response) {
        List<Action> out = new ArrayList<>();
        if (response == null || response.isEmpty()) return out;

        List<Integer> targetStart = new ArrayList<>();
        List<Integer> targetEnd = new ArrayList<>();
        List<String> targetName = new ArrayList<>();
        Matcher t = TARGET.matcher(response);
        while (t.find()) {
            targetStart.add(t.start());
            targetEnd.add(t.end());
            targetName.add(t.group(1));
        }
        if (targetStart.isEmpty()) return out;

        List<Integer> fenceStart = new ArrayList<>();
        List<String> fenceLang = new ArrayList<>();
        List<String> fenceCode = new ArrayList<>();
        Matcher f = FENCE.matcher(response);
        while (f.find()) {
            fenceStart.add(f.start());
            fenceLang.add(f.group(1) == null ? "text" : f.group(1).trim().toLowerCase(Locale.US));
            fenceCode.add(f.group(2) == null ? "" : f.group(2).trim());
        }
        if (fenceStart.isEmpty()) return out;

        int fi = 0;
        for (int i = 0; i < targetStart.size(); i++) {
            while (fi < fenceStart.size() && fenceStart.get(fi) < targetEnd.get(i)) fi++;
            if (fi >= fenceStart.size()) break;
            // A fence that already belongs to the next TARGET is not ours.
            if (i + 1 < targetStart.size() && fenceStart.get(fi) > targetStart.get(i + 1)) continue;

            String path = sanitizeTarget(targetName.get(i));
            String code = fenceCode.get(fi);
            if (!path.isEmpty() && !code.isEmpty()) {
                out.add(new Action(path, fenceLang.get(fi), code));
            }
            fi++;
        }
        return out;
    }

    /** Strips markdown links, backticks, quotes and stray decoration from a TARGET line. */
    private static String sanitizeTarget(String raw) {
        if (raw == null) return "";
        String value = raw.trim();
        Matcher link = MD_LINK.matcher(value);
        if (link.matches()) value = link.group(1).trim();
        value = value.replace("`", "").replace("\"", "").replace("'", "").trim();
        if (value.startsWith("**") && value.endsWith("**") && value.length() > 4) {
            value = value.substring(2, value.length() - 2).trim();
        }
        int space = value.indexOf(' ');
        if (space > 0 && value.indexOf('/') > space) value = value.substring(0, space);
        return value.replace('\\', '/').trim();
    }

    /** Raw XML layouts without a res/ prefix are handed to the Direct XML editor instead. */
    public static boolean needsEditorHandoff(Action action) {
        if (action == null || !action.isXml()) return false;
        String lower = action.target.toLowerCase(Locale.US);
        return !lower.startsWith("res/") && !lower.startsWith("assets/");
    }

    /** Applies one action, journalling the previous version for a single step undo. */
    public static String apply(Context context, String scId, Action action) {
        if (action == null) return "The response did not contain a safe TARGET and fenced code block.";
        if (needsEditorHandoff(action)) return "XML_HANDOFF";
        if (action.isBlocks()) return AgentBlockExecutor.apply(scId, action.target, action.content);

        List<AgentMemoryEngine.Entry> batch = new ArrayList<>();
        String result = writeFile(scId, action, batch);
        if (result.startsWith("APPLIED") && !batch.isEmpty()) {
            AgentMemoryEngine.Entry e = batch.get(0);
            AgentUndoJournal.saveFile(scId, e.path, e.existed, e.previous);
        }
        return result;
    }

    /**
     * Applies every action in order and stores all captured snapshots as ONE rollback batch, so
     * a later "balik ke yang tadi" restores the complete feature, not just the last file.
     *
     * @return a short human readable report.
     */
    public static String applyAll(Context context, String scId, List<Action> actions, AgentMemoryEngine memory, String label) {
        if (actions == null || actions.isEmpty()) return "Tidak ada file untuk diterapkan.";

        List<AgentMemoryEngine.Entry> batch = new ArrayList<>();
        List<String> applied = new ArrayList<>();
        List<String> failed = new ArrayList<>();

        for (Action action : actions) {
            String result;
            if (action.isBlocks()) {
                result = AgentBlockExecutor.apply(scId, action.target, action.content);
                if (result != null && result.startsWith("APPLIED")) applied.add(action.target);
                else failed.add(action.target + " (" + result + ")");
                continue;
            }
            result = writeFile(scId, action, batch);
            if (result.startsWith("APPLIED")) applied.add(action.target);
            else failed.add(action.target + " (" + result + ")");
        }

        if (memory != null && !batch.isEmpty()) {
            memory.pushBatch(scId, batch, label);
        } else if (!batch.isEmpty()) {
            AgentMemoryEngine.Entry e = batch.get(0);
            AgentUndoJournal.saveFile(scId, e.path, e.existed, e.previous);
        }

        StringBuilder sb = new StringBuilder();
        if (!applied.isEmpty()) {
            sb.append(applied.size()).append(" file diterapkan:\n");
            for (String a : applied) sb.append("  \u2713 ").append(a).append('\n');
        }
        if (!failed.isEmpty()) {
            sb.append(failed.size()).append(" file gagal:\n");
            for (String a : failed) sb.append("  \u2717 ").append(a).append('\n');
        }
        return sb.toString().trim();
    }

    /** Resolves the safe destination inside the project and writes the file. */
    private static String writeFile(String scId, Action action, List<AgentMemoryEngine.Entry> batch) {
        String pathInsideRoot = action.target;
        if (pathInsideRoot.startsWith("/") || pathInsideRoot.contains("..") || pathInsideRoot.contains(":")) {
            return "Rejected unsafe target path: " + pathInsideRoot;
        }

        String lower = pathInsideRoot.toLowerCase(Locale.US);
        File root;
        FilePathUtil util = new FilePathUtil();

        if (lower.startsWith("res/")) {
            root = new File(util.getPathResource(scId));
            pathInsideRoot = pathInsideRoot.substring(4);
        } else if (lower.startsWith("assets/")) {
            root = new File(util.getPathAssets(scId));
            pathInsideRoot = pathInsideRoot.substring(7);
        } else if (lower.endsWith(".java") || lower.endsWith(".kt") || lower.endsWith(".kts") || lower.endsWith(".gradle")) {
            root = new File(util.getPathJava(scId));
        } else if (lower.endsWith(".cpp") || lower.endsWith(".c") || lower.endsWith(".h") || lower.endsWith(".hpp")) {
            root = new File(util.getPathNativelibs(scId));
        } else {
            root = new File(util.getPathJava(scId));
        }

        File file = new File(root, pathInsideRoot);
        try {
            String rootPath = root.getCanonicalPath();
            String filePath = file.getCanonicalPath();
            if (!filePath.equals(rootPath) && !filePath.startsWith(rootPath + File.separator)) {
                return "Rejected target outside project source root.";
            }

            boolean existed = file.isFile();
            String previous = existed ? new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8) : "";
            if (batch != null) {
                batch.add(AgentMemoryEngine.entry(filePath, action.target, existed, previous));
            }

            File parent = file.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) return "Could not create target directory.";

            Files.write(file.toPath(), action.content.getBytes(StandardCharsets.UTF_8));
            return "APPLIED:" + filePath;
        } catch (Exception e) {
            return "Write failed: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
    }

    public static String revertLast(Context context, String scId) {
        if (scId == null || scId.trim().isEmpty()) return "No project is selected.";
        return AgentUndoJournal.revert(scId);
    }
}
