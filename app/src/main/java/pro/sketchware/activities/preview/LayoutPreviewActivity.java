package pro.sketchware.activities.preview;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;

import com.besome.sketch.lib.base.BaseAppCompatActivity;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import pro.sketchware.R;
import pro.sketchware.databinding.ActivityLayoutPreviewBinding;
import pro.sketchware.utility.SketchwareUtil;
import pro.sketchware.utility.UI;

public class LayoutPreviewActivity extends BaseAppCompatActivity {

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService parserExecutor = Executors.newSingleThreadExecutor();
    private final AtomicInteger renderGeneration = new AtomicInteger();
    private ActivityLayoutPreviewBinding binding;
    private String source;
    private String liveKey;
    private boolean active;
    private RuntimeXmlPreview.Node lastValidNode;

    private final Runnable livePoll = new Runnable() {
        @Override
        public void run() {
            if (!active) return;
            String latest = liveKey == null ? null : getSharedPreferences("xml_preview_live", MODE_PRIVATE).getString(liveKey, null);
            if (latest != null && !latest.equals(source)) {
                source = latest;
                scheduleRender(source);
            }
            mainHandler.postDelayed(this, 400);
        }
    };

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);
        binding = ActivityLayoutPreviewBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Interactive XML Preview");
            getSupportActionBar().setSubtitle(getIntent().getStringExtra("title"));
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        binding.toolbar.setNavigationOnClickListener(v -> finish());
        source = getIntent().getStringExtra("xml");
        liveKey = getIntent().getStringExtra("live_key");
        binding.previewStatus.setText("LIVE  •  native Android views");
        UI.addSystemWindowInsetToPadding(binding.previewCard, false, false, false, true);
        scheduleRender(source);
    }

    private void scheduleRender(String xml) {
        final int generation = renderGeneration.incrementAndGet();
        parserExecutor.execute(() -> {
            RuntimeXmlPreview.ParseResult parsed = RuntimeXmlPreview.parse(xml);
            if (generation != renderGeneration.get()) return;
            mainHandler.post(() -> {
                if (generation != renderGeneration.get()) return;
                if (!parsed.isSuccess()) {
                    showError("XML Error\nLine " + parsed.line + ", column " + parsed.column + ": " + parsed.error);
                    return;
                }
                RuntimeXmlPreview.RenderResult rendered = RuntimeXmlPreview.render(this, parsed.root);
                if (!rendered.isSuccess()) {
                    showError("Preview Error\n" + rendered.error);
                    return;
                }
                lastValidNode = parsed.root;
                showPreview(rendered.root, rendered.warnings);
            });
        });
    }

    private void showPreview(View view, java.util.List<String> warnings) {
        binding.previewContainer.removeAllViews();
        binding.previewContainer.addView(view, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        if (warnings.isEmpty()) {
            binding.previewError.setVisibility(View.GONE);
        } else {
            binding.previewError.setVisibility(View.VISIBLE);
            binding.previewError.setText("Warnings\n• " + android.text.TextUtils.join("\n• ", warnings));
        }
    }

    private void showError(String message) {
        binding.previewError.setVisibility(View.VISIBLE);
        binding.previewError.setText(message + "\nPrevious valid preview is kept.");
        if (lastValidNode == null) binding.previewContainer.removeAllViews();
    }

    @Override
    protected void onResume() {
        super.onResume();
        active = true;
        if (liveKey != null) mainHandler.post(livePoll);
    }

    @Override
    protected void onPause() {
        active = false;
        mainHandler.removeCallbacks(livePoll);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        active = false;
        mainHandler.removeCallbacksAndMessages(null);
        parserExecutor.shutdownNow();
        super.onDestroy();
    }
}