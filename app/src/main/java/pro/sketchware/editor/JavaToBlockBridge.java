package pro.sketchware.editor;

import com.besome.sketch.beans.BlockBean;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.Statement;

import java.util.ArrayList;
import java.util.Collections;

import a.a.a.kq;

/**
 * Imports Java event bodies as guaranteed-renderable Sketchware command blocks.
 *
 * Full generated Activity source is not an event body. The reference editor treats
 * that source as a class override, so this bridge detects it and refuses to
 * replace event blocks with the class text. Event imports use an ID range above
 * the event header blocks to avoid overwriting getArg blocks during reload.
 */
public final class JavaToBlockBridge {
    private static final String RAW_SPEC = "add source directly %s.inputOnly";

    private JavaToBlockBridge() { }

    public static Analysis analyze(String source) {
        try {
            ParsedBody parsed = parseSource(source);
            ArrayList<String> statements = new ArrayList<>();
            for (Statement statement : parsed.body.getStatements()) statements.add(statement.toString());
            return new Analysis(true, 0, 0, "", statements, parsed.fullSource);
        } catch (Throwable error) {
            int line = 1;
            int column = 1;
            if (error instanceof com.github.javaparser.ParseProblemException parseError
                    && !parseError.getProblems().isEmpty()
                    && parseError.getProblems().get(0).getLocation().isPresent()) {
                var token = parseError.getProblems().get(0).getLocation().get().getBegin();
                var range = token.getRange().orElse(null);
                if (range != null) {
                    line = range.begin.line;
                    column = range.begin.column;
                }
            }
            return new Analysis(false, line, column, clean(error.getMessage()),
                    Collections.emptyList(), looksLikeClassSource(source));
        }
    }

    public static ImportResult toBlocks(String source) {
        return toBlocks(source, 1);
    }

    /** Convert using an ID range that cannot collide with the event header blocks. */
    public static ImportResult toBlocks(String source, int firstId) {
        Analysis analysis = analyze(source);
        if (!analysis.valid || analysis.fullSource) return new ImportResult(analysis, new ArrayList<>());

        ArrayList<BlockBean> blocks = new ArrayList<>();
        try {
            BlockStmt body = parseSource(source).body;
            int id = Math.max(1, firstId);
            for (Statement statement : body.getStatements()) {
                blocks.add(rawBean(statement.toString(), id++));
            }
            if (blocks.isEmpty() && source != null && !source.trim().isEmpty()) {
                blocks.add(rawBean(source.trim(), id));
            }
            for (int i = 0; i + 1 < blocks.size(); i++) {
                blocks.get(i).nextBlock = Integer.parseInt(blocks.get(i + 1).id);
            }
        } catch (Throwable error) {
            return new ImportResult(new Analysis(false, 1, 1,
                    "Unable to convert this Java body: " + clean(error.getMessage()),
                    Collections.emptyList(), false), blocks);
        }
        return new ImportResult(analysis, blocks);
    }

    private static ParsedBody parseSource(String source) {
        String text = source == null ? "" : source.trim();
        if (looksLikeClassSource(text)) {
            String methodBody = extractMethodBody(text, "initializeLogic");
            if (methodBody == null) methodBody = extractMethodBody(text, "onCreate");
            if (methodBody == null) {
                throw new IllegalArgumentException(
                        "Full Java class detected. Use Edit generated Java class instead of Java to Blocks.");
            }
            return new ParsedBody(parseBody(methodBody), true);
        }
        return new ParsedBody(parseBody(text), false);
    }

    private static boolean looksLikeClassSource(String source) {
        if (source == null) return false;
        String text = source.trim();
        return text.startsWith("package ") || text.startsWith("import ")
                || text.contains(" class ") || text.startsWith("class ")
                || text.contains("public class ");
    }

    private static String extractMethodBody(String source, String methodName) {
        int method = source.indexOf(methodName + "(");
        if (method < 0) return null;
        int open = source.indexOf('{', method);
        if (open < 0) return null;
        int depth = 0;
        boolean quoted = false;
        boolean escaped = false;
        for (int i = open; i < source.length(); i++) {
            char c = source.charAt(i);
            if (quoted) {
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (c == '"') quoted = false;
                continue;
            }
            if (c == '"') {
                quoted = true;
                continue;
            }
            if (c == '{') depth++;
            else if (c == '}' && --depth == 0) {
                return source.substring(open + 1, i);
            }
        }
        return null;
    }

    private static BlockStmt parseBody(String source) {
        String text = source == null ? "" : source.trim();
        if (text.startsWith("{") && text.endsWith("}")) return StaticJavaParser.parseBlock(text);
        return StaticJavaParser.parseBlock("{\n" + text + "\n}");
    }

    private static BlockBean rawBean(String source, int id) {
        BlockBean bean = new BlockBean(String.valueOf(id), RAW_SPEC, " ", "", "addSourceDirectly");
        bean.parameters.add(source);
        bean.color = kq.a("addSourceDirectly", " ");
        return bean;
    }

    private static String clean(String message) {
        if (message == null || message.isEmpty()) return "Invalid Java syntax";
        int newline = message.indexOf('\n');
        return newline >= 0 ? message.substring(0, newline) : message;
    }

    private static final class ParsedBody {
        final BlockStmt body;
        final boolean fullSource;
        ParsedBody(BlockStmt body, boolean fullSource) {
            this.body = body;
            this.fullSource = fullSource;
        }
    }

    public static final class Analysis {
        public final boolean valid;
        public final int line;
        public final int column;
        public final String message;
        public final java.util.List<String> unsupported;
        public final boolean fullSource;

        Analysis(boolean valid, int line, int column, String message,
                 java.util.List<String> unsupported, boolean fullSource) {
            this.valid = valid;
            this.line = line;
            this.column = column;
            this.message = message;
            this.unsupported = unsupported;
            this.fullSource = fullSource;
        }
    }

    public static final class ImportResult {
        public final Analysis analysis;
        public final ArrayList<BlockBean> blocks;
        ImportResult(Analysis analysis, ArrayList<BlockBean> blocks) {
            this.analysis = analysis;
            this.blocks = blocks;
        }
    }
}
