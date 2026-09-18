package pro.sketchware.editor;

import com.besome.sketch.beans.BlockBean;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.Statement;

import java.util.ArrayList;
import java.util.Collections;

import a.a.a.kq;

/**
 * Imports Java event code as guaranteed-renderable Sketchware command blocks.
 *
 * A Java statement and a Sketchware block are not interchangeable: a block's
 * opcode/spec must exist in the active palette or the editor can persist it
 * and then render only the event header after reload. Until a native mapping
 * is verified against the active palette, this bridge deliberately uses the
 * built-in addSourceDirectly command block. That guarantees that every valid
 * Java statement is visible and never silently discarded.
 */
public final class JavaToBlockBridge {
    private static final String RAW_SPEC = "add source directly %s.inputOnly";

    private JavaToBlockBridge() { }

    public static Analysis analyze(String source) {
        try {
            BlockStmt body = parseBody(source);
            ArrayList<String> statements = new ArrayList<>();
            for (Statement statement : body.getStatements()) statements.add(statement.toString());
            return new Analysis(true, 0, 0, "", statements);
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
            return new Analysis(false, line, column, clean(error.getMessage()), Collections.emptyList());
        }
    }

    public static ImportResult toBlocks(String source) {
        Analysis analysis = analyze(source);
        if (!analysis.valid) return new ImportResult(analysis, new ArrayList<>());

        ArrayList<BlockBean> blocks = new ArrayList<>();
        try {
            BlockStmt body = parseBody(source);
            int id = 1;
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
                    Collections.emptyList()), blocks);
        }
        return new ImportResult(analysis, blocks);
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

    public static final class Analysis {
        public final boolean valid;
        public final int line;
        public final int column;
        public final String message;
        public final java.util.List<String> unsupported;

        Analysis(boolean valid, int line, int column, String message, java.util.List<String> unsupported) {
            this.valid = valid;
            this.line = line;
            this.column = column;
            this.message = message;
            this.unsupported = unsupported;
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
