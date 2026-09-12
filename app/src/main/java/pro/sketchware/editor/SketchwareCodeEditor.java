package pro.sketchware.editor;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;

import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.rosemoe.sora.lang.analysis.StyleUpdateRange;
import io.github.rosemoe.sora.lang.styling.Span;
import io.github.rosemoe.sora.lang.styling.Spans;
import io.github.rosemoe.sora.lang.styling.Styles;
import io.github.rosemoe.sora.lang.styling.color.ResolvableColor;
import io.github.rosemoe.sora.lang.styling.line.LineSideIcon;
import io.github.rosemoe.sora.lang.styling.span.SpanColorResolver;
import io.github.rosemoe.sora.lang.styling.span.SpanExtAttrs;
import io.github.rosemoe.sora.text.Content;
import io.github.rosemoe.sora.widget.CodeEditor;

import pro.sketchware.R;
import pro.sketchware.activities.resourceseditor.components.utils.ColorsEditorManager;
import pro.sketchware.editor.diagnostics.SquigglyLineRenderer;
import pro.sketchware.utility.RealtimeDiagnostics;
import pro.sketchware.utility.ThemeUtils;

public class SketchwareCodeEditor extends CodeEditor {
    private static final Pattern RESOURCE_PATTERN = Pattern.compile(
            "#([0-9a-fA-F]{3,8})|@(?:android:)?color/([a-zA-Z0-9_]+)"
                    + "|R\\.color\\.([a-zA-Z0-9_]+)|0x([0-9a-fA-F]{6,8})"
                    + "|\\b(?:android\\.graphics\\.)?Color\\.(BLACK|BLUE|CYAN|DKGRAY|GRAY|GREEN|LTGRAY|MAGENTA|RED|TRANSPARENT|WHITE|YELLOW)\\b"
                    + "|@(?:android:)?drawable/([a-zA-Z0-9_]+)|R\\.drawable\\.([a-zA-Z0-9_]+)"
                    + "|@(?:android:)?mipmap/([a-zA-Z0-9_]+)|R\\.mipmap\\.([a-zA-Z0-9_]+)"
    );

    private ColorsEditorManager colorsManager;
    private String currentScId;
    private final Map<String, Drawable> iconCache = new HashMap<>();
    private List<RealtimeDiagnostics.Issue> diagnostics = List.of();

    public SketchwareCodeEditor(Context context) { super(context); }
    public SketchwareCodeEditor(Context context, AttributeSet attrs) { super(context, attrs); }
    public SketchwareCodeEditor(Context context, AttributeSet attrs, int defStyleAttr) { super(context, attrs, defStyleAttr); }

    public void setScId(String id) {
        if (id != null && !id.equals(currentScId)) {
            currentScId = id;
            com.besome.sketch.design.DesignActivity.sc_id = id;
            colorsManager = new ColorsEditorManager();
            rerunAnalysis();
        }
    }

    /** Only redraw diagnostics when the result actually changed, preventing visible blinking. */
    public void setDiagnostics(List<RealtimeDiagnostics.Issue> issues) {
        List<RealtimeDiagnostics.Issue> next = issues == null ? List.of() : new ArrayList<>(issues);
        if (diagnostics.equals(next)) return;
        diagnostics = next;
        rerunAnalysis();
    }

    @Override
    public void setStyles(@Nullable Styles styles) {
        if (styles != null) processStyles(styles, null);
        super.setStyles(styles);
    }

    @Override
    public void updateStyles(@Nullable Styles styles, @Nullable StyleUpdateRange range) {
        if (styles != null) processStyles(styles, range);
        super.updateStyles(styles, range);
    }

    private void processStyles(Styles styles, @Nullable StyleUpdateRange range) {
        getProps().sideIconSizeFactor = .9f;
        Content content = getText();
        Spans.Modifier modifier = styles.spans != null && styles.spans.supportsModify()
                ? styles.spans.modify() : null;
        Spans.Reader reader = styles.spans != null ? styles.spans.read() : null;
        int count = content.getLineCount();

        if (range == null) {
            styles.eraseAllLineStyles();
            for (int i = 0; i < count; i++) processLine(i, content, modifier, reader, styles);
        } else {
            var iterator = range.lineIndexIterator(count);
            while (iterator.hasNext()) {
                int line = iterator.nextInt();
                if (line >= 0 && line < content.getLineCount()) {
                    styles.eraseLineStyle(line, LineSideIcon.class);
                    processLine(line, content, modifier, reader, styles);
                }
            }
        }
        styles.finishBuilding();
    }

