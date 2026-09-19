package pro.sketchware.editor;

import android.content.Context;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import pro.sketchware.utility.FilePathUtil;
import pro.sketchware.utility.FileUtil;

/** Builds a bounded, secret-aware snapshot for Gemini instead of sending an empty prompt. */
public final class AgentProjectContext {
    private static final int MAX_TOTAL = 36000;
    private static final int MAX_FILE = 9000;

    private AgentProjectContext() { }

    public static String build(Context context, String scId, String selectedFile) {
        if (scId == null || scId.trim().isEmpty()) return "No project is currently selected.";
        StringBuilder out = new StringBuilder();
        out.append("PROJECT_ID: ").append(scId).append('\n');
        if (selectedFile != null && !selectedFile.isEmpty()) out.append("SELECTED_FILE: ").append(selectedFile).append('\n');
        FilePathUtil paths = new FilePathUtil();
        List<File> roots = new ArrayList<>();
        roots.add(new File(paths.getPathJava(scId)));
        roots.add(new File(paths.getPathResource(scId)));
        roots.add(new File(paths.getPathNativelibs(scId)));
        File compileLog = new File(FilePathUtil.getLastCompileLogPath(scId));
        out.append("FILES:\n");
        for (File root : roots) appendTree(root, root, out, 0);
        appendFile(compileLog, "compile_log", out);
        out.append("CONTENT:\n");
        int before = out.length();
        for (File root : roots) appendContent(root, root, out);
        if (out.length() == before) out.append("No readable project source found yet.\n");
        return out.length() > MAX_TOTAL ? out.substring(0, MAX_TOTAL) + "\n[context truncated]" : out.toString();
    }

    private static void appendTree(File root, File current, StringBuilder out, int depth) {
        if (out.length() >= MAX_TOTAL || depth > 5 || current == null || !current.exists()) return;
        if (current.isFile()) {
            if (isRelevant(current)) out.append("- ").append(relative(root, current)).append(" (file)\n");
            return;
        }
        File[] children = current.listFiles();
        if (children == null) return;
        java.util.Arrays.sort(children, Comparator.comparing(File::getName));
        for (File child : children) {
            if (child.getName().startsWith(".")) continue;
            appendTree(root, child, out, depth + 1);
        }
    }

    private static void appendContent(File root, File current, StringBuilder out) {
        if (out.length() >= MAX_TOTAL || current == null || !current.exists()) return;
        if (current.isDirectory()) {
            File[] children = current.listFiles();
            if (children == null) return;
            java.util.Arrays.sort(children, Comparator.comparing(File::getName));
            for (File child : children) appendContent(root, child, out);
            return;
        }
        if (!isRelevant(current)) return;
        appendFile(current, relative(root, current), out);
    }

    private static void appendFile(File file, String name, StringBuilder out) {
        if (file == null || !file.isFile() || out.length() >= MAX_TOTAL || !isRelevant(file)) return;
        try {
            byte[] bytes = Files.readAllBytes(file.toPath());
            String value = new String(bytes, StandardCharsets.UTF_8);
            if (value.length() > MAX_FILE) value = value.substring(0, MAX_FILE) + "\n[truncated]";
            out.append("\n--- ").append(name).append(" ---\n").append(value).append('\n');
        } catch (IOException ignored) { }
    }

    private static boolean isRelevant(File file) {
        String name = file.getName().toLowerCase();
        if (name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".webp") || name.endsWith(".jar") || name.endsWith(".aar") || name.endsWith(".dex")) return false;
        return name.endsWith(".java") || name.endsWith(".kt") || name.endsWith(".kts") || name.endsWith(".cpp") || name.endsWith(".c") || name.endsWith(".h") || name.endsWith(".xml") || name.endsWith(".json") || name.endsWith(".gradle") || name.equals("compile_log") || name.endsWith(".txt");
    }

    private static String relative(File root, File child) {
        try { return root.toPath().relativize(child.toPath()).toString(); }
        catch (Exception ignored) { return child.getName(); }
    }
}
