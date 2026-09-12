package pro.sketchware.utility;

import org.xml.sax.SAXParseException;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilderFactory;

/** Lightweight, safe diagnostics that can run while the user is typing. */
public final class RealtimeDiagnostics {
    public enum Kind { XML, JAVA, KOTLIN, BLOCKS }

    public record Issue(
            Kind kind,
            int line,
            int column,
            int length,
            String severity,
            String message,
            String hint) {
    }

    private static final Pattern UNQUOTED_XML_ATTRIBUTE = Pattern.compile(
            "\\s+[A-Za-z_:][\\w:.-]*=\\s*(?![\\\"])[^\\s>]+"
    );

    private RealtimeDiagnostics() {
    }

    public static List<Issue> check(Kind kind, String source) {
        List<Issue> issues = new ArrayList<>();
        if (source == null || source.trim().isEmpty()) {
            return issues;
        }

        if (kind == Kind.XML) {
            checkXml(source, issues);
        } else if (kind == Kind.JAVA || kind == Kind.KOTLIN) {
            checkCode(kind, source, issues);
        } else {
            checkDelimiters(kind, source, issues);
        }
        return issues;
    }

    private static void checkXml(String source, List<Issue> issues) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            try {
                factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
                factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
                factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
                factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            } catch (Exception ignored) {
                // Older Android parsers may not expose every hardening feature.
            }
            factory.newDocumentBuilder().parse(
                    new ByteArrayInputStream(source.getBytes(StandardCharsets.UTF_8))
            );
        } catch (SAXParseException e) {
            add(issues, Kind.XML, Math.max(1, e.getLineNumber()),
                    Math.max(1, e.getColumnNumber()), 1, "error",
                    cleanMessage(e.getMessage()),
                    "Fix the tag, attribute, or closing element at this location.");
        } catch (Exception e) {
            add(issues, Kind.XML, 1, 1, 1, "error", safeMessage(e),
                    "Check XML structure, namespaces, and quoted attributes.");
        }

        Matcher unquoted = UNQUOTED_XML_ATTRIBUTE.matcher(source);
        if (unquoted.find()) {
            int line = lineOf(source, unquoted.start());
            int column = columnOf(source, unquoted.start());
            add(issues, Kind.XML, line, column, Math.max(1, unquoted.group().length()),
                    "error", "Attribute value must be quoted.", "Use attr=\"value\".");
        }
    }

    private static void checkCode(Kind kind, String source, List<Issue> issues) {
        checkDelimiters(kind, source, issues);
        String[] lines = source.split("\\n", -1);
        boolean java = kind == Kind.JAVA;

        for (int i = 0; i < lines.length; i++) {
            String raw = lines[i];
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("//") || line.startsWith("/*")
                    || line.startsWith("*") || line.startsWith("#")) {
                continue;
            }

            if (java && line.matches(".*\\b(class|interface|enum)\\s+[A-Za-z_$][\\w$]*\\.[A-Za-z_$][\\w$]*.*")) {
                addLine(issues, kind, i, raw, 1, "A class name cannot contain a dot.",
                        "Rename the class using a valid identifier, for example Test.");
            }
            if (java && line.matches(".*\\b(string|integer|boolean|float|double)\\s+[A-Za-z_$].*")) {
                addLine(issues, kind, i, raw, 1, "Java type names are case-sensitive.",
                        "Use String, Integer, Boolean, Float, or Double.");
            }
            if (line.matches(".*\\b(?:string|integer|bool)\\s+[A-Za-z_$][\\w$]*.*")) {
                addLine(issues, kind, i, raw, 1, "Unknown type name.",
                        java ? "Use a valid Java type spelling." : "Use String, Int, Boolean, Float, or Double.");
            }
            if (java && line.matches("(public|private|protected)\\s+[A-Za-z_$][\\w$]*\\s*\\(.*")
                    && !line.matches(".*\\b(class|interface|enum)\\b.*")) {
                addLine(issues, kind, i, raw, 1, "Method declaration is missing a return type.",
                        "Use a return type such as void, or make it a valid constructor.");
            }
            if (java && looksLikeStatement(line) && !line.endsWith(";")
                    && !line.endsWith("{") && !line.endsWith("}") && !line.endsWith(":")) {
                addLine(issues, kind, i, raw, Math.max(1, raw.length()),
                        "Statement is missing a semicolon.", "Terminate this statement with ;.");
            }
        }
    }

    private static boolean looksLikeStatement(String line) {
        if (line.startsWith("if ") || line.startsWith("if(") || line.startsWith("for ")
                || line.startsWith("for(") || line.startsWith("while ") || line.startsWith("while(")
                || line.startsWith("switch ") || line.startsWith("switch(") || line.startsWith("catch ")
                || line.startsWith("catch(") || line.startsWith("else") || line.startsWith("do")
                || line.startsWith("try") || line.startsWith("class ") || line.startsWith("public class")
                || line.startsWith("private class") || line.startsWith("protected class")
                || line.startsWith("package ") || line.startsWith("import ") || line.startsWith("@")) {
            return false;
        }
        return line.startsWith("return ") || line.startsWith("throw ") || line.startsWith("break")
                || line.startsWith("continue") || line.matches(".*[=+\\-*/].*")
                || line.matches(".*\\w+\\s*\\(.*\\).*" );
    }

    private static void checkDelimiters(Kind kind, String source, List<Issue> issues) {
        int round = 0;
        int curly = 0;
        int square = 0;
        int line = 1;
        int column = 0;
        boolean lineComment = false;
        boolean blockComment = false;
        boolean inString = false;
        boolean inChar = false;
        boolean escaped = false;

        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '\n') {
                line++;
                column = 0;
                lineComment = false;
                escaped = false;
                continue;
            }
            column++;

            if (lineComment) {
                continue;
            }
            if (blockComment) {
                if (c == '*' && i + 1 < source.length() && source.charAt(i + 1) == '/') {
                    blockComment = false;
                    i++;
                    column++;
                }
                continue;
            }
            if (inString) {
                if (c == '"' && !escaped) {
                    inString = false;
                }
                escaped = c == '\\' && !escaped;
                continue;
            }
            if (inChar) {
                if (c == '\'' && !escaped) {
                    inChar = false;
                }
                escaped = c == '\\' && !escaped;
                continue;
            }
            if (c == '/' && i + 1 < source.length() && source.charAt(i + 1) == '/') {
                lineComment = true;
                i++;
                column++;
                continue;
            }
            if (c == '/' && i + 1 < source.length() && source.charAt(i + 1) == '*') {
                blockComment = true;
                i++;
                column++;
                continue;
            }
            if (c == '"') {
                inString = true;
                escaped = false;
                continue;
            }
            if (c == '\'') {
                inChar = true;
                escaped = false;
                continue;
            }

            if (c == '(') round++;
            else if (c == ')') round--;
            else if (c == '{') curly++;
            else if (c == '}') curly--;
            else if (c == '[') square++;
            else if (c == ']') square--;

            if (round < 0 || curly < 0 || square < 0) {
                add(issues, kind, line, column, 1, "error",
                        "Closing bracket has no matching opening bracket.",
                        "Check the surrounding block and indentation.");
                return;
            }
        }

        if (round != 0 || curly != 0 || square != 0) {
            add(issues, kind, line, Math.max(1, column), 1, "error",
                    "Unbalanced brackets or braces.",
                    "Every opening bracket must have a matching closing bracket.");
        }
    }

    private static void addLine(List<Issue> issues, Kind kind, int zeroBasedLine,
                                String raw, int length, String message, String hint) {
        add(issues, kind, zeroBasedLine + 1, Math.max(1, raw.length()),
                Math.max(1, length), "error", message, hint);
    }

    private static void add(List<Issue> issues, Kind kind, int line, int column, int length,
                            String severity, String message, String hint) {
        issues.add(new Issue(kind, Math.max(1, line), Math.max(1, column),
                Math.max(1, length), severity, message, hint));
    }

    private static int lineOf(String source, int index) {
        int end = Math.min(index, source.length());
        int line = 1;
        for (int i = 0; i < end; i++) {
            if (source.charAt(i) == '\n') line++;
        }
        return line;
    }

    private static int columnOf(String source, int index) {
        int end = Math.min(index, source.length());
        int lastNewLine = source.lastIndexOf('\n', Math.max(0, end - 1));
        return Math.max(1, end - lastNewLine);
    }

    private static String cleanMessage(String message) {
        if (message == null || message.isBlank()) return "Invalid source structure.";
        int newline = message.indexOf('\n');
        return newline >= 0 ? message.substring(0, newline) : message;
    }

    private static String safeMessage(Exception e) {
        return cleanMessage(e.getMessage());
    }
}
