package pro.sketchware.editor.diagnostics;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import io.github.rosemoe.sora.lang.styling.span.SpanExternalRenderer;
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme;

public final class SquigglyLineRenderer implements SpanExternalRenderer {
    public static final int SEVERITY_ERROR = 0, SEVERITY_WARNING = 1, SEVERITY_INFO = 2;
    private final Paint wavePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    public SquigglyLineRenderer(int severity, String message) { wavePaint.setStyle(Paint.Style.STROKE); wavePaint.setStrokeWidth(1.5f); wavePaint.setStrokeCap(Paint.Cap.ROUND); wavePaint.setColor(severity == 0 ? 0xFFE53935 : severity == 1 ? 0xFFFFA726 : 0xFF42A5F5); }
    @Override public boolean requirePreDraw() { return false; }
    @Override public boolean requirePostDraw() { return true; }
    @Override public void draw(Canvas canvas, Paint paint, EditorColorScheme scheme, boolean post) { if (!post || canvas == null) return; float width = canvas.getClipBounds().width(), bottom = canvas.getClipBounds().bottom - 2f; Path path = new Path(); path.moveTo(0, bottom); boolean up = true; for (float x=0; x<width; x+=3f) { float next=Math.min(x+3f,width); path.quadTo(x+1.5f,bottom+(up?-2.5f:2.5f),next,bottom); up=!up; } canvas.drawPath(path,wavePaint); }
}