    private boolean processLine(int line, Content text, Spans.Modifier modifier,
                                Spans.Reader reader, Styles styles) {
        if (line < 0 || line >= text.getLineCount()) return false;
        Matcher matcher = RESOURCE_PATTERN.matcher(text.getLineString(line));
        List<Span> spans = reader != null
                ? new ArrayList<>(reader.getSpansOnLine(line)) : new ArrayList<>();
        boolean modified = false;
        boolean iconAdded = false;

        while (matcher.find()) {
            String match = matcher.group();
            int color = resolveColor(match);
            if (color != 0) {
                if (modifier != null && addBoundedColorSpan(spans, matcher.start(), matcher.end(), color)) modified = true;
                if (!iconAdded) {
                    styles.addLineStyle(new LineSideIcon(line, getIconForColor(color)));
                    iconAdded = true;
                }
            } else {
                Drawable drawable = resolveDrawable(match);
                if (drawable != null && !iconAdded) {
                    styles.addLineStyle(new LineSideIcon(line, drawable));
                    iconAdded = true;
                }
            }
        }

        for (RealtimeDiagnostics.Issue issue : diagnostics) {
            if (issue.line() != line + 1) continue;
            Drawable bug = AppCompatResources.getDrawable(getContext(), R.drawable.ic_mtrl_bug_report);
            if (bug != null) {
                bug = bug.mutate();
                bug.setTint("warning".equals(issue.severity())
                        ? 0xFFFFA726 : "info".equals(issue.severity()) ? 0xFF42A5F5 : 0xFFE53935);
                styles.addLineStyle(new LineSideIcon(line, bug));
                iconAdded = true;
            }
            if (modifier != null) {
                int lineLength = text.getLineString(line).length();
                int start = Math.min(lineLength, Math.max(0, issue.column() - 1));
                int end = Math.min(lineLength, start + Math.max(1, issue.length()));
                if (end <= start && lineLength > 0) end = Math.min(lineLength, start + 1);
                Span anchor = getOrCreateSpanAt(spans, start);
                if (end > start) getOrCreateSpanAt(spans, end);
                int severity = "warning".equals(issue.severity())
                        ? SquigglyLineRenderer.SEVERITY_WARNING
                        : "info".equals(issue.severity())
                        ? SquigglyLineRenderer.SEVERITY_INFO
                        : SquigglyLineRenderer.SEVERITY_ERROR;
                anchor.setSpanExt(SpanExtAttrs.EXT_EXTERNAL_RENDERER,
                        new SquigglyLineRenderer(severity, issue.message()));
                modified = true;
            }
        }

        if (modified && modifier != null) modifier.setSpansOnLine(line, spans);
        return iconAdded;
    }

    private Drawable getIconForColor(int color) {
        String key = "color_" + color;
        Drawable drawable = iconCache.get(key);
        if (drawable == null) {
            float density = getResources().getDisplayMetrics().density;
            int size = (int) (18 * density);
            Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            paint.setColor(color);
            float radius = 4 * density;
            canvas.drawRoundRect(new android.graphics.RectF(0, 0, size, size), radius, radius, paint);
            drawable = new BitmapDrawable(getResources(), bitmap);
            iconCache.put(key, drawable);
        }
        return drawable;
    }

