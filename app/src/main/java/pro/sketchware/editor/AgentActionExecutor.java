package pro.sketchware.editor;

import android.content.Context;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import pro.sketchware.utility.FilePathUtil;

/** Parses Gemini's explicit action protocol and writes only after the user taps Accept. */
public final class AgentActionExecutor {
    private static final Pattern TARGET = Pattern.compile("(?im)^\\s*(?:TARGET|FILE)\\s*:\\s*([^\\r\\n]+)");
    private static final Pattern FENCE = Pattern.compile("(?is)```([a-zA-Z0-9_+#.-]*)\\s*\\r?\\n(.*?)```");

    public static final class Action {
        public final String target;
        public final String language;
        public final String content;
        private Action(String target, String language, String content) { this.target = target; this.language = language; this.content = content; }
        public boolean isXml() { return target.toLowerCase(Locale.US).endsWith(".xml") || "xml".equals(language); }
        public boolean isBlocks() { return target.toLowerCase(Locale.US).startsWith("blocks/"); }
    }

    private AgentActionExecutor() { }

    public static Action parse(String response) {
        if (response == null) return null;
        Matcher target = TARGET.matcher(response), fence = FENCE.matcher(response);
        if (!target.find() || !fence.find()) return null;
        String path = target.group(1).trim().replace('\\', '/');
        String language = fence.group(1) == null ? "text" : fence.group(1).trim().toLowerCase(Locale.US);
        String code = fence.group(2) == null ? "" : fence.group(2).trim();
        if (path.isEmpty() || code.isEmpty()) return null;
        return new Action(path, language, code);
    }

    public static String apply(Context context, String scId, Action action) {
        if (action == null) return "The response did not contain a safe TARGET and fenced code block.";
        if (action.isXml()) return "XML_HANDOFF";
        if (action.isBlocks()) return AgentBlockExecutor.apply(scId, action.target, action.content);
        String target = action.target;
        if (target.startsWith("/") || target.contains("..") || target.contains(":") || target.contains("\\")) return "Rejected unsafe target path: " + target;
        String lower = target.toLowerCase(Locale.US);
        File root;
        if (lower.endsWith(".java") || lower.endsWith(".kt") || lower.endsWith(".kts") || lower.endsWith(".gradle")) root = new File(new FilePathUtil().getPathJava(scId));
        else if (lower.endsWith(".cpp") || lower.endsWith(".c") || lower.endsWith(".h") || lower.endsWith(".hpp")) root = new File(new FilePathUtil().getPathNativelibs(scId));
        else return "Rejected unsupported target type: " + target;
        File file = new File(root, target);
        try {
            String rootPath = root.getCanonicalPath(), filePath = file.getCanonicalPath();
            if (!filePath.equals(rootPath) && !filePath.startsWith(rootPath + File.separator)) return "Rejected target outside project source root.";
            File parent = file.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) return "Could not create target directory.";
            Files.write(file.toPath(), action.content.getBytes(StandardCharsets.UTF_8));
            return "APPLIED:" + filePath;
        } catch (Exception e) {
            return "Write failed: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
    }
}
