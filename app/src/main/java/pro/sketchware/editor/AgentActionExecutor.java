package pro.sketchware.editor;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.Toast;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import pro.sketchware.utility.FilePathUtil;
import pro.sketchware.utility.XmlSyncHelper;

/**
 * Parses Dayven AI's explicit action protocol and writes only after the user taps Accept.
 *
 * <p>v3.6 Autonomous Shell & Execution Engine:
 * - Real-time command shell execution dispatcher (`BUILD_TRIGGER`, `SHELL_CMD`, `AUTO_BUILD`).
 * - Direct build/run trigger into DesignActivity (R.id.btn_run).
 * - Automatic XML sync to Sketchware Visual Editor (ViewBean) via XmlSyncHelper.
 * - Structured ExecutionStep parser for Claude-style real-time accordions.</p>
 */
public final class AgentActionExecutor {
    private static final Pattern TARGET = Pattern.compile("(?im)^\\s*(?:TARGET|FILE)\\s*:\\s*([^\\r\\n]+)");
    private static final Pattern FENCE = Pattern.compile("(?is)```([a-zA-Z0-9_+#.-]*)\\s*\\r?\\n(.*?)```");
    private static final Pattern MD_LINK = Pattern.compile("^\\[([^\\]]+)\\]\\([^)]*\\)$");

    public static final class Action {
        public final String target;
        public final String language;
        public final String content;

        public Action(String target, String language, String content) {
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

    /** Structured model for Claude-style collapsible command steps (`>_ n langkah`). */
    public static final class ExecutionStep {
        public final String title;
        public final String command;
        public final String status;
        public final String log;

        public ExecutionStep(String title, String command, String status, String log) {
            this.title = title == null ? "Proses langkah" : title.trim();
            this.command = command == null ? "" : command.trim();
            this.status = status == null ? "OK" : status.trim();
            this.log = log == null ? "" : log.trim();
        }
    }

    private AgentActionExecutor() { }

    /** First action in the response. */
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

    /** Parses Claude-style execution steps from response. */
    public static List<ExecutionStep> parseSteps(String response) {
        List<ExecutionStep> steps = new ArrayList<>();
        if (response == null || response.isEmpty()) return steps;

        Pattern stepPattern = Pattern.compile("(?im)^\\s*(?:EXEC_STEP|LANGKAH|STEP)\\s*:\\s*([^\\r\\n]+)");
        Matcher m = stepPattern.matcher(response);

        List<Integer> starts = new ArrayList<>();
        List<String> titles = new ArrayList<>();
        while (m.find()) {
            starts.add(m.start());
            titles.add(m.group(1));
        }

        if (starts.isEmpty()) return steps;

        for (int i = 0; i < starts.size(); i++) {
            int blockStart = starts.get(i);
            int blockEnd = (i + 1 < starts.size()) ? starts.get(i + 1) : response.length();
            String block = response.substring(blockStart, blockEnd);

            String title = titles.get(i);
            String cmd = extractValue(block, "CMD");
            if (cmd.isEmpty()) cmd = extractValue(block, "COMMAND");
            String status = extractValue(block, "STATUS");
            if (status.isEmpty()) status = "DONE";
            String log = extractValue(block, "LOG");

            steps.add(new ExecutionStep(title, cmd, status, log));
        }

        return steps;
    }

    private static String extractValue(String block, String key) {
        Pattern p = Pattern.compile("(?im)^\\s*" + key + "\\s*:\\s*([^\\r\\n]+)");
        Matcher m = p.matcher(block);
        if (m.find()) return m.group(1).trim();
        return "";
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

    public static boolean needsEditorHandoff(Action action) {
        if (action == null || !action.isXml()) return false;
        String lower = action.target.toLowerCase(Locale.US);
        return !lower.startsWith("res/") && !lower.startsWith("assets/");
    }

    /**
     * Checks if response requests automated build execution.
     */
    public static boolean containsBuildTrigger(String response) {
        if (response == null) return false;
        return response.contains("[TRIGGER_RUN_BUILD]") || response.contains("TRIGGER: RUN_BUILD") || response.contains("BUILD_ACTION: EXECUTE");
    }

    /**
     * Triggers the real Run / Build button in DesignActivity.
     */
    public static boolean triggerDesignActivityBuild(Activity activity) {
        if (activity == null) return false;
        try {
            int btnRunId = activity.getResources().getIdentifier("btn_run", "id", activity.getPackageName());
            if (btnRunId != 0) {
                View btnRun = activity.findViewById(btnRunId);
                if (btnRun != null) {
                    activity.runOnUiThread(btnRun::performClick);
                    return true;
                }
            }
        } catch (Throwable ignored) { }
        return false;
    }

    /**
     * Applies every action in order, automatically syncing layout XML to Sketchware
     * visual editor data (ViewBeans) via XmlSyncHelper, and stores snapshots as ONE rollback batch.
     */
    public static String applyAll(Context context, String scId, List<Action> actions, AgentMemoryEngine memory, String label) {
        if (actions == null || actions.isEmpty()) return "Tidak ada file untuk diterapkan.";

        List<AgentMemoryEngine.Entry> batch = new ArrayList<>();
        List<String> applied = new ArrayList<>();
        List<String> failed = new ArrayList<>();

        for (Action action : actions) {
            if (action.isBlocks()) {
                String result = AgentBlockExecutor.apply(scId, action.target, action.content);
                if (result != null && result.startsWith("APPLIED")) applied.add(action.target);
                else failed.add(action.target + " (" + result + ")");
                continue;
            }

            // Sync layout directly to Visual Editor if it's a layout XML
            if (action.isXml() && context != null && scId != null) {
                try {
                    String fileName = action.target;
                    if (fileName.contains("/")) fileName = fileName.substring(fileName.lastIndexOf('/') + 1);
                    XmlSyncHelper.syncXml(context, scId, fileName, action.content);
                } catch (Throwable ignored) { }
            }

            String result = writeFile(scId, action, batch);
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
            sb.append(applied.size()).append(" file berhasil diterapkan & disinkronisasi ke project:\n");
            for (String a : applied) sb.append("  \u2713 ").append(a).append('\n');
        }
        if (!failed.isEmpty()) {
            sb.append(failed.size()).append(" file gagal:\n");
            for (String a : failed) sb.append("  \u2717 ").append(a).append('\n');
        }
        return sb.toString().trim();
    }

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
}
