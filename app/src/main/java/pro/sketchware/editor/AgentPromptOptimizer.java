package pro.sketchware.editor;

/** Keeps requests compact and output bounded so routine Agent work uses far fewer tokens. */
public final class AgentPromptOptimizer {
    private AgentPromptOptimizer() { }

    public static String build(String userPrompt, String projectContext) {
        String compact = compact(projectContext, 12000);
        boolean mutation = userPrompt != null && userPrompt.matches("(?is).*\\b(create|make|build|generate|fix|repair|change|modify|update|buat|bikin|perbaiki|ubah|layout|xml|java|kotlin|c\\+\\+|block|code)\\b.*");
        int output = mutation ? 4096 : 1536;
        return "You are Dayven AI embedded in Sketchware-DayGreen. Use only the compact project context below. "
                + "Be concise, root-cause focused, and do not claim changes were applied. "
                + "Use short answers for questions. If the user requests a mutation, finish with exactly one proposal: "
                + "TARGET: relative/path/to/file, then one fenced code block with complete replacement content. "
                + "Use XML, java, kotlin, cpp, or blocks/<JavaFile>/<eventKey>. Do not invent block formats. "
                + "Keep the response under " + output + " output tokens.\n\nPROJECT CONTEXT:\n"
                + compact + "\n\nUSER REQUEST:\n" + (userPrompt == null ? "" : userPrompt.trim());
    }

    private static String compact(String source, int limit) {
        if (source == null || source.isEmpty()) return "No readable project context.";
        StringBuilder out = new StringBuilder(Math.min(limit, source.length()));
        boolean blockComment = false;
        for (String raw : source.split("\\r?\\n")) {
            String line = raw.trim();
            if (line.isEmpty()) continue;
            if (blockComment) {
                if (line.contains("*/")) blockComment = false;
                continue;
            }
            if (line.startsWith("/*")) { blockComment = !line.contains("*/"); continue; }
            if (line.startsWith("//") || line.startsWith("<!--") || line.startsWith("# ")) continue;
            if (out.length() + line.length() + 1 > limit) break;
            out.append(line).append('\n');
        }
        return out.length() == 0 ? "No readable project context." : out.toString();
    }
}
