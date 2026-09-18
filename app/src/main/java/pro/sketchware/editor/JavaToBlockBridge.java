package pro.sketchware.editor;

import com.besome.sketch.beans.BlockBean;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.BooleanLiteralExpr;
import com.github.javaparser.ast.expr.CharLiteralExpr;
import com.github.javaparser.ast.expr.DoubleLiteralExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.expr.UnaryExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.Statement;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import a.a.a.kq;

/** Converts Java event bodies to Sketchware blocks without silently dropping code. */
public final class JavaToBlockBridge {
    private static final String RAW_SPEC = "add source directly %s.inputOnly";

    private JavaToBlockBridge() { }

    public static Analysis analyze(String source) {
        try {
            BlockStmt block = parseBody(source);
            ArrayList<String> preserved = new ArrayList<>();
            for (Statement statement : block.getStatements()) {
                if (!toNativeBean(statement, 1).isPresent()) preserved.add(statement.toString());
            }
            return new Analysis(true, 0, 0, "", preserved);
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
            int nextId = 1;
            for (Statement statement : body.getStatements()) {
                java.util.Optional<BlockBean> nativeBean = toNativeBean(statement, nextId);
                BlockBean bean = nativeBean.isPresent() ? nativeBean.get() : rawBean(statement, nextId);
                blocks.add(bean);
                nextId++;
            }
            if (blocks.isEmpty() && source != null && !source.trim().isEmpty()) {
                blocks.add(rawBeanText(source.trim(), nextId));
            }
            linkNext(blocks);
        } catch (Throwable error) {
            return new ImportResult(new Analysis(false, 1, 1,
                    "Unable to convert this Java body: " + clean(error.getMessage()),
                    Collections.emptyList()), blocks);
        }
        return new ImportResult(analysis, blocks);
    }

    private static void linkNext(ArrayList<BlockBean> blocks) {
        for (int i = 0; i + 1 < blocks.size(); i++) {
            blocks.get(i).nextBlock = Integer.parseInt(blocks.get(i + 1).id);
        }
    }

    private static BlockStmt parseBody(String source) {
        String text = source == null ? "" : source.trim();
        if (text.startsWith("{") && text.endsWith("}")) return StaticJavaParser.parseBlock(text);
        return StaticJavaParser.parseBlock("{\n" + text + "\n}");
    }

    private static java.util.Optional<BlockBean> toNativeBean(Statement statement, int id) {
        if (!(statement instanceof ExpressionStmt expressionStmt)) return java.util.Optional.empty();
        Expression expression = expressionStmt.getExpression();
        if (!(expression instanceof MethodCallExpr call)) return java.util.Optional.empty();

        String method = call.getNameAsString();
        String opcode = method;
        String spec;
        String type = "v";
        List<Expression> args = new ArrayList<>(call.getArguments());
        String scope = call.getScope().map(Node::toString).orElse("");

        if ("showMessage".equals(method) && "SketchwareUtil".equals(scope)) {
            opcode = "doToast";
            type = "p";
            spec = "%s";
            if (args.size() == 2) args = new ArrayList<>(args.subList(1, 2));
        } else {
            if (scope.startsWith("binding.")) scope = scope.substring("binding.".length());
            switch (method) {
                case "setText" -> spec = "%m.view setText %s";
                case "setEnabled" -> { opcode = "setEnable"; spec = "%m.view setEnable %b"; }
                case "setVisibility" -> { opcode = "setVisible"; spec = "%m.view setVisible %m.visibility"; }
                case "setAlpha" -> spec = "%m.view setAlpha %d";
                case "setRotation" -> { opcode = "setRotate"; spec = "%m.view setRotate %d"; }
                case "setTranslationX" -> spec = "%m.view setTranslationX %d";
                case "setTranslationY" -> spec = "%m.view setTranslationY %d";
                case "setScaleX" -> spec = "%m.view setScaleX %d";
                case "setScaleY" -> spec = "%m.view setScaleY %d";
                case "setChecked" -> spec = "%m.checkbox setChecked %b";
                case "setClickable" -> spec = "%m.view setClickable %b";
                case "setHint" -> spec = "%m.edittext setHint %s";
                case "setTextColor" -> spec = "%m.textview setTextColor %m.color";
                case "setBackgroundColor" -> { opcode = "setBgColor"; spec = "%m.view setBgColor %m.color"; }
                case "requestFocus" -> spec = "%m.view requestFocus";
                default -> { return java.util.Optional.empty(); }
            }
            if (scope.isEmpty()) return java.util.Optional.empty();
            args.add(0, new NameExpr(scope));
        }
        if (args.size() != specTypes(spec).size()) return java.util.Optional.empty();
        BlockBean bean = new BlockBean(String.valueOf(id), spec, type, "", opcode);
        for (Expression arg : args) bean.parameters.add(parameterValue(arg));
        bean.color = kq.a(opcode, type);
        return java.util.Optional.of(bean);
    }

    private static BlockBean rawBean(Statement statement, int id) { return rawBeanText(statement.toString(), id); }

    private static BlockBean rawBeanText(String source, int id) {
        BlockBean bean = new BlockBean(String.valueOf(id), RAW_SPEC, " ", "", "addSourceDirectly");
        bean.parameters.add(source);
        bean.color = kq.a("addSourceDirectly", " ");
        return bean;
    }

    private static List<String> specTypes(String spec) {
        ArrayList<String> types = new ArrayList<>();
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("%\\w+(?:\\.\\w+)?").matcher(spec);
        while (matcher.find()) types.add(matcher.group());
        return types;
    }

    private static String parameterValue(Expression expression) {
        if (expression instanceof StringLiteralExpr value) return value.asString();
        if (expression instanceof CharLiteralExpr value) return value.getValue();
        if (expression instanceof BooleanLiteralExpr value) return value.getValue() ? "true" : "false";
        if (expression instanceof IntegerLiteralExpr value) return value.getValue().replace("_", "");
        if (expression instanceof DoubleLiteralExpr value) return value.getValue().replace("_", "");
        if (expression instanceof FieldAccessExpr field) {
            String name = field.toString();
            if (name.startsWith("View.")) return name.substring("View.".length()).toLowerCase();
            return name;
        }
        if (expression instanceof UnaryExpr unary && unary.getOperator() == UnaryExpr.Operator.MINUS) {
            return "-" + parameterValue(unary.getExpression());
        }
        return expression.toString().replace("((float) (", "").replace("))", "");
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
        public final List<String> unsupported;
        Analysis(boolean valid, int line, int column, String message, List<String> unsupported) {
            this.valid = valid; this.line = line; this.column = column; this.message = message; this.unsupported = unsupported;
        }
    }

    public static final class ImportResult {
        public final Analysis analysis;
        public final ArrayList<BlockBean> blocks;
        ImportResult(Analysis analysis, ArrayList<BlockBean> blocks) { this.analysis = analysis; this.blocks = blocks; }
    }
}
