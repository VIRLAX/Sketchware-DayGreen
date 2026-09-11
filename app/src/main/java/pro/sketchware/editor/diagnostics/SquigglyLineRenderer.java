package pro.sketchware.editor.diagnostics;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;

import io.github.rosemoe.sora.lang.styling.span.SpanExternalRenderer;
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme;

/** Wavy underline renderer compatible with the Sora editor renderer API. */
public final class SquigglyLineRenderer implements SpanExternalRenderer {
    public static final int SEVERITY_ERROR = 0;
    public static final int SEVERITY_WARNING = 1;
    public static final int SEVERITY_INFO = 2;

    private final int severity;
    private final Paint wavePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public SquigglyLineRenderer(int severity, String message) {
        this.severity = severity;
        wavePaint.setStyle(Paint.Style.STROKE);
        wavePaint.setStrokeWidth(1.5f);
        wavePaint.setStrokeCap(Paint.Cap.ROUND);
        wavePaint.setColor(severity == SEVERITY_ERROR ? 0xFFE53935
                : severity == SEVERITY_WARNING ? 0xFFFFA726 : 0xFF42A5F5);
    }

    public int getSeverity() { return severity; }

    @Override
    public boolean requirePreDraw() { return false; }

    @Override
    public boolean requirePostDraw() { return true; }

    @Override
    public void draw(Canvas canvas, Paint paint, EditorColorScheme colorScheme, boolean preOrPost) {
        if (!preOrPost || canvas == null) return;
        float width = canvas.getClipBounds().width();
        float bottom = canvas.getClipBounds().bottom - 2f;
        if (width <= 0) return;

        Path path = new Path();
        path.moveTo(0, bottom);
        float step = 3f;
        boolean up = true;
        for (float x = 0; x < width; x += step) {
            float next = Math.min(x + step, width);
            path.quadTo(x + step / 2f, bottom + (up ? -2.5f : 2.5f), next, bottom);
            up = !up;
        }
        canvas.drawPath(path, wavePaint);
    }
}
