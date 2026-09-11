package pro.sketchware.utility;

import org.xml.sax.SAXParseException;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilderFactory;

/** Offline, deterministic diagnostics for editor content. No network or build side effects. */
public final class RealtimeDiagnostics {
    public enum Kind { XML, JAVA, KOTLIN, BLOCKS }
    public record Issue(Kind kind, int line, int column, String severity, String message, String hint) {}

    private RealtimeDiagnostics() {}

    public static List<Issue> check(Kind kind, String source) {
        List<Issue> issues = new ArrayList<>();
        if (source == null || source.trim().isEmpty()) return issues;
        switch (kind) {
            case XML -> checkXml(source, issues);
            case JAVA, KOTLIN -> checkBraces(kind, source, issues);
            case BLOCKS -> checkBraces(kind, source, issues);
        }
        return issues;
    }

    private static void checkXml(String source, List<Issue> issues) {
        try {
            var factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.newDocumentBuilder().parse(new ByteArrayInputStream(source.getBytes(StandardCharsets.UTF_8)));
        } catch (SAXParseException e) {
            issues.add(new Issue(Kind.XML, e.getLineNumber(), e.getColumnNumber(), "error", e.getMessage(), "Close the missing tag or fix the attribute near this location."));
        } catch (Exception e) {
            issues.add(new Issue(Kind.XML, lineOf(source, 0), 1, "error", safeMessage(e), "Check XML structure, namespace declarations, and attribute quoting."));
        }
        Matcher unquoted = Pattern.compile("\\s+[A-Za-z_:][\\w:.-]*=\\s*(?![\\\"])\\S+").matcher(source);
        if (unquoted.find()) issues.add(new Issue(Kind.XML, lineOf(source, unquoted.start()), 1, "error", "Attribute value must be quoted.", "Use attr=\"value\"."));
    }

    private static void checkBraces(Kind kind, String source, List<Issue> issues) {
        int round = 0, curly = 0, square = 0;
        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '(') round++; else if (c == ')') round--;
            else if (c == '{') curly++; else if (c == '}') curly--;
            else if (c == '[') square++; else if (c == ']') square--;
            if (round < 0 || curly < 0 || square < 0) {
                issues.add(new Issue(kind, lineOf(source, i), 1, "error", "Closing bracket has no matching opening bracket.", "Check the surrounding block and indentation."));
                return;
            }
        }
        if (round != 0 || curly != 0 || square != 0) issues.add(new Issue(kind, lineOf(source, source.length()), 1, "error", "Unbalanced brackets or braces.", "Every opening bracket must have a matching closing bracket."));
    }

    private static int lineOf(String source, int index) { return (int) source.substring(0, Math.min(index, source.length())).chars().filter(c -> c == '\n').count() + 1; }
    private static String safeMessage(Exception e) { return e.getMessage() == null ? "Invalid source structure." : e.getMessage(); }
}
