package com.besome.sketch.tools;

import android.content.Context;
import android.content.Intent;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.widget.TextView;

import java.io.File;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import mod.hey.studios.util.CompileLogHelper;
import mod.hey.studios.code.SrcCodeEditor;
import pro.sketchware.utility.FileUtil;

/** Turns compiler locations into one-tap editor links, including Eclipse/JDT and XML formats. */
public final class CompileErrorNavigator {
    private static final Pattern ABSOLUTE = Pattern.compile("(?m)(?<![\\w])((?:[A-Za-z]:)?/[^\\n\\r]*?\\.(?:java|kt|xml)):(\\d+)(?::(\\d+))?");
    private static final Pattern ECLIPSE = Pattern.compile("(?m)(?:^|\\n)\\s*(?:\\d+\\.\\s*)?(?:ERROR|WARNING)\\s+in\\s+([^\\n\\r]*?\\.(?:java|kt|xml))(?:\\s*\\(at\\s+line\\s+(\\d+)(?:,\\s*column\\s+(\\d+))?\\))?");
    private static final Pattern RELATIVE = Pattern.compile("(?m)(?<![\\w])([A-Za-z0-9_./\\\\-]+\\.(?:java|kt|xml)):(\\d+)(?::(\\d+))?");

    private CompileErrorNavigator() { }

    public static void bind(Context context, TextView target, String log, String scId) {
        CharSequence colored = CompileLogHelper.getColoredLogs(context, log == null ? "" : log);
        SpannableStringBuilder value = new SpannableStringBuilder(colored);
        Set<String> linked = new HashSet<>();
        int links = bindPattern(context, value, ABSOLUTE, scId, linked, false);
        links += bindPattern(context, value, ECLIPSE, scId, linked, true);
        links += bindPattern(context, value, RELATIVE, scId, linked, false);
        target.setText(value);
        target.setMovementMethod(android.text.method.LinkMovementMethod.getInstance());
        target.setLinksClickable(true);
        target.setTextIsSelectable(false);
        target.setContentDescription(links == 0 ? "Compile log" : links + " clickable source locations");
    }

    private static int bindPattern(Context context, SpannableStringBuilder value, Pattern pattern, String scId, Set<String> linked, boolean inferLocation) {
        Matcher matcher = pattern.matcher(value.toString());
        int links = 0;
        while (matcher.find()) {
            String rawPath = matcher.group(1);
            String path = resolvePath(rawPath, scId);
            int line = parseInt(matcher.group(2), 0);
            int column = parseInt(matcher.group(3), 0);
            if (line <= 0 && inferLocation) line = inferLine(value.toString(), matcher.end(), path);
            if (line <= 0) line = 1;
            if (column <= 0) column = inferColumn(value.toString(), matcher.end());
            String key = matcher.start() + ":" + path + ":" + line + ":" + column;
            if (!linked.add(key) || value.getSpans(matcher.start(1), matcher.end(1), ClickableSpan.class).length > 0) continue;
            final String targetPath = path;
            final int targetLine = line;
            final int targetColumn = column;
            value.setSpan(new ClickableSpan() {
                @Override public void onClick(View widget) {
                    Intent intent = new Intent(context, SrcCodeEditor.class);
                    intent.putExtra("sc_id", scId);
                    intent.putExtra("content", targetPath);
                    intent.putExtra("title", new File(targetPath).getName());
                    intent.putExtra("line", targetLine);
                    intent.putExtra("column", targetColumn);
                    intent.putExtra("from_compile_error", true);
                    context.startActivity(intent);
                }
            }, matcher.start(1), matcher.end(1), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            value.setSpan(new ForegroundColorSpan(0xFF1976D2), matcher.start(1), matcher.end(1), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            links++;
        }
        return links;
    }

    private static String resolvePath(String rawPath, String scId) {
        String path = rawPath == null ? "" : rawPath.trim().replace('\\', '/');
        int metadata = path.indexOf(" (at line ");
        if (metadata >= 0) path = path.substring(0, metadata).trim();
        File direct = new File(path);
        if (direct.exists()) return direct.getAbsolutePath();
        if (scId != null && !scId.isEmpty()) {
            String root = FileUtil.getExternalStorageDir() + "/.sketchware/mysc/" + scId + "/";
            String relative = path.startsWith("/") ? path.substring(1) : path;
            File candidate = new File(root, relative);
            if (candidate.exists()) return candidate.getAbsolutePath();
            int source = path.indexOf("/src/main/");
            if (source >= 0) {
                candidate = new File(root + "app/src/main/", path.substring(source + "/src/main/".length()));
                if (candidate.exists()) return candidate.getAbsolutePath();
            }
            if (path.startsWith("app/src/main/")) {
                candidate = new File(root, path);
                if (candidate.exists()) return candidate.getAbsolutePath();
            }
        }
        return path;
    }

    private static int inferLine(String log, int from, String path) {
        File file = new File(path);
        if (!file.exists()) return 1;
        String source = FileUtil.readFile(path);
        String[] sourceLines = source == null ? new String[0] : source.split("\\r?\\n", -1);
        String[] tail = log.substring(Math.min(from, log.length())).split("\\r?\\n");
        for (String candidate : tail) {
            String code = candidate.trim();
            if (code.isEmpty() || code.startsWith("^") || code.startsWith("----------") || code.matches("\\d+\\.\\s+.*") || code.matches(".*\\(at line \\d+.*\\).*")) continue;
            for (int i = 0; i < sourceLines.length; i++) if (!code.isEmpty() && sourceLines[i].trim().equals(code)) return i + 1;
            if (tail.length > 8) break;
        }
        return 1;
    }

    private static int inferColumn(String log, int from) {
        String[] tail = log.substring(Math.min(from, log.length())).split("\\r?\\n");
        for (String line : tail) {
            if (line.trim().matches("^\\^+$")) return line.indexOf('^') + 1;
        }
        return 1;
    }

    private static int parseInt(String value, int fallback) { try { return Integer.parseInt(value); } catch (Exception ignored) { return fallback; } }
}
