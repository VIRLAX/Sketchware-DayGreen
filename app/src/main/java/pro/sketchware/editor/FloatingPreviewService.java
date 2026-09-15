package pro.sketchware.editor;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.google.android.material.card.MaterialCardView;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import pro.sketchware.R;
import pro.sketchware.activities.preview.PreviewLog;
import pro.sketchware.activities.preview.RuntimeXmlPreview;

/** Floating native XML preview. It stays small and docked so the code editor remains usable. */
public class FloatingPreviewService extends Service {
    public static final String ACTION_SHOW = "pro.sketchware.action.SHOW_FLOATING_PREVIEW";
    public static final String ACTION_STOP = "pro.sketchware.action.STOP_FLOATING_PREVIEW";
    public static final String EXTRA_XML = "xml";
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_SC_ID = "sc_id";
    private static final String CHANNEL_ID = "floating_preview";
    private static final String PREFS = "floating_preview";
    private static final int MIN_WIDTH = 220;
    private static final int MIN_HEIGHT = 320;
    private static final long RENDER_DEBOUNCE_MS = 140L;
    private static volatile boolean running;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService renderExecutor = Executors.newSingleThreadExecutor();
    private final AtomicInteger renderGeneration = new AtomicInteger();
    private WindowManager windowManager;
    private MaterialCardView previewWindow;
    private View floatingIcon;
    private WindowManager.LayoutParams previewParams;
    private WindowManager.LayoutParams iconParams;
    private String latestXml = "";
    private String latestTitle = "Layout Preview";
    private String latestScId;
    private int screenWidth;
    private int screenHeight;
    private final Runnable debouncedRender = () -> requestRenderNow(latestXml);

    public static boolean isRunning() { return running; }

