package pro.sketchware.editor;

/**
 * Optimizes prompts to stay within token limits while maintaining critical context.
 * Aims to save ~80% of tokens by pruning non-essential metadata and comments.
 */
public final class AgentPromptOptimizer {
    private AgentPromptOptimizer() { }

    /**
     * Builds a compact prompt by intelligently pruning the project context.
     */
    public static String build(String userPrompt, String projectContext) {
        // Limit context to ~10,000 chars for extreme efficiency
        String compactContext = compact(projectContext, 10000);
        
        boolean isMutation = userPrompt != null && userPrompt.matches("(?is).*\\b(create|make|build|generate|fix|repair|change|modify|update|buat|bikin|perbaiki|ubah|layout|xml|java|kotlin|c\\+\\+|block|code|res|library|hubungkan|download)\\b.*");
        
        StringBuilder sb = new StringBuilder();
        sb.append("You are Dayven AI, a professional coding mentor for Sketchware-DayDream.\n");
        sb.append("Role: Expert Android Developer. Tone: Formal, precise, and helpful.\n");
        sb.append("Rules:\n");
        sb.append("1. Analyze the project context provided below.\n");
        sb.append("2. Provide direct solutions. If a code change is needed, use the PROPOSAL format.\n");
        sb.append("3. Format Proposals exactly as:\n");
        sb.append("   TARGET: path/to/file.ext\n");
        sb.append("   ```language\n");
        sb.append("   [FULL REPLACEMENT CODE]\n");
        sb.append("   ```\n");
        if (isMutation) {
            sb.append("4. IMPORTANT: The user wants to modify the project. Be extremely accurate with file paths and code syntax.\n");
        }
        sb.append("5. Be extremely concise to save output tokens.\n\n");
        
        sb.append("PROJECT CONTEXT:\n");
        sb.append(compactContext);
        sb.append("\n\nUSER INSTRUCTION:\n");
        sb.append(userPrompt != null ? userPrompt.trim() : "");
        
        return sb.toString();
    }

    /**
     * Aggressively prunes source code context.
     * - Removes Javadoc and block comments.
     * - Removes single line comments.
     * - Collapses multiple empty lines.
     * - Prunes package and import statements if too long.
     */
    private static String compact(String source, int limit) {
        if (source == null || source.isEmpty()) return "No context available.";

        // 1. Remove block comments (/* ... */)
        String pruned = source.replaceAll("/\\*.*?\\*/", "");
        
        // 2. Remove single line comments (// ...)
        pruned = pruned.replaceAll("//.*", "");
        
        // 3. Remove XML comments (<!-- ... -->)
        pruned = pruned.replaceAll("(?s)<!--.*?-->", "");

        StringBuilder out = new StringBuilder();
        String[] lines = pruned.split("\\r?\\n");
        int currentLength = 0;

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;
            
            // Skip common boilerplate if we are getting close to the limit
            if (currentLength > limit * 0.7) {
                if (trimmed.startsWith("import ") || trimmed.startsWith("package ")) continue;
            }

            if (currentLength + trimmed.length() + 1 > limit) {
                out.append("\n[... Context truncated for efficiency ...]");
                break;
            }
            
            out.append(trimmed).append("\n");
            currentLength += trimmed.length() + 1;
        }

        return out.toString().trim();
    }
}
