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

/** Builds a compact, secret-aware snapshot so routine requests use a small fraction of context tokens. */
public final class AgentProjectContext {
    private static final int MAX_TOTAL = 12000;
    private static final int MAX_FILE = 3500;
    private static final int MAX_FILES = 8;

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
        out.append("FILES:\n");
        for (File root : roots) appendTree(root, root, out, 0);
        File compileLog = new File(FilePathUtil.getLastCompileLogPath(scId));
        appendFileTail(compileLog, "compile_log", out, 4500);
        out.append("CONTENT:\n");
        int[] count = {0};
        if (selectedFile != null && !selectedFile.isEmpty()) {
            for (File root : roots) appendSelected(root, root, selectedFile, out, count);
        }
        for (File root : roots) appendContent(root, root, out, count);
        if (out.length() == 0) out.append("No readable project context.\n");
        return out.length() > MAX_TOTAL ? out.substring(0, MAX_TOTAL) + "\n[compact context truncated]" : out.toString();
    }

    private static void appendTree(File root, File current, StringBuilder out, int depth) {
        if (out.length() >= MAX_TOTAL || depth > 5 || current == null || !current.exists()) return;
        if (current.isFile()) { if (isRelevant(current)) out.append("- ").append(relative(root, current)).append('\n'); return; }
        File[] children = current.listFiles(); if (children == null) return;
        java.util.Arrays.sort(children, Comparator.comparing(File::getName));
        for (File child : children) { if (!child.getName().startsWith(".")) appendTree(root, child, out, depth + 1); }
    }

    private static void appendSelected(File root, File current, String selected, StringBuilder out, int[] count) {
        if (current == null || !current.exists() || count[0] >= MAX_FILES || out.length() >= MAX_TOTAL) return;
        if (current.isDirectory()) { File[] children = current.listFiles(); if (children == null) return; for (File child : children) appendSelected(root, child, selected, out, count); return; }
        if (current.getName().equals(selected) || relative(root, current).equals(selected)) { appendFile(current, relative(root, current), out); count[0]++; }
    }

    private static void appendContent(File root, File current, StringBuilder out, int[] count) {
        if (out.length() >= MAX_TOTAL || count[0] >= MAX_FILES || current == null || !current.exists()) return;
        if (current.isDirectory()) { File[] children = current.listFiles(); if (children == null) return; java.util.Arrays.sort(children, Comparator.comparing(File::getName)); for (File child : children) appendContent(root, child, out, count); return; }
        if (!isRelevant(current)) return;
        appendFile(current, relative(root, current), out); count[0]++;
    }

    private static void appendFileTail(File file, String name, StringBuilder out, int max) {
        if (file == null || !file.isFile()) return;
        try { String value = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8); if (value.length() > max) value = value.substring(value.length() - max); out.append("\n--- ").append(name).append(" ---\n").append(value).append('\n'); } catch (IOException ignored) { }
    }

    private static void appendFile(File file, String name, StringBuilder out) {
        if (file == null || !file.isFile() || out.length() >= MAX_TOTAL || !isRelevant(file)) return;
        try {
            String value = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            if (value.length() > MAX_FILE) value = value.substring(0, MAX_FILE) + "\n[truncated]";
            out.append("\n--- ").append(name).append(" ---\n").append(value).append('\n');
        } catch (IOException ignored) { }
    }

    private static boolean isRelevant(File file) {
        String name = file.getName().toLowerCase();
        if (name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".webp") || name.endsWith(".jar") || name.endsWith(".aar") || name.endsWith(".dex")) return false;
        return name.endsWith(".java") || name.endsWith(".kt") || name.endsWith(".kts") || name.endsWith(".cpp") || name.endsWith(".c") || name.endsWith(".h") || name.endsWith(".xml") || name.endsWith(".json") || name.endsWith(".gradle") || name.equals("compile_log") || name.endsWith(".txt");
    }

    private static String relative(File root, File child) { try { return root.toPath().relativize(child.toPath()).toString(); } catch (Exception ignored) { return child.getName(); } }
}
