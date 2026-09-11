package pro.sketchware.editor.diagnostics;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;

import androidx.annotation.NonNull;

import io.github.rosemoe.sora.lang.styling.Span;
import io.github.rosemoe.sora.lang.styling.span.SpanExternalRenderer;
import io.github.rosemoe.sora.widget.CodeEditor;

/**
 * Renders a squiggly (wavy) underline beneath text spans that contain errors/warnings.
 * Similar to VS Code / Android Studio red/yellow wavy underlines.
 */
public class SquigglyLineRenderer implements SpanExternalRenderer {

    public static final int SEVERITY_ERROR = 0;
    public static final int SEVERITY_WARNING = 1;
    public static final int SEVERITY_INFO = 2;

    private static final float WAVE_HEIGHT = 3f;
    private static final float WAVE_LENGTH = 6f;
    private static final float STROKE_WIDTH = 1.5f;

    private final int severity;
    private final String message;

    public SquigglyLineRenderer(int severity, String message) {
        this.severity = severity;
        this.message = message;
    }

    public int getSeverity() {
        return severity;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public boolean requirePreDraw() {
        return false;
    }

    @Override
    public boolean requirePostDraw() {
        return true;
    }

    @Override
    public void draw(@NonNull Canvas canvas, @NonNull Paint paint, @NonNull CodeEditor editor,
                     @NonNull Span span, float startX, float endX, int lineTop, int lineBottom,
                     int lineBaseline) {
        // no-op for pre-draw
    }

    @Override
    public void drawAfter(@NonNull Canvas canvas, @NonNull Paint paint, @NonNull CodeEditor editor,
                          @NonNull Span span, float startX, float endX, int lineTop, int lineBottom,
                          int lineBaseline) {
        if (startX >= endX) return;

        float density = editor.getResources().getDisplayMetrics().density;
        float waveH = WAVE_HEIGHT * density;
        float waveL = WAVE_LENGTH * density;
        float strokeW = STROKE_WIDTH * density;

        Paint wavePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        wavePaint.setStyle(Paint.Style.STROKE);
        wavePaint.setStrokeWidth(strokeW);
        wavePaint.setStrokeCap(Paint.Cap.ROUND);

        switch (severity) {
            case SEVERITY_ERROR:
                wavePaint.setColor(0xFFE53935); // Material Red 600
                break;
            case SEVERITY_WARNING:
                wavePaint.setColor(0xFFFFA726); // Material Orange 400
                break;
            case SEVERITY_INFO:
            default:
                wavePaint.setColor(0xFF42A5F5); // Material Blue 400
                break;
        }

        float y = lineBottom - (2f * density);

        Path path = new Path();
        path.moveTo(startX, y);

        float x = startX;
        boolean up = true;
        while (x < endX) {
            float nextX = Math.min(x + waveL / 2f, endX);
            float nextY = up ? y - waveH : y + waveH;
            path.quadTo(x + (nextX - x) / 2f, nextY, nextX, y);
            x = nextX;
            up = !up;
        }

        canvas.drawPath(path, wavePaint);
    }
}
