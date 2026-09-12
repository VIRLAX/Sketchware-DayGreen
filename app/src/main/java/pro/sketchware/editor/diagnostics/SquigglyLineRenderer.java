package pro.sketchware.editor.diagnostics;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;

import io.github.rosemoe.sora.lang.styling.span.SpanExternalRenderer;
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme;

/** Draws the compact IDE-style wave under the exact diagnostic span. */
public final class SquigglyLineRenderer implements SpanExternalRenderer {
    public static final int SEVERITY_ERROR = 0;
    public static final int SEVERITY_WARNING = 1;
    public static final int SEVERITY_INFO = 2;

    private final Paint wavePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public SquigglyLineRenderer(int severity, String message) {
        wavePaint.setStyle(Paint.Style.STROKE);
        wavePaint.setStrokeWidth(1.6f);
        wavePaint.setStrokeCap(Paint.Cap.ROUND);
        wavePaint.setColor(severity == SEVERITY_ERROR
                ? 0xFFE53935
                : severity == SEVERITY_WARNING ? 0xFFFFA726 : 0xFF42A5F5);
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
    public void draw(Canvas canvas, Paint paint, EditorColorScheme scheme, boolean post) {
        if (!post || canvas == null) return;
        float left = canvas.getClipBounds().left;
        float right = canvas.getClipBounds().right;
        float bottom = canvas.getClipBounds().bottom - 2f;
        if (right <= left) return;

        Path path = new Path();
        path.moveTo(left, bottom);
        boolean up = true;
        for (float x = left; x < right; x += 4f) {
            float next = Math.min(x + 4f, right);
            path.quadTo(x + 2f, bottom + (up ? -2.3f : 2.3f), next, bottom);
            up = !up;
        }
        canvas.drawPath(path, wavePaint);
    }
}