    public static void showOrUpdate(Context context, String xml, String title, String scId) {
        Intent intent = new Intent(context, FloatingPreviewService.class).setAction(ACTION_SHOW)
                .putExtra(EXTRA_XML, xml).putExtra(EXTRA_TITLE, title).putExtra(EXTRA_SC_ID, scId);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent); else context.startService(intent);
    }

    public static void stop(Context context) { context.stopService(new Intent(context, FloatingPreviewService.class)); }

    public static void updatePreview(Context context, String xml, String title, String scId) {
        if (running) showOrUpdate(context, xml, title, scId);
    }

    @Override public void onCreate() {
        super.onCreate();
        running = true;
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        android.util.DisplayMetrics metrics = new android.util.DisplayMetrics();
        windowManager.getDefaultDisplay().getRealMetrics(metrics);
        screenWidth = metrics.widthPixels;
        screenHeight = metrics.heightPixels;
        createNotificationChannel();
        startForeground(2301, buildNotification());
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) { stopSelf(); return START_NOT_STICKY; }
        if (intent != null && ACTION_SHOW.equals(intent.getAction())) {
            String xml = intent.getStringExtra(EXTRA_XML);
            latestXml = xml == null ? "" : xml;
            latestTitle = intent.getStringExtra(EXTRA_TITLE);
            if (latestTitle == null) latestTitle = "Layout Preview";
            latestScId = intent.getStringExtra(EXTRA_SC_ID);
            RuntimeXmlPreview.setProjectId(latestScId);
            if (previewWindow == null && floatingIcon == null) showIcon();
            mainHandler.removeCallbacks(debouncedRender);
            mainHandler.postDelayed(debouncedRender, RENDER_DEBOUNCE_MS);
        }
        return START_STICKY;
    }

    private Context themedContext() { return new ContextThemeWrapper(this, R.style.Theme_SketchwarePro); }

    private void showIcon() {
        if (floatingIcon != null || !Settings.canDrawOverlays(this)) return;
        Context context = themedContext();
        floatingIcon = LayoutInflater.from(context).inflate(R.layout.floating_icon_layout, null);
        iconParams = baseParams(dp(56), dp(56));
        iconParams.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        iconParams.gravity = Gravity.TOP | Gravity.END;
        iconParams.x = -dp(14);
        iconParams.y = Math.max(dp(90), Math.min(getPrefs().getInt("icon_y", dp(120)), screenHeight - dp(100)));
        floatingIcon.setOnClickListener(v -> showPreview());
        floatingIcon.setOnTouchListener(new DragTouch(iconParams));
        windowManager.addView(floatingIcon, iconParams);
    }

    private void showPreview() {
        if (!Settings.canDrawOverlays(this)) return;
        if (floatingIcon != null) { windowManager.removeView(floatingIcon); floatingIcon = null; }
        Context context = themedContext();
        previewWindow = (MaterialCardView) LayoutInflater.from(context).inflate(R.layout.floating_preview_layout, null);
        TextView title = previewWindow.findViewById(R.id.preview_title);
        title.setText(latestTitle);
        View header = previewWindow.findViewById(R.id.preview_header);
        View close = previewWindow.findViewById(R.id.preview_close);
        View resize = previewWindow.findViewById(R.id.preview_resize);
        header.setOnTouchListener(new PreviewDragTouch());
        close.setOnClickListener(v -> minimize());
        resize.setOnTouchListener(new ResizeTouch());
        int width = clamp(getPrefs().getInt("width", dp(292)), dp(240), Math.max(dp(240), screenWidth - dp(20)));
        int height = clamp(getPrefs().getInt("height", dp(440)), dp(360), Math.max(dp(360), screenHeight - dp(70)));
        previewParams = baseParams(width, height);
        // The preview remains clickable, but cannot steal keyboard focus from the XML editor.
        previewParams.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        previewParams.gravity = Gravity.TOP | Gravity.START;
        boolean hasSavedPosition = getPrefs().contains("x") && getPrefs().contains("y");
        previewParams.x = hasSavedPosition ? clamp(getPrefs().getInt("x", 0), 0, Math.max(0, screenWidth - width)) : Math.max(dp(8), screenWidth - width - dp(12));
        previewParams.y = hasSavedPosition ? clamp(getPrefs().getInt("y", dp(64)), dp(24), Math.max(dp(24), screenHeight - height)) : dp(72);
        previewParams.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN;
        windowManager.addView(previewWindow, previewParams);
        PreviewLog.setListener(lines -> mainHandler.post(() -> updateLog(lines)));
        mainHandler.removeCallbacks(debouncedRender);
        mainHandler.post(debouncedRender);
    }

    private void minimize() {
        PreviewLog.setListener(null);
        if (previewWindow != null) { saveBounds(); windowManager.removeView(previewWindow); previewWindow = null; }
        showIcon();
    }

    private void requestRenderNow(String xml) {
        if (xml == null || latestTitle == null) return;
        final int generation = renderGeneration.incrementAndGet();
        final String requestedXml = xml;
        final String projectId = latestScId;
        renderExecutor.execute(() -> {
            RuntimeXmlPreview.setProjectId(projectId);
            RuntimeXmlPreview.ParseResult parsed = RuntimeXmlPreview.parse(requestedXml);
            mainHandler.post(() -> {
                if (generation != renderGeneration.get() || previewWindow == null) return;
                if (!parsed.isSuccess()) {
                    showRenderError("XML error at line " + parsed.line + ", column " + parsed.column + ": " + parsed.error);
                    return;
                }
                RuntimeXmlPreview.RenderResult rendered = RuntimeXmlPreview.render(themedContext(), parsed.root);
                if (!rendered.isSuccess()) { showRenderError("Render error: " + rendered.error); return; }
                renderNative(rendered.root, parsed.root, rendered.warnings);
            });
        });
    }

    private void renderNative(View root, RuntimeXmlPreview.Node node, List<String> warnings) {
        FrameLayout content = previewWindow.findViewById(R.id.preview_content);
        content.removeAllViews();
        content.addView(root, rootLayoutParams(node));
        PreviewLog.log("LIVE render complete" + (warnings.isEmpty() ? "" : ", " + warnings.size() + " warning(s)"));
        updateLog(PreviewLog.snapshot());
    }

    private FrameLayout.LayoutParams rootLayoutParams(RuntimeXmlPreview.Node node) {
        int width = "match_parent".equals(node.attributes.get("android:layout_width")) ? -1 : -2;
        int height = "match_parent".equals(node.attributes.get("android:layout_height")) ? -1 : -2;
        return new FrameLayout.LayoutParams(width, height, Gravity.CENTER_HORIZONTAL);
    }

    private void showRenderError(String message) {
        PreviewLog.log(message);
        TextView status = previewWindow == null ? null : previewWindow.findViewById(R.id.preview_status);
        if (status != null) { status.setText("CHECK XML"); status.setTextColor(0xFFB42318); }
        updateLog(PreviewLog.snapshot());
    }

    private void updateLog(List<String> lines) {
        if (previewWindow == null) return;
        TextView status = previewWindow.findViewById(R.id.preview_status);
        TextView log = previewWindow.findViewById(R.id.preview_log);
        if (status != null) { status.setText("LIVE  •  native interactive"); status.setTextColor(0xFF64748B); }
        if (log != null) {
            int start = Math.max(0, lines.size() - 4);
            log.setText(android.text.TextUtils.join("\n", lines.subList(start, lines.size())));
        }
    }

    private WindowManager.LayoutParams baseParams(int width, int height) {
        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE;
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(width, height, type,
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;
        params.gravity = Gravity.TOP | Gravity.START;
        return params;
    }

    private class PreviewDragTouch implements View.OnTouchListener {
        private float downX, downY; private int startX, startY;
        @Override public boolean onTouch(View v, MotionEvent event) {
            if (previewParams == null || previewWindow == null) return false;
            if (event.getAction() == MotionEvent.ACTION_DOWN) { downX = event.getRawX(); downY = event.getRawY(); startX = previewParams.x; startY = previewParams.y; return true; }
            if (event.getAction() == MotionEvent.ACTION_MOVE) { previewParams.x = clamp(startX + (int) (event.getRawX() - downX), 0, Math.max(0, screenWidth - previewParams.width)); previewParams.y = clamp(startY + (int) (event.getRawY() - downY), dp(24), Math.max(dp(24), screenHeight - previewParams.height)); windowManager.updateViewLayout(previewWindow, previewParams); return true; }
            if (event.getAction() == MotionEvent.ACTION_UP) { if (event.getRawX() - downX < -dp(96)) minimize(); else saveBounds(); return true; }
            return true;
        }
    }

    private class ResizeTouch implements View.OnTouchListener {
        private float downX, downY; private int startWidth;
        @Override public boolean onTouch(View v, MotionEvent event) {
            if (previewParams == null || previewWindow == null) return false;
            if (event.getAction() == MotionEvent.ACTION_DOWN) { downX = event.getRawX(); downY = event.getRawY(); startWidth = previewParams.width; return true; }
            if (event.getAction() == MotionEvent.ACTION_MOVE) {
                int width = Math.max(MIN_WIDTH, startWidth + (int) (event.getRawX() - downX));
                int height = Math.max(MIN_HEIGHT, (int) (width * 1.52f));
                previewParams.width = Math.min(width, screenWidth - dp(12));
                previewParams.height = Math.min(height, screenHeight - dp(48));
                windowManager.updateViewLayout(previewWindow, previewParams);
                return true;
            }
            if (event.getAction() == MotionEvent.ACTION_UP) { saveBounds(); return true; }
            return true;
        }
    }

    private class DragTouch implements View.OnTouchListener {
        private final WindowManager.LayoutParams params;
        private final int touchSlop = ViewConfiguration.get(FloatingPreviewService.this).getScaledTouchSlop();
        private float downX, downY; private int startX, startY; private boolean moved;
        DragTouch(WindowManager.LayoutParams params) { this.params = params; }
        @Override public boolean onTouch(View v, MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_DOWN) { downX = event.getRawX(); downY = event.getRawY(); startX = params.x; startY = params.y; moved = false; return true; }
            if (event.getAction() == MotionEvent.ACTION_MOVE) { float dx = event.getRawX() - downX, dy = event.getRawY() - downY; if (Math.abs(dx) > touchSlop || Math.abs(dy) > touchSlop) moved = true; params.x = startX + (int) dx; params.y = startY + (int) dy; if (floatingIcon != null) windowManager.updateViewLayout(floatingIcon, params); return true; }
            if (event.getAction() == MotionEvent.ACTION_UP) { if (moved) getPrefs().edit().putInt("icon_y", params.y).apply(); else v.performClick(); return true; }
            return true;
        }
    }

    private android.content.SharedPreferences getPrefs() { return getSharedPreferences(PREFS, MODE_PRIVATE); }
    private void saveBounds() { if (previewParams != null) getPrefs().edit().putInt("width", previewParams.width).putInt("height", previewParams.height).putInt("x", previewParams.x).putInt("y", previewParams.y).apply(); }
    private int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + .5f); }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Floating XML Preview", NotificationManager.IMPORTANCE_LOW);
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(channel);
        }
    }

    private Notification buildNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID).setSmallIcon(R.drawable.ic_floating_preview)
                .setContentTitle("Floating XML Preview").setContentText("Preview aktif, editor tetap bisa dipakai").setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW).build();
    }

    @Override public void onTaskRemoved(Intent rootIntent) { stopSelf(); super.onTaskRemoved(rootIntent); }

    @Override public void onDestroy() {
        mainHandler.removeCallbacksAndMessages(null);
        PreviewLog.setListener(null);
        if (previewWindow != null) windowManager.removeView(previewWindow);
        if (floatingIcon != null) windowManager.removeView(floatingIcon);
        previewWindow = null; floatingIcon = null; running = false; renderExecutor.shutdownNow(); super.onDestroy();
    }

    @Nullable @Override public IBinder onBind(Intent intent) { return null; }
}