    private Drawable resolveDrawable(String match) {
        String name = null;
        boolean system = false;
        boolean mipmap = false;
        if (match.startsWith("@drawable/")) name = match.substring(10);
        else if (match.startsWith("@android:drawable/")) { name = match.substring(18); system = true; }
        else if (match.startsWith("R.drawable.")) name = match.substring(11);
        else if (match.startsWith("@mipmap/")) { name = match.substring(8); mipmap = true; }
        else if (match.startsWith("@android:mipmap/")) { name = match.substring(16); system = true; mipmap = true; }
        else if (match.startsWith("R.mipmap.")) { name = match.substring(9); mipmap = true; }
        if (name == null) return null;

        String key = (system ? "sys_" : mipmap ? "mip_" : "res_") + name;
        if (iconCache.containsKey(key)) return iconCache.get(key);
        try {
            int id = getContext().getResources().getIdentifier(name, mipmap ? "mipmap" : "drawable",
                    system ? "android" : getContext().getPackageName());
            if (id != 0) {
                Drawable drawable = AppCompatResources.getDrawable(getContext(), id);
                if (drawable != null) {
                    iconCache.put(key, drawable);
                    return drawable;
                }
            }
        } catch (Exception ignored) { }
        return null;
    }

    private int resolveColor(String value) {
        try {
            if (value.startsWith("#")) {
                String hex = value;
                if (hex.length() == 4) {
                    hex = "#" + hex.charAt(1) + hex.charAt(1) + hex.charAt(2) + hex.charAt(2)
                            + hex.charAt(3) + hex.charAt(3);
                }
                return Color.parseColor(hex);
            }
            if (value.startsWith("0x")) return (int) Long.parseLong(value.substring(2), 16);
            if (value.startsWith("R.color.")) return resolveXmlColor("@color/" + value.substring(8));
            if (value.contains("Color.")) return resolveColorConstant(value.substring(value.lastIndexOf('.') + 1));
            return resolveXmlColor(value);
        } catch (Exception ignored) { return 0; }
    }

    private int resolveXmlColor(String value) {
        if (colorsManager == null) return 0;
        try {
            String hex = colorsManager.getColorValue(getContext(), value, 4,
                    ThemeUtils.isDarkThemeEnabled(getContext()));
            return hex == null || hex.equals("#00000000") ? 0 : Color.parseColor(hex);
        } catch (Exception ignored) { return 0; }
    }

    private int resolveColorConstant(String value) {
        return switch (value) {
            case "BLACK" -> Color.BLACK;
            case "BLUE" -> Color.BLUE;
            case "CYAN" -> Color.CYAN;
            case "DKGRAY" -> Color.DKGRAY;
            case "GRAY" -> Color.GRAY;
            case "GREEN" -> Color.GREEN;
            case "LTGRAY" -> Color.LTGRAY;
            case "MAGENTA" -> Color.MAGENTA;
            case "RED" -> Color.RED;
            case "TRANSPARENT" -> Color.TRANSPARENT;
            case "WHITE" -> Color.WHITE;
            case "YELLOW" -> Color.YELLOW;
            default -> 0;
        };
    }

    private boolean addBoundedColorSpan(List<Span> spans, int start, int end, int color) {
        getOrCreateSpanAt(spans, start);
        getOrCreateSpanAt(spans, end);
        int textColor = ColorPreviewRenderer.isDarkColor(color) ? Color.WHITE : Color.BLACK;
        SpanColorResolver resolver = new SpanColorResolver() {
            @Override public ResolvableColor getForegroundColor(Span span) { return scheme -> textColor; }
            @Override public ResolvableColor getBackgroundColor(Span span) { return null; }
        };
        List<Span> affected = new ArrayList<>();
        for (Span span : spans) if (span.getColumn() >= start && span.getColumn() < end) affected.add(span);
        for (int i = 0; i < affected.size(); i++) {
            Span span = affected.get(i);
            span.setSpanExt(SpanExtAttrs.EXT_EXTERNAL_RENDERER,
                    new ColorPreviewRenderer(color, i == 0, i == affected.size() - 1));
            span.setSpanExt(SpanExtAttrs.EXT_COLOR_RESOLVER, resolver);
        }
        return !affected.isEmpty();
    }

    private Span getOrCreateSpanAt(List<Span> spans, int column) {
        for (int i = 0; i < spans.size(); i++) {
            Span span = spans.get(i);
            if (span.getColumn() == column) return span;
            if (span.getColumn() > column) {
                long style = i > 0 ? spans.get(i - 1).getStyle() : 0;
                Span created = Span.obtain(column, style);
                spans.add(i, created);
                return created;
            }
        }
        Span created = Span.obtain(column, spans.isEmpty() ? 0 : spans.get(spans.size() - 1).getStyle());
        spans.add(created);
        return created;
    }
}
