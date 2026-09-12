package pro.sketchware.editor;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
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

import com.besome.sketch.beans.ViewBean;
import com.besome.sketch.editor.view.ViewPane;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import a.a.a.jC;
import pro.sketchware.R;
import pro.sketchware.tools.ViewBeanParser;

public class FloatingPreviewService extends Service {
    public static final String ACTION_SHOW = "pro.sketchware.action.SHOW_FLOATING_PREVIEW";
    public static final String ACTION_STOP = "pro.sketchware.action.STOP_FLOATING_PREVIEW";
    public static final String EXTRA_XML = "xml";
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_SC_ID = "sc_id";
    private static final String CHANNEL_ID = "floating_preview";
    private static final String PREFS = "floating_preview";
    private static final int MIN_WIDTH = 220;
    private static final int MIN_HEIGHT = 180;
    private static volatile boolean running;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService renderExecutor = Executors.newSingleThreadExecutor();
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

    public static boolean isRunning() { return running; }

    public static void showOrUpdate(Context context, String xml, String title, String scId) {
        Intent intent = new Intent(context, FloatingPreviewService.class)
                .setAction(ACTION_SHOW)
                .putExtra(EXTRA_XML, xml)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_SC_ID, scId);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent);
        else context.startService(intent);
    }

    public static void stop(Context context) {
        context.stopService(new Intent(context, FloatingPreviewService.class));
    }

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
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (intent != null && ACTION_SHOW.equals(intent.getAction())) {
            latestXml = intent.getStringExtra(EXTRA_XML);
            latestTitle = intent.getStringExtra(EXTRA_TITLE);
            latestScId = intent.getStringExtra(EXTRA_SC_ID);
            if (previewWindow == null && floatingIcon == null) showIcon();
            requestRender(latestXml);
        }
        return START_STICKY;
    }

    private Context themedContext() {
        return new ContextThemeWrapper(this, R.style.Theme_SketchwarePro);
    }

    private void showIcon() {
        if (floatingIcon != null || !Settings.canDrawOverlays(this)) return;
        Context context = themedContext();
        floatingIcon = LayoutInflater.from(context).inflate(R.layout.floating_icon_layout, null);
        iconParams = baseParams(dp(64), dp(64));
        iconParams.gravity = Gravity.TOP | Gravity.END;
        iconParams.x = dp(16);
        iconParams.y = dp(120);
        floatingIcon.setOnClickListener(v -> showPreview());
        floatingIcon.setOnTouchListener(new DragTouch(iconParams));
        windowManager.addView(floatingIcon, iconParams);
    }

    private void showPreview() {
        if (!Settings.canDrawOverlays(this)) return;
        if (floatingIcon != null) {
            windowManager.removeView(floatingIcon);
            floatingIcon = null;
        }
        Context context = themedContext();
        previewWindow = (MaterialCardView) LayoutInflater.from(context)
                .inflate(R.layout.floating_preview_layout, null);
        TextView title = previewWindow.findViewById(R.id.preview_title);
        title.setText(latestTitle == null ? "Layout Preview" : latestTitle);
        View header = previewWindow.findViewById(R.id.preview_header);
        View close = previewWindow.findViewById(R.id.preview_close);
        View resize = previewWindow.findViewById(R.id.preview_resize);
        header.setOnTouchListener(new PreviewDragTouch());
        close.setOnClickListener(v -> minimize());
        resize.setOnTouchListener(new ResizeTouch());
        int width = getPrefs().getInt("width", Math.min(dp(360), screenWidth - dp(24)));
        int height = getPrefs().getInt("height", Math.min(dp(520), screenHeight - dp(180)));
        previewParams = baseParams(Math.max(MIN_WIDTH, width), Math.max(MIN_HEIGHT, height));
        previewParams.gravity = Gravity.TOP | Gravity.START;
        previewParams.x = getPrefs().getInt("x", Math.max(dp(8), (screenWidth - width) / 2));
        previewParams.y = getPrefs().getInt("y", dp(96));
        windowManager.addView(previewWindow, previewParams);
        requestRender(latestXml);
    }

    private void minimize() {
        if (previewWindow != null) {
            saveBounds();
            windowManager.removeView(previewWindow);
            previewWindow = null;
        }
        showIcon();
    }

    private void snapToCenter() {
        if (previewParams == null || previewWindow == null) return;
        previewParams.x = Math.max(0, (screenWidth - previewParams.width) / 2);
        previewParams.y = Math.max(dp(48), (screenHeight - previewParams.height) / 2);
        windowManager.updateViewLayout(previewWindow, previewParams);
        saveBounds();
    }

    private void requestRender(String xml) {
        if (xml == null || latestScId == null || latestTitle == null) return;
        final String requestedXml = xml;
        final String requestedTitle = latestTitle;
        final String requestedScId = latestScId;
        renderExecutor.execute(() -> {
            try {
                ArrayList<ViewBean> beans = new ViewBeanParser(requestedXml).parse();
                mainHandler.post(() -> renderBeans(beans, requestedTitle, requestedScId));
            } catch (Exception e) {
                mainHandler.post(() -> showRenderError(e.getMessage()));
            }
        });
    }

    private void renderBeans(ArrayList<ViewBean> beans, String title, String scId) {
        if (previewWindow == null) return;
        try {
            ViewPane pane = new ViewPane(this);
            pane.initialize(scId, true);
            pane.setResourceManager(jC.d(scId));
            pane.updateRootLayout(scId, title);
            for (ViewBean bean : beans) {
                if (beans.indexOf(bean) == 0) {
                    bean.parent = "root";
                    bean.parentType = 0;
                    bean.preParent = null;
                    bean.preParentType = -1;
                }
                View item = pane.createItemView(bean);
                pane.addViewAndUpdateIndex(item);
            }
            FrameLayout content = previewWindow.findViewById(R.id.preview_content);
            content.removeAllViews();
            content.addView(pane, new FrameLayout.LayoutParams(-1, -1));
        } catch (Exception e) {
            showRenderError(e.getMessage());
        }
    }

    private void showRenderError(String message) {
        if (previewWindow == null) return;
        FrameLayout content = previewWindow.findViewById(R.id.preview_content);
        content.removeAllViews();
        TextView error = new TextView(this);
        error.setText("Preview belum bisa dirender\n" + (message == null ? "Periksa XML dan resource layout." : message));
        error.setTextColor(Color.WHITE);
        error.setPadding(dp(16), dp(16), dp(16), dp(16));
        content.addView(error, new FrameLayout.LayoutParams(-1, -1));
    }

    private WindowManager.LayoutParams baseParams(int width, int height) {
        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE;
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(width, height, type,
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        return params;
    }

    private class PreviewDragTouch implements View.OnTouchListener {
        private float downX, downY;
        private int startX, startY;
        @Override public boolean onTouch(View v, MotionEvent event) {
            if (previewParams == null || previewWindow == null) return false;
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                downX = event.getRawX(); downY = event.getRawY();
                startX = previewParams.x; startY = previewParams.y; return true;
            }
            if (event.getAction() == MotionEvent.ACTION_MOVE) {
                previewParams.x = startX + (int) (event.getRawX() - downX);
                previewParams.y = startY + (int) (event.getRawY() - downY);
                windowManager.updateViewLayout(previewWindow, previewParams); return true;
            }
            if (event.getAction() == MotionEvent.ACTION_UP) {
                if (event.getRawX() - downX > dp(96)) minimize(); else snapToCenter();
                return true;
            }
            return true;
        }
    }

    private class ResizeTouch implements View.OnTouchListener {
        private float downX, downY;
        private int startWidth, startHeight;
        @Override public boolean onTouch(View v, MotionEvent event) {
            if (previewParams == null || previewWindow == null) return false;
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                downX = event.getRawX(); downY = event.getRawY();
                startWidth = previewParams.width; startHeight = previewParams.height; return true;
            }
            if (event.getAction() == MotionEvent.ACTION_MOVE) {
                previewParams.width = Math.max(MIN_WIDTH, startWidth + (int) (event.getRawX() - downX));
                previewParams.height = Math.max(MIN_HEIGHT, startHeight + (int) (event.getRawY() - downY));
                windowManager.updateViewLayout(previewWindow, previewParams); return true;
            }
            if (event.getAction() == MotionEvent.ACTION_UP) { saveBounds(); return true; }
            return true;
        }
    }

    private class DragTouch implements View.OnTouchListener {
        private final WindowManager.LayoutParams params;
        private final int touchSlop = ViewConfiguration.get(FloatingPreviewService.this).getScaledTouchSlop();
        private float downX, downY;
        private int startX, startY;
        private boolean moved;
        DragTouch(WindowManager.LayoutParams params) { this.params = params; }
        @Override public boolean onTouch(View v, MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                downX = event.getRawX(); downY = event.getRawY();
                startX = params.x; startY = params.y; moved = false; return true;
            }
            if (event.getAction() == MotionEvent.ACTION_MOVE) {
                float dx = event.getRawX() - downX;
                float dy = event.getRawY() - downY;
                if (Math.abs(dx) > touchSlop || Math.abs(dy) > touchSlop) moved = true;
                params.x = startX + (int) dx; params.y = startY + (int) dy;
                if (floatingIcon != null) windowManager.updateViewLayout(floatingIcon, params);
                return true;
            }
            if (event.getAction() == MotionEvent.ACTION_UP) {
                if (!moved) v.performClick();
                return true;
            }
            return true;
        }
    }

    private android.content.SharedPreferences getPrefs() { return getSharedPreferences(PREFS, MODE_PRIVATE); }
    private void saveBounds() {
        if (previewParams != null) getPrefs().edit().putInt("width", previewParams.width)
                .putInt("height", previewParams.height).putInt("x", previewParams.x)
                .putInt("y", previewParams.y).apply();
    }
    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + .5f); }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Floating XML Preview", NotificationManager.IMPORTANCE_LOW);
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(channel);
        }
    }

    private Notification buildNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_floating_preview)
                .setContentTitle("Floating XML Preview")
                .setContentText("Preview aktif di atas editor")
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    @Override public void onDestroy() {
        if (previewWindow != null) windowManager.removeView(previewWindow);
        if (floatingIcon != null) windowManager.removeView(floatingIcon);
        previewWindow = null; floatingIcon = null; running = false;
        renderExecutor.shutdownNow();
        super.onDestroy();
    }

    @Nullable @Override public IBinder onBind(Intent intent) { return null; }
}
