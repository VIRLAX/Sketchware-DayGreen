package pro.sketchware.editor.layoutbuilder;

import android.content.ClipData;
import android.content.ClipDescription;
import android.os.Build;
import android.view.DragEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.rosemoe.sora.text.Content;
import io.github.rosemoe.sora.widget.CodeEditor;

/**
 * Manages drag & drop from the widget palette into the code editor.
 * Detects the nearest container tag and inserts widget XML with correct indentation.
 *
 * Usage:
 *   DragDropLayoutBuilder builder = new DragDropLayoutBuilder(codeEditor);
 *   builder.attach();
 *   DragDropLayoutBuilder.startWidgetDrag(view, widgetTemplate);
 */
public class DragDropLayoutBuilder {

    public static final String MIME_WIDGET = "application/x-sketchware-widget";
    private static final AtomicInteger idCounter = new AtomicInteger(1);

    private final CodeEditor editor;
    @Nullable private Runnable onDropCallback;

    public DragDropLayoutBuilder(@NonNull CodeEditor editor) {
        this.editor = editor;
    }

    public void setOnDropCallback(@Nullable Runnable callback) {
        this.onDropCallback = callback;
    }

    public static void startWidgetDrag(@NonNull View view, @NonNull WidgetTemplate template) {
        ClipData.Item item = new ClipData.Item(template.getName());
        ClipData dragData = new ClipData(
                template.getName(),
                new String[]{MIME_WIDGET},
                item
        );
        View.DragShadowBuilder shadow = new View.DragShadowBuilder(view);
        view.setTag(template);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            view.startDragAndDrop(dragData, shadow, template, 0);
        } else {
            view.startDrag(dragData, shadow, template, 0);
        }
    }

    public void attach() {
        editor.setOnDragListener((v, event) -> {
            if (!isWidgetDrag(event)) return false;
            switch (event.getAction()) {
                case DragEvent.ACTION_DRAG_STARTED: return true;
                case DragEvent.ACTION_DRAG_LOCATION: handleDragLocation(event); return true;
                case DragEvent.ACTION_DROP: return handleDrop(event);
                case DragEvent.ACTION_DRAG_ENDED: return true;
                default: return true;
            }
        });
    }

    private boolean isWidgetDrag(DragEvent event) {
        if (event.getClipDescription() == null) return false;
        return event.getClipDescription().hasMimeType(MIME_WIDGET);
    }

    private void handleDragLocation(DragEvent event) {
        int[] lineAndCol = getPositionFromDragEvent(event);
        if (lineAndCol != null) {
            editor.setSelection(lineAndCol[0], lineAndCol[1]);
        }
    }

    private boolean handleDrop(DragEvent event) {
        Object local = event.getLocalState();
        if (!(local instanceof WidgetTemplate template)) return false;

        int[] pos = getPositionFromDragEvent(event);
        if (pos == null) return false;

        int dropLine = pos[0];
        InsertionPoint point = findBestInsertionPoint(dropLine);
        if (point == null) {
            point = new InsertionPoint(dropLine, guessIndentation(dropLine));
        }

        String idSuffix = String.valueOf(idCounter.getAndIncrement());
        String xml = template.generateXml(idSuffix, point.indent);

        Content content = editor.getText();
        int insertLine = Math.min(point.line, content.getLineCount() - 1);
        String lineText = content.getLineString(insertLine);
        String indentStr = " ".repeat(point.indent);
        String insertText = "\n" + indentStr + xml;

        content.insert(insertLine, lineText.length(), insertText);

        int newLine = insertLine + insertText.split("\n").length - 1;
        editor.setSelection(Math.min(newLine, content.getLineCount() - 1), 0);

        if (onDropCallback != null) onDropCallback.run();
        return true;
    }

    @Nullable
    private int[] getPositionFromDragEvent(DragEvent event) {
        try {
            float y = event.getY();
            float lineHeight = editor.getRowHeight();
            float scrollY = editor.getOffsetY();
            int targetLine = (int) ((y + scrollY) / lineHeight);
            targetLine = Math.max(0, Math.min(targetLine, editor.getText().getLineCount() - 1));
            return new int[]{targetLine, 0};
        } catch (Exception e) {
            return null;
        }
    }

    @Nullable
    private InsertionPoint findBestInsertionPoint(int dropLine) {
        Content content = editor.getText();
        int lineCount = content.getLineCount();

        for (int i = dropLine; i >= 0; i--) {
            String line = content.getLineString(i).trim();
            if (isContainerOpeningTag(line)) {
                int indent = getLeadingSpaces(content.getLineString(i)) + 4;
                int insertAt = Math.min(dropLine, lineCount - 1);
                for (int j = i + 1; j < lineCount; j++) {
                    String checkLine = content.getLineString(j).trim();
                    if (isClosingTagFor(checkLine, line)) {
                        insertAt = Math.min(insertAt, j - 1);
                        break;
                    }
                }
                return new InsertionPoint(Math.max(insertAt, i), indent);
            }
        }
        return null;
    }

    private boolean isContainerOpeningTag(String line) {
        return line.matches("^<(LinearLayout|RelativeLayout|FrameLayout|ScrollView|" +
                "HorizontalScrollView|androidx\\..*|android\\.widget\\..*|" +
                "com\\.google\\.android\\.material\\..*).*") &&
                !line.endsWith("/>");
    }

    private boolean isClosingTagFor(String closingLine, String openingLine) {
        Matcher openMatcher = Pattern.compile("^<(\\S+)").matcher(openingLine);
        if (!openMatcher.find()) return false;
        String tagName = openMatcher.group(1);
        return closingLine.equals("</" + tagName + ">");
    }

    private int guessIndentation(int line) {
        Content content = editor.getText();
        if (line < 0 || line >= content.getLineCount()) return 4;
        return getLeadingSpaces(content.getLineString(line)) + 4;
    }

    private int getLeadingSpaces(String line) {
        int count = 0;
        for (char c : line.toCharArray()) {
            if (c == ' ') count++;
            else if (c == '\t') count += 4;
            else break;
        }
        return count;
    }

    private static class InsertionPoint {
        final int line;
        final int indent;
        InsertionPoint(int line, int indent) {
            this.line = line;
            this.indent = indent;
        }
    }
}
