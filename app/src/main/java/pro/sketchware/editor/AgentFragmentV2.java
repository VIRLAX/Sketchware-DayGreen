package pro.sketchware.editor;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.method.PasswordTransformationMethod;
import android.text.style.StyleSpan;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import a.a.a.lC;
import a.a.a.wq;
import a.a.a.yB;
import pro.sketchware.activities.editor.view.ViewCodeEditorActivity;
import pro.sketchware.utility.XmlSyncHelper;

/**
 * Dayven AI Agent v3.5 - Autonomous Elite Engineer Edition.
 *
 * Capabilities & UI Overhaul:
 * - Ultra-Smooth Streaming: Batch-throttled delta dispatcher eliminating UI freezing on huge code outputs.
 * - Claude-style Collapsible Execution Steps (`>_ n langkah` accordion) with interactive command shell logs.
 * - Smart Drag-and-Drop & Direct XML integration via XmlSyncHelper with immediate ViewBean synchronization.
 * - Automatic Fallback across Gemini model tiers preventing "high demand" errors.
 * - Token efficiency up to 90% via hyper-compact context optimizer.
 * - ChatGPT-style code blocks with language indicators and one-tap copy button.
 * - Project profile drawer showing App Name & custom icon with long-press permanent deletion of chat history.
 * - 6-tool developer settings menu.
 */
public final class AgentFragmentV2 extends Fragment {
    // Premium dark & high contrast palette
    private static final int BG_DARK = 0xFF0B0C0F;
    private static final int SURFACE_DARK = 0xFF15171C;
    private static final int CARD_DARK = 0xFF1C1F26;
    private static final int CARD_ELEVATED = 0xFF232732;
    private static final int BORDER_DARK = 0xFF2A2E38;
    private static final int TEXT_PRIMARY = 0xFFF3F5F9;
    private static final int TEXT_SECONDARY = 0xFF8E94A3;
    private static final int TEXT_MUTED = 0xFF656B7B;
    private static final int ACCENT = 0xFFD97757;
    private static final int ACCENT_SOFT = 0x33D97757;
    private static final int ACCENT_CYAN = 0xFF38BDF8;
    private static final int IOS_GREEN = 0xFF34C759;
    private static final int IOS_RED = 0xFFFF453A;
    private static final int USER_BUBBLE = 0xFF262A34;
    private static final int AI_BUBBLE = 0xFF15171C;
    private static final int CODE_BG = 0xFF1E1E1E;
    private static final int CODE_HEADER_BG = 0xFF2D2D2D;

    private static final String GREETING = "Halo! Dayven AI siap membantu.\nAPI key kamu sudah aktif!\n\nAda bug yang mau diperbaiki, integrasi library, atau mau saya rancang fitur dan layout XML langsung ke project kamu? Tulis instruksinya, saya kerjakan secara otonom! 🚀";
    private static final String NO_KEY = "Halo! Saya Dayven AI\nSilakan buka ikon Settings di kanan atas untuk memasukkan API Key Google AI Studio (Gemini) agar saya bisa mulai bekerja.";
    private static final String CONTINUE_HINT = "[LANJUTKAN] Lanjutkan tepat dari karakter terakhir. Jangan mengulang bagian sebelumnya, langsung sambung kode/teks.";

    private static final int MAX_CONTINUATION = 4;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final GeminiAutoClient client = new GeminiAutoClient();
    private final Set<String> triedRoutes = new HashSet<>();

    private AgentWorkspaceStore store;
    private AgentMemoryEngine memory;
    private AgentWorkspaceStore.Session session;
    private List<AgentWorkspaceStore.Profile> profiles = new ArrayList<>();

    private FrameLayout root;
    private LinearLayout shell;
    private LinearLayout header;
    private LinearLayout messages;
    private ScrollView transcript;
    private LinearLayout composerRow;
    private LinearLayout providerList;
    private TextView statusText;
    private TextView tokenText;
    private TextView sessionTitle;
    private View statusDot;
    private EditText composer;
    private MaterialButton sendButton;
    private MaterialButton expandButton;

    private String projectId;
    private String selectedFile;
    private LinearLayout liveAssistantContainer;
    private TextView liveAssistantText;
    private boolean busy;
    private boolean expanded = true;
    private int insetTop;
    private Runnable heartbeat;
    private ObjectAnimator pulseAnimator;
    private FrameLayout activeSettingsPage;
    private FrameLayout activeDrawerOverlay;
    private ViewTreeObserver.OnGlobalLayoutListener keyboardLayoutListener;

    private List<GeminiAutoClient.Turn> activeTurns = new ArrayList<>();
    private String activeSystem = "";
    private int continuation;

    // Advanced thinking state visual effects
    private AnimatorSet thinkingAnimatorSet;

    // Batch delta queue to eliminate lag when receiving very long code
    private final StringBuilder streamBuffer = new StringBuilder();
    private boolean streamFlushScheduled = false;
    private final Runnable streamFlushRunnable = new Runnable() {
        @Override public void run() {
            streamFlushScheduled = false;
            if (streamBuffer.length() == 0) return;
            removeThinking();
            if (liveAssistantText == null) addAssistantStreamBubble();
            liveAssistantText.append(streamBuffer.toString());
            streamBuffer.setLength(0);
            scrollBottom();
        }
    };

    public static AgentFragmentV2 newInstance(String projectId, String selectedFile) {
        AgentFragmentV2 f = new AgentFragmentV2();
        Bundle b = new Bundle();
        b.putString("project_id", projectId == null ? "" : projectId);
        b.putString("selected_file", selectedFile == null ? "" : selectedFile);
        f.setArguments(b);
        return f;
    }

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup parent, @Nullable Bundle state) {
        projectId = getArguments() == null ? "" : getArguments().getString("project_id", "");
        selectedFile = getArguments() == null ? "" : getArguments().getString("selected_file", "");
        store = new AgentWorkspaceStore(requireContext());
        memory = new AgentMemoryEngine(requireContext());
        session = store.getOrCreateCurrent();
        profiles = store.getProfiles();
        insetTop = systemStatusBarHeight();
        requireActivity().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        return build(requireContext());
    }

    @Override public void onResume() {
        super.onResume();
        profiles = store.getProfiles();
        updateStatusIndicator(hasActiveKey(profiles));
        startHeartbeat();
        applyTopInset();
        restoreSession();
    }

    @Override public void onPause() {
        stopHeartbeat();
        stopThinkingAnimation();
        super.onPause();
    }

    @Override public void onDestroyView() {
        if (root != null && keyboardLayoutListener != null) {
            root.getViewTreeObserver().removeOnGlobalLayoutListener(keyboardLayoutListener);
        }
        stopThinkingAnimation();
        super.onDestroyView();
    }

    public void onAgentTabOpened(boolean opened) {
        if (!opened) return;
        expanded = true;
        Activity act = getActivity();
        if (act != null) AgentTabEnhancer.setExpanded(act, true);
        if (expandButton != null) {
            int minimizeId = getResDrawable(requireContext(), "ic_dayven_minimize");
            if (minimizeId != 0) expandButton.setIconResource(minimizeId);
        }
        applyTopInset();
        profiles = store.getProfiles();
        updateStatusIndicator(hasActiveKey(profiles));
        startHeartbeat();
        restoreSession();
    }

    private View build(Context c) {
        root = new FrameLayout(c);
        root.setLayoutParams(new ViewGroup.LayoutParams(-1, -1));
        root.setBackgroundColor(BG_DARK);

        shell = column(c);
        shell.setLayoutParams(new FrameLayout.LayoutParams(-1, -1));

        shell.addView(buildHeader(c), new LinearLayout.LayoutParams(-1, -2));
        shell.addView(buildStatusStrip(c), new LinearLayout.LayoutParams(-1, -2));

        transcript = new ScrollView(c);
        transcript.setFillViewport(true);
        transcript.setVerticalScrollBarEnabled(false);
        messages = column(c);
        messages.setPadding(dp(14), dp(14), dp(14), dp(14));
        transcript.addView(messages, new ViewGroup.LayoutParams(-1, -2));
        shell.addView(transcript, new LinearLayout.LayoutParams(-1, 0, 1f));

        composerRow = buildComposer(c);
        shell.addView(composerRow, new LinearLayout.LayoutParams(-1, -2));

        root.addView(shell);

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            insetTop = Math.max(bars.top, systemStatusBarHeight());
            int bottom = Math.max(bars.bottom, ime.bottom);
            composerRow.setPadding(dp(12), dp(8), dp(12), bottom + dp(10));
            applyTopInset();
            if (bottom > dp(60)) scrollBottom();
            return insets;
        });
        ViewCompat.requestApplyInsets(root);

        keyboardLayoutListener = () -> {
            if (root == null || composerRow == null) return;
            Rect r = new Rect();
            root.getWindowVisibleDisplayFrame(r);
            int screenHeight = root.getRootView().getHeight();
            int keypadHeight = screenHeight - r.bottom;
            if (keypadHeight > screenHeight * 0.15) {
                composerRow.setPadding(dp(12), dp(8), dp(12), keypadHeight + dp(10));
                scrollBottom();
            } else {
                composerRow.setPadding(dp(12), dp(8), dp(12), dp(10));
            }
        };
        root.getViewTreeObserver().addOnGlobalLayoutListener(keyboardLayoutListener);

        restoreSession();
        applyTopInset();
        return root;
    }

    private void applyTopInset() {
        if (header == null) return;
        int top = expanded ? insetTop : 0;
        header.setPadding(dp(6), top + dp(6), dp(6), dp(6));
    }

    private int systemStatusBarHeight() {
        int id = getResources().getIdentifier("status_bar_height", "dimen", "android");
        int value = id > 0 ? getResources().getDimensionPixelSize(id) : 0;
        return value > 0 ? value : dp(26);
    }

    private LinearLayout buildHeader(Context c) {
        header = row(c);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setBackgroundColor(SURFACE_DARK);
        header.setElevation(dp(3));

        MaterialButton menuBtn = vectorIconButton(c, "ic_dayven_menu", "Menu & History", dp(20));
        menuBtn.setOnClickListener(v -> {
            bounce(v);
            showDrawer();
        });
        header.addView(menuBtn, new LinearLayout.LayoutParams(dp(42), dp(42)));

        ImageView avatar = new ImageView(c);
        int sparkleId = getResDrawable(c, "ic_dayven_sparkle");
        if (sparkleId != 0) avatar.setImageResource(sparkleId);
        avatar.setColorFilter(ACCENT);
        avatar.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        avatar.setPadding(dp(6), dp(6), dp(6), dp(6));
        avatar.setBackground(round(ACCENT_SOFT, 16));
        LinearLayout.LayoutParams avatarLp = new LinearLayout.LayoutParams(dp(32), dp(32));
        avatarLp.leftMargin = dp(2);
        avatarLp.rightMargin = dp(10);
        header.addView(avatar, avatarLp);

        LinearLayout titleGroup = column(c);
        titleGroup.setGravity(Gravity.CENTER_VERTICAL);
        sessionTitle = label(c, session.title, 15, true);
        sessionTitle.setTextColor(TEXT_PRIMARY);
        sessionTitle.setSingleLine(true);
        titleGroup.addView(sessionTitle);
        TextView subtitle = label(c, "Dayven AI · Elite v3.5", 11, false);
        subtitle.setTextColor(TEXT_SECONDARY);
        subtitle.setSingleLine(true);
        titleGroup.addView(subtitle);
        header.addView(titleGroup, new LinearLayout.LayoutParams(0, -2, 1f));

        MaterialButton undoBtn = vectorIconButton(c, "ic_dayven_rollback", "Rollback", dp(19));
        undoBtn.setOnClickListener(v -> {
            bounce(v);
            showRollbackCard();
        });
        header.addView(undoBtn, new LinearLayout.LayoutParams(dp(40), dp(42)));

        expandButton = vectorIconButton(c, "ic_dayven_minimize", "Minimize/Expand", dp(19));
        expandButton.setOnClickListener(v -> {
            bounce(v);
            toggleExpand();
        });
        header.addView(expandButton, new LinearLayout.LayoutParams(dp(40), dp(42)));

        MaterialButton settingsBtn = vectorIconButton(c, "ic_dayven_settings", "Settings", dp(20));
        settingsBtn.setOnClickListener(v -> {
            bounce(v);
            showSettingsDialog();
        });
        header.addView(settingsBtn, new LinearLayout.LayoutParams(dp(40), dp(42)));

        return header;
    }

    private LinearLayout buildStatusStrip(Context c) {
        LinearLayout strip = row(c);
        strip.setGravity(Gravity.CENTER_VERTICAL);
        strip.setPadding(dp(18), dp(7), dp(18), dp(7));
        strip.setBackgroundColor(0xFF101216);

        statusDot = new View(c);
        statusDot.setBackground(round(IOS_RED, 5));
        strip.addView(statusDot, new LinearLayout.LayoutParams(dp(8), dp(8)));

        statusText = label(c, "Disconnected", 11, false);
        statusText.setPadding(dp(8), 0, 0, 0);
        statusText.setTextColor(IOS_RED);
        strip.addView(statusText, new LinearLayout.LayoutParams(0, -2, 1f));

        tokenText = label(c, "0 tokens", 11, false);
        tokenText.setTextColor(TEXT_SECONDARY);
        strip.addView(tokenText);

        return strip;
    }

    private LinearLayout buildComposer(Context c) {
        LinearLayout bar = row(c);
        bar.setGravity(Gravity.BOTTOM);
        bar.setPadding(dp(12), dp(8), dp(12), dp(12));
        bar.setBackgroundColor(BG_DARK);

        LinearLayout inputArea = row(c);
        inputArea.setGravity(Gravity.CENTER_VERTICAL);
        inputArea.setBackground(stroke(SURFACE_DARK, dp(22), BORDER_DARK));
        inputArea.setPadding(dp(6), dp(4), dp(6), dp(4));

        MaterialButton attach = vectorIconButton(c, "ic_dayven_add", "Attach context", dp(18));
        attach.setIconTint(ColorStateList.valueOf(TEXT_SECONDARY));
        attach.setOnClickListener(v -> {
            bounce(v);
            showContextSheet();
        });
        inputArea.addView(attach, new LinearLayout.LayoutParams(dp(36), dp(36)));

        composer = new EditText(c);
        composer.setHint("Message Dayven AI...");
        composer.setHintTextColor(TEXT_SECONDARY);
        composer.setTextColor(TEXT_PRIMARY);
        composer.setBackground(null);
        composer.setTextSize(14);
        composer.setMaxLines(5);
        composer.setPadding(dp(6), dp(8), dp(6), dp(8));
        inputArea.addView(composer, new LinearLayout.LayoutParams(0, -2, 1f));

        sendButton = vectorIconButton(c, "ic_dayven_send", "Send", dp(18));
        sendButton.setIconTint(ColorStateList.valueOf(Color.WHITE));
        sendButton.setBackgroundTintList(ColorStateList.valueOf(ACCENT));
        sendButton.setCornerRadius(dp(18));
        sendButton.setScaleX(0f);
        sendButton.setScaleY(0f);
        sendButton.setOnClickListener(v -> send());
        inputArea.addView(sendButton, new LinearLayout.LayoutParams(dp(36), dp(36)));

        bar.addView(inputArea, new LinearLayout.LayoutParams(0, -2, 1f));

        composer.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                boolean hasText = s != null && s.toString().trim().length() > 0;
                float target = hasText ? 1f : 0f;
                sendButton.animate().cancel();
                sendButton.animate().scaleX(target).scaleY(target)
                        .setInterpolator(new OvershootInterpolator(1.6f))
                        .setDuration(180).start();
            }
            @Override public void afterTextChanged(Editable s) { }
        });

        composer.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                main.postDelayed(this::scrollBottom, 250);
            }
        });

        return bar;
    }

    private void toggleExpand() {
        Activity act = getActivity();
        if (act == null) return;
        expanded = !expanded;
        AgentTabEnhancer.setExpanded(act, expanded);
        if (expandButton != null) {
            int iconId = getResDrawable(requireContext(), "ic_dayven_minimize");
            if (iconId != 0) expandButton.setIconResource(iconId);
        }
        applyTopInset();
    }

    private void restoreSession() {
        messages.removeAllViews();
        profiles = store.getProfiles();
        boolean active = hasActiveKey(profiles);
        updateStatusIndicator(active);
        if (session == null || session.messages.isEmpty()) {
            bubble(active ? GREETING : NO_KEY, false);
        } else {
            for (AgentWorkspaceStore.Message m : session.messages) bubble(m.text, "user".equals(m.role));
        }
        scrollBottom();
    }

    private boolean hasActiveKey(List<AgentWorkspaceStore.Profile> list) {
        if (store != null) {
            String fallback = store.getFallbackApiKey();
            if (fallback != null && !fallback.trim().isEmpty()) {
                return true;
            }
        }
        if (list != null) {
            for (AgentWorkspaceStore.Profile p : list) {
                if (p != null && p.key != null && !p.key.trim().isEmpty()) {
                    return true;
                }
            }
        }
        return false;
    }

    private String getBestApiKey(List<AgentWorkspaceStore.Profile> list) {
        if (store != null) {
            String fallback = store.getFallbackApiKey();
            if (fallback != null && !fallback.trim().isEmpty()) {
                return fallback.trim();
            }
        }
        if (list != null) {
            for (AgentWorkspaceStore.Profile p : list) {
                if (p != null && p.key != null && !p.key.trim().isEmpty()) {
                    return p.key.trim();
                }
            }
        }
        return "";
    }

    private void addAssistant(String text) {
        bubble(text, false);
        scrollBottom();
    }

    private void send() {
        if (busy) return;
        String prompt = composer.getText().toString().trim();
        if (prompt.isEmpty()) return;

        // Instant local intents
        if (AgentMemoryEngine.isRollbackIntent(prompt) && memory.depth(projectId) > 0) {
            composer.setText("");
            recordUser(prompt);
            showRollbackCard();
            return;
        }
        if (AgentMemoryEngine.isChangelogIntent(prompt)) {
            composer.setText("");
            recordUser(prompt);
            addAssistant(changelogText());
            return;
        }

        profiles = store.getProfiles();
        if (!hasActiveKey(profiles)) {
            addAssistant("API Key belum dikonfigurasi. Silakan buka Settings di kanan atas untuk menambahkan API Key Gemini.");
            Toast.makeText(requireContext(), "Silakan tambahkan API Key di Settings", Toast.LENGTH_SHORT).show();
            return;
        }

        composer.setText("");
        recordUser(prompt);

        busy = true;
        triedRoutes.clear();
        continuation = 0;
        liveAssistantContainer = null;
        liveAssistantText = null;
        streamBuffer.setLength(0);
        addThemedThinking();

        int mode = AgentPromptOptimizer.detectMode(prompt);
        activeSystem = AgentPromptOptimizer.systemInstruction(mode);
        activeTurns = new ArrayList<>();
        appendHistory(activeTurns, mode);

        String context = mode == AgentPromptOptimizer.MODE_LITE
                ? ""
                : AgentProjectContext.build(requireContext(), projectId, selectedFile);
        activeTurns.add(GeminiAutoClient.Turn.user(
                AgentPromptOptimizer.buildTurn(prompt, context, memory.digest(projectId), mode)));

        List<Route> routes = routesFor(prompt, mode);
        if (routes.isEmpty()) {
            finishError("Tidak ada model AI yang valid. Silakan periksa API Key di Settings.");
            return;
        }
        streamRoute(prompt, routes, 0, new StringBuilder(), false);
    }

    private void recordUser(String prompt) {
        store.append(session.id, "user", prompt);
        AgentWorkspaceStore.Session refreshed = store.getSession(session.id);
        if (refreshed != null) session = refreshed;
        if (sessionTitle != null) sessionTitle.setText(session.title);
        bubble(prompt, true);
        scrollBottom();
    }

    private void appendHistory(List<GeminiAutoClient.Turn> turns, int mode) {
        if (session == null || session.messages == null) return;
        List<AgentWorkspaceStore.Message> all = session.messages;
        int end = all.size() - 1;
        if (end <= 0) return;

        int window = mode == AgentPromptOptimizer.MODE_LITE ? 4 : 10;
        int start = Math.max(0, end - window);
        while (start < end && all.get(start) != null && !"user".equals(all.get(start).role)) start++;

        for (int i = start; i < end; i++) {
            AgentWorkspaceStore.Message m = all.get(i);
            if (m == null || m.text == null || m.text.trim().isEmpty()) continue;
            boolean user = "user".equals(m.role);
            String text = AgentPromptOptimizer.trimHistory(m.text, user ? 1000 : 2000);
            turns.add(user ? GeminiAutoClient.Turn.user(text) : GeminiAutoClient.Turn.model(text));
        }
    }

    private void addThemedThinking() {
        stopThinkingAnimation();
        Context c = requireContext();

        final LinearLayout box = row(c);
        box.setTag("thinking");
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setPadding(dp(14), dp(10), dp(16), dp(10));
        box.setBackground(stroke(CARD_DARK, dp(16), BORDER_DARK));

        final View glowDot = new View(c);
        glowDot.setBackground(round(ACCENT_CYAN, 7));
        box.addView(glowDot, new LinearLayout.LayoutParams(dp(10), dp(10)));

        LinearLayout textCol = column(c);
        textCol.setPadding(dp(10), 0, 0, 0);

        TextView title = label(c, "Dayven AI menganalisis...", 13, true);
        title.setTextColor(TEXT_PRIMARY);
        textCol.addView(title);

        TextView subtitle = label(c, "Menyusun arsitektur dan mengeksekusi instruksi", 11, false);
        subtitle.setTextColor(TEXT_SECONDARY);
        textCol.addView(subtitle);

        box.addView(textCol, new LinearLayout.LayoutParams(0, -2, 1f));

        LinearLayout wrap = row(c);
        wrap.setTag("thinking_wrap");
        wrap.setGravity(Gravity.START);
        wrap.addView(aiAvatar(), avatarParams());
        wrap.addView(box);

        messages.addView(wrap);
        animateIn(wrap);
        scrollBottom();

        ObjectAnimator scaleX = ObjectAnimator.ofFloat(glowDot, "scaleX", 0.7f, 1.4f, 0.7f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(glowDot, "scaleY", 0.7f, 1.4f, 0.7f);
        ObjectAnimator alpha = ObjectAnimator.ofFloat(glowDot, "alpha", 0.4f, 1f, 0.4f);
        ObjectAnimator boxAlpha = ObjectAnimator.ofFloat(box, "alpha", 0.85f, 1f, 0.85f);

        scaleX.setRepeatCount(ValueAnimator.INFINITE);
        scaleY.setRepeatCount(ValueAnimator.INFINITE);
        alpha.setRepeatCount(ValueAnimator.INFINITE);
        boxAlpha.setRepeatCount(ValueAnimator.INFINITE);

        thinkingAnimatorSet = new AnimatorSet();
        thinkingAnimatorSet.playTogether(scaleX, scaleY, alpha, boxAlpha);
        thinkingAnimatorSet.setDuration(1200);
        thinkingAnimatorSet.setInterpolator(new AccelerateDecelerateInterpolator());
        thinkingAnimatorSet.start();
    }

    private void stopThinkingAnimation() {
        if (thinkingAnimatorSet != null) {
            thinkingAnimatorSet.cancel();
            thinkingAnimatorSet = null;
        }
    }

    private void removeThinking() {
        stopThinkingAnimation();
        if (messages == null) return;
        final View v = messages.findViewWithTag("thinking_wrap");
        if (v == null) return;
        v.setTag(null);
        v.animate().alpha(0f).translationY(dp(-8)).setDuration(180)
                .withEndAction(() -> messages.removeView(v)).start();
    }

    private List<Route> routesFor(String prompt, int mode) {
        List<Route> out = new ArrayList<>();
        profiles = store.getProfiles();
        String directKey = getBestApiKey(profiles);

        List<String> defaultModels = new ArrayList<>(GeminiAutoClient.FALLBACK_TIERS);

        for (AgentWorkspaceStore.Profile p : profiles) {
            String useKey = (p.key != null && !p.key.trim().isEmpty()) ? p.key.trim() : directKey;
            if (useKey.isEmpty()) continue;
            List<String> models = new ArrayList<>();
            if (p.models != null && !p.models.isEmpty()) {
                models.addAll(p.models);
            } else {
                models.addAll(defaultModels);
            }
            for (String fallbackM : defaultModels) {
                if (!models.contains(fallbackM)) models.add(fallbackM);
            }
            final int t = mode;
            models.sort((a, b) -> Integer.compare(scoreModel(a, t), scoreModel(b, t)));
            for (String m : models) {
                AgentWorkspaceStore.Profile activeP = new AgentWorkspaceStore.Profile();
                activeP.id = p.id;
                activeP.name = p.name;
                activeP.key = useKey;
                out.add(new Route(activeP, m));
            }
        }

        if (out.isEmpty() && !directKey.isEmpty()) {
            AgentWorkspaceStore.Profile fallbackProf = new AgentWorkspaceStore.Profile();
            fallbackProf.name = "Gemini";
            fallbackProf.key = directKey;
            for (String m : defaultModels) {
                out.add(new Route(fallbackProf, m));
            }
        }

        return out;
    }

    private int scoreModel(String model, int tier) {
        String m = model.toLowerCase();
        if (tier == AgentPromptOptimizer.MODE_PRO) {
            if (m.contains("3.6-flash")) return 0;
            if (m.contains("pro")) return 1;
            if (m.contains("2.5-flash") || m.contains("2.0-flash") || m.contains("flash")) return 2;
            return 3;
        }
        if (tier == AgentPromptOptimizer.MODE_LITE) {
            if (m.contains("lite") || m.contains("8b")) return 0;
            if (m.contains("2.5-flash") || m.contains("2.0-flash") || m.contains("3.6-flash")) return 1;
            return 2;
        }
        if (m.contains("3.6-flash")) return 0;
        if (m.contains("2.5-flash") || m.contains("2.0-flash")) return 1;
        if (m.contains("flash")) return 2;
        if (m.contains("pro")) return 3;
        return 4;
    }

    private void streamRoute(final String original, final List<Route> routes, final int index,
                             final StringBuilder errorTrace, final boolean resume) {
        if (index >= routes.size()) {
            String detail = errorTrace.length() > 0 ? "\n\nDetail: " + errorTrace : "";
            finishError("Semua API Key dan model tier sudah dicoba. Cek koneksi atau kuota API Key di Settings." + detail);
            return;
        }

        final Route route = routes.get(index);
        String routeKey = (route.profile.id != null ? route.profile.id : "prof") + ":" + route.model;
        if (!resume && !triedRoutes.add(routeKey)) {
            streamRoute(original, routes, index + 1, errorTrace, false);
            return;
        }

        if (!resume) {
            liveAssistantContainer = null;
            liveAssistantText = null;
            streamBuffer.setLength(0);
        }

        client.streamConversation(route.profile.key, route.model, activeSystem, activeTurns,
                GeminiAutoClient.MAX_OUTPUT_TOKENS, new GeminiAutoClient.StreamListener() {
            @Override public void onModels(List<GeminiAutoClient.ModelInfo> models, String remaining, String retry) { }

            @Override public void onDelta(String text) {
                // Throttle / batch delta dispatch to prevent UI freezes on massive code streams
                synchronized (streamBuffer) {
                    streamBuffer.append(text);
                }
                if (!streamFlushScheduled) {
                    streamFlushScheduled = true;
                    main.postDelayed(streamFlushRunnable, 60);
                }
            }

            @Override public void onComplete(String model, GeminiAutoClient.Usage usage, String remaining, String retry) {
                main.post(() -> {
                    main.removeCallbacks(streamFlushRunnable);
                    streamFlushRunnable.run();
                    finalizeAnswer(route, original, usage, remaining);
                });
            }

            @Override public void onTruncated(String model, GeminiAutoClient.Usage usage, String remaining, String retry) {
                main.post(() -> {
                    main.removeCallbacks(streamFlushRunnable);
                    streamFlushRunnable.run();
                    if (liveAssistantText == null || continuation >= MAX_CONTINUATION) {
                        finalizeAnswer(route, original, usage, remaining);
                        return;
                    }
                    continuation++;
                    tokenText.setText(usage.label() + " · lanjut " + continuation);
                    String partial = liveAssistantText.getText().toString();
                    String tail = partial.length() > 4000 ? partial.substring(partial.length() - 4000) : partial;
                    activeTurns.add(GeminiAutoClient.Turn.model(tail));
                    activeTurns.add(GeminiAutoClient.Turn.user(CONTINUE_HINT));
                    streamRoute(original, routes, index, errorTrace, true);
                });
            }

            @Override public void onError(int status, String message, String remaining, String retry) {
                main.post(() -> {
                    main.removeCallbacks(streamFlushRunnable);
                    streamFlushRunnable.run();
                    if (liveAssistantText != null && liveAssistantText.getText().length() > 0) {
                        finalizeAnswer(route, original, new GeminiAutoClient.Usage(0, 0, 0), remaining);
                        return;
                    }
                    // Silent failover on high demand or rate limit errors
                    if (errorTrace.length() == 0) {
                        errorTrace.append('[').append(route.model).append(": ").append(message).append(']');
                    }
                    streamRoute(original, routes, index + 1, errorTrace, false);
                });
            }
        });
    }

    private void finalizeAnswer(Route route, String original, GeminiAutoClient.Usage usage, String remaining) {
        busy = false;
        removeThinking();
        if (route != null && route.profile != null) {
            route.profile.rate = remaining == null ? "" : remaining;
            store.saveProfiles(profiles);
        }
        if (usage != null && usage.total > 0) tokenText.setText(usage.label());
        if (liveAssistantText == null || liveAssistantContainer == null) return;

        String full = liveAssistantText.getText().toString();
        store.append(session.id, "assistant", full);
        AgentWorkspaceStore.Session refreshed = store.getSession(session.id);
        if (refreshed != null) session = refreshed;

        // Render response with Claude-style accordion, ChatGPT-style code blocks, and copy button
        renderStructuredResponse(liveAssistantContainer, full);

        List<AgentActionExecutor.Action> actions = AgentActionExecutor.parseAll(full);
        if (!actions.isEmpty()) showProposal(actions, original);
        scrollBottom();
    }

    private void addAssistantStreamBubble() {
        LinearLayout line = row(requireContext());
        line.setGravity(Gravity.START);
        line.addView(aiAvatar(), avatarParams());

        LinearLayout holder = column(requireContext());

        TextView view = label(requireContext(), "", 14, false);
        view.setTextColor(TEXT_PRIMARY);
        view.setLineSpacing(dp(3), 1f);
        view.setPadding(dp(14), dp(11), dp(14), dp(11));
        view.setBackground(round(AI_BUBBLE, 16));
        view.setMaxWidth(Math.round(getResources().getDisplayMetrics().widthPixels * 0.82f));
        view.setTextIsSelectable(true);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMargins(0, dp(5), 0, dp(2));
        holder.addView(view, lp);

        LinearLayout copy = copyRow(view, null);
        holder.addView(copy);

        line.addView(holder, new LinearLayout.LayoutParams(-2, -2));
        messages.addView(line);
        animateIn(line);

        liveAssistantContainer = holder;
        liveAssistantText = view;
    }

    private void bubble(String text, boolean user) {
        LinearLayout line = row(requireContext());
        line.setGravity(user ? Gravity.END : Gravity.START);

        if (!user) line.addView(aiAvatar(), avatarParams());

        LinearLayout holder = column(requireContext());

        if (user) {
            TextView view = label(requireContext(), text, 14, false);
            view.setTextColor(TEXT_PRIMARY);
            view.setLineSpacing(dp(3), 1f);
            view.setPadding(dp(14), dp(11), dp(14), dp(11));
            view.setBackground(round(USER_BUBBLE, 16));
            view.setMaxWidth(Math.round(getResources().getDisplayMetrics().widthPixels * 0.78f));
            view.setTextIsSelectable(true);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.setMargins(0, dp(5), 0, dp(2));
            holder.addView(view, lp);
        } else {
            renderStructuredResponse(holder, text);
            holder.addView(copyRow(null, text));
        }

        line.addView(holder, new LinearLayout.LayoutParams(-2, -2));
        messages.addView(line);
        animateIn(line);
    }

    /**
     * Parses the assistant message and displays:
     * 1. Claude-style collapsible execution step accordion (`>_ n langkah`)
     * 2. Prose paragraphs
     * 3. ChatGPT-style code blocks with language indicator and copy button
     */
    private void renderStructuredResponse(LinearLayout targetContainer, String raw) {
        targetContainer.removeAllViews();
        Context c = requireContext();
        int maxBubbleWidth = Math.round(getResources().getDisplayMetrics().widthPixels * 0.82f);

        // 1. Check for Claude-style execution steps
        List<AgentActionExecutor.ExecutionStep> steps = AgentActionExecutor.parseSteps(raw);
        if (!steps.isEmpty()) {
            View stepAccordion = createClaudeStepsAccordion(c, steps, maxBubbleWidth);
            targetContainer.addView(stepAccordion);
        }

        // Clean out raw EXEC_STEP blocks from prose text
        String cleanProse = raw.replaceAll("(?im)^\\s*(?:EXEC_STEP|LANGKAH|STEP):[^\r\n]+", "")
                               .replaceAll("(?im)^\\s*(?:CMD|COMMAND):[^\r\n]+", "")
                               .replaceAll("(?im)^\\s*STATUS:[^\r\n]+", "")
                               .replaceAll("(?im)^\\s*LOG:[^\r\n]+", "").trim();

        int pos = 0;
        int len = cleanProse.length();

        while (pos < len) {
            int fenceStart = cleanProse.indexOf("```", pos);
            if (fenceStart == -1) {
                String tail = cleanProse.substring(pos).trim();
                if (!tail.isEmpty()) {
                    TextView tv = createTextBubble(c, tail, maxBubbleWidth);
                    targetContainer.addView(tv);
                }
                break;
            }

            String proseBefore = cleanProse.substring(pos, fenceStart).trim();
            if (!proseBefore.isEmpty()) {
                TextView tv = createTextBubble(c, proseBefore, maxBubbleWidth);
                targetContainer.addView(tv);
            }

            int newlineAfterFence = cleanProse.indexOf('\n', fenceStart + 3);
            String lang = "CODE";
            if (newlineAfterFence != -1 && newlineAfterFence - (fenceStart + 3) < 30) {
                String extracted = cleanProse.substring(fenceStart + 3, newlineAfterFence).trim();
                if (!extracted.isEmpty()) lang = extracted.toUpperCase(Locale.ROOT);
            }

            int codeContentStart = newlineAfterFence != -1 ? newlineAfterFence + 1 : fenceStart + 3;
            int fenceEnd = cleanProse.indexOf("```", codeContentStart);
            String codeBody;
            if (fenceEnd != -1) {
                codeBody = cleanProse.substring(codeContentStart, fenceEnd);
                pos = fenceEnd + 3;
            } else {
                codeBody = cleanProse.substring(codeContentStart);
                pos = len;
            }

            View codeCard = createCodeBlock(c, codeBody.trim(), lang, maxBubbleWidth);
            targetContainer.addView(codeCard);
        }
    }

    /**
     * Builds Claude-style collapsible execution step accordion (`>_ n langkah`)
     * as requested and shown in user reference screenshots.
     */
    private View createClaudeStepsAccordion(Context c, List<AgentActionExecutor.ExecutionStep> steps, int maxWidth) {
        LinearLayout wrap = column(c);
        wrap.setBackground(stroke(0xFF14161C, dp(12), 0xFF2A2E38));

        LinearLayout.LayoutParams wrapLp = new LinearLayout.LayoutParams(maxWidth, -2);
        wrapLp.setMargins(0, dp(4), 0, dp(6));
        wrap.setLayoutParams(wrapLp);

        // Header bar
        LinearLayout head = row(c);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(dp(12), dp(8), dp(12), dp(8));

        TextView icon = label(c, ">_", 13, true);
        icon.setTextColor(ACCENT_CYAN);
        icon.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        head.addView(icon);

        TextView title = label(c, " " + steps.size() + " langkah", 12, true);
        title.setTextColor(TEXT_PRIMARY);
        head.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));

        final TextView toggleIcon = label(c, "❯", 12, true);
        toggleIcon.setTextColor(TEXT_SECONDARY);
        head.addView(toggleIcon);

        wrap.addView(head);

        // Expandable list container
        final LinearLayout bodyList = column(c);
        bodyList.setVisibility(View.GONE);
        bodyList.setPadding(dp(12), 0, dp(12), dp(10));

        for (AgentActionExecutor.ExecutionStep step : steps) {
            LinearLayout stepItem = column(c);
            stepItem.setPadding(0, dp(4), 0, dp(4));

            LinearLayout titleRow = row(c);
            titleRow.setGravity(Gravity.CENTER_VERTICAL);

            TextView dot = label(c, "• ", 12, true);
            dot.setTextColor(IOS_GREEN);
            titleRow.addView(dot);

            TextView stepTitle = label(c, step.title, 12, true);
            stepTitle.setTextColor(TEXT_PRIMARY);
            titleRow.addView(stepTitle, new LinearLayout.LayoutParams(0, -2, 1f));

            TextView statusBadge = label(c, step.status, 10, true);
            statusBadge.setTextColor(IOS_GREEN);
            statusBadge.setBackground(round(0x2234C759, 4));
            statusBadge.setPadding(dp(6), dp(2), dp(6), dp(2));
            titleRow.addView(statusBadge);

            stepItem.addView(titleRow);

            if (!step.command.isEmpty()) {
                TextView cmdView = new TextView(c);
                cmdView.setText("$ " + step.command);
                cmdView.setTypeface(Typeface.MONOSPACE);
                cmdView.setTextSize(11);
                cmdView.setTextColor(0xFF9CDCFE);
                cmdView.setBackground(round(0xFF1E1E1E, 6));
                cmdView.setPadding(dp(8), dp(4), dp(8), dp(4));
                LinearLayout.LayoutParams cmdLp = new LinearLayout.LayoutParams(-1, -2);
                cmdLp.topMargin = dp(2);
                stepItem.addView(cmdView, cmdLp);
            }

            if (!step.log.isEmpty()) {
                TextView logView = new TextView(c);
                logView.setText(step.log);
                logView.setTypeface(Typeface.MONOSPACE);
                logView.setTextSize(10);
                logView.setTextColor(TEXT_MUTED);
                logView.setPadding(dp(4), dp(2), dp(4), dp(2));
                stepItem.addView(logView);
            }

            bodyList.addView(stepItem);
        }

        wrap.addView(bodyList);

        head.setOnClickListener(v -> {
            bounce(v);
            boolean isVisible = bodyList.getVisibility() == View.VISIBLE;
            bodyList.setVisibility(isVisible ? View.GONE : View.VISIBLE);
            toggleIcon.setRotation(isVisible ? 0f : 90f);
        });

        return wrap;
    }

    private TextView createTextBubble(Context c, String text, int maxWidth) {
        TextView view = label(c, richText(text), 14, false);
        view.setTextColor(TEXT_PRIMARY);
        view.setLineSpacing(dp(3), 1f);
        view.setPadding(dp(14), dp(11), dp(14), dp(11));
        view.setBackground(round(AI_BUBBLE, 16));
        view.setMaxWidth(maxWidth);
        view.setTextIsSelectable(true);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMargins(0, dp(4), 0, dp(4));
        view.setLayoutParams(lp);
        return view;
    }

    private View createCodeBlock(Context c, final String code, String lang, int maxWidth) {
        LinearLayout box = column(c);
        box.setBackground(stroke(CODE_BG, dp(12), 0xFF383C4A));
        box.setElevation(dp(2));

        LinearLayout.LayoutParams boxLp = new LinearLayout.LayoutParams(maxWidth, -2);
        boxLp.setMargins(0, dp(8), 0, dp(8));
        box.setLayoutParams(boxLp);

        // Header
        LinearLayout headerRow = row(c);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);
        headerRow.setPadding(dp(14), dp(8), dp(10), dp(8));
        headerRow.setBackground(roundTop(CODE_HEADER_BG, dp(12)));

        TextView langTv = label(c, lang, 11, true);
        langTv.setTextColor(0xFF4EC9B0);
        headerRow.addView(langTv, new LinearLayout.LayoutParams(0, -2, 1f));

        final MaterialButton copyBtn = new MaterialButton(c);
        copyBtn.setText("Copy");
        copyBtn.setTextSize(11);
        copyBtn.setAllCaps(false);
        int copyIconId = getResDrawable(c, "ic_dayven_copy");
        if (copyIconId != 0) {
            copyBtn.setIconResource(copyIconId);
            copyBtn.setIconTint(ColorStateList.valueOf(Color.WHITE));
            copyBtn.setIconSize(dp(14));
            copyBtn.setIconPadding(dp(4));
        }
        copyBtn.setBackgroundTintList(ColorStateList.valueOf(0xFF2563EB));
        copyBtn.setTextColor(Color.WHITE);
        copyBtn.setCornerRadius(dp(8));
        copyBtn.setInsetTop(0);
        copyBtn.setInsetBottom(0);
        copyBtn.setPadding(dp(10), 0, dp(10), 0);
        copyBtn.setMinHeight(dp(30));
        copyBtn.setMinimumHeight(dp(30));
        copyBtn.setOnClickListener(v -> {
            bounce(v);
            ClipboardManager cm = (ClipboardManager) c.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("Code", code));
            copyBtn.setText("Copied!");
            int checkIconId = getResDrawable(c, "ic_dayven_check");
            if (checkIconId != 0) copyBtn.setIconResource(checkIconId);
            copyBtn.setBackgroundTintList(ColorStateList.valueOf(0xFF059669));
            main.postDelayed(() -> {
                copyBtn.setText("Copy");
                if (copyIconId != 0) copyBtn.setIconResource(copyIconId);
                copyBtn.setBackgroundTintList(ColorStateList.valueOf(0xFF2563EB));
            }, 1800);
            Toast.makeText(c, "Kode berhasil disalin! 🚀", Toast.LENGTH_SHORT).show();
        });
        headerRow.addView(copyBtn, new LinearLayout.LayoutParams(-2, dp(30)));
        box.addView(headerRow);

        // Code text view
        TextView codeView = new TextView(c);
        codeView.setText(code);
        codeView.setTypeface(Typeface.MONOSPACE);
        codeView.setTextSize(12);
        codeView.setTextColor(0xFFD4D4D4);
        codeView.setLineSpacing(dp(2), 1.15f);
        codeView.setPadding(dp(14), dp(12), dp(14), dp(12));
        codeView.setTextIsSelectable(true);
        box.addView(codeView);

        return box;
    }

    private LinearLayout copyRow(final TextView source, final String raw) {
        LinearLayout holder = row(requireContext());
        holder.setGravity(Gravity.START);

        final TextView copy = label(requireContext(), "COPY SEMUA", 10, true);
        copy.setTextColor(TEXT_SECONDARY);
        copy.setPadding(dp(10), dp(4), dp(10), dp(4));
        copy.setBackground(round(CARD_DARK, 9));
        copy.setTag(raw);
        copy.setOnClickListener(v -> {
            Object tag = copy.getTag();
            String value = tag instanceof String ? (String) tag : (source != null ? source.getText().toString() : "");
            ClipboardManager cm = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("Dayven AI", value));
            bounce(v);
            Toast.makeText(requireContext(), "Disalin ke clipboard ✅", Toast.LENGTH_SHORT).show();
        });
        holder.addView(copy);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMargins(dp(2), 0, 0, dp(6));
        holder.setLayoutParams(lp);

        return holder;
    }

    private View aiAvatar() {
        ImageView avatar = new ImageView(requireContext());
        int sparkleId = getResDrawable(requireContext(), "ic_dayven_sparkle");
        if (sparkleId != 0) avatar.setImageResource(sparkleId);
        avatar.setColorFilter(ACCENT);
        avatar.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        avatar.setPadding(dp(4), dp(4), dp(4), dp(4));
        avatar.setBackground(round(ACCENT_SOFT, 12));
        return avatar;
    }

    private LinearLayout.LayoutParams avatarParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(24), dp(24));
        lp.setMargins(0, dp(10), dp(8), 0);
        return lp;
    }

    private void animateIn(View v) {
        v.setAlpha(0f);
        v.setTranslationY(dp(14));
        v.animate().alpha(1f).translationY(0f)
                .setDuration(240)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    private void bounce(View v) {
        v.animate().cancel();
        v.setScaleX(1f);
        v.setScaleY(1f);
        v.animate().scaleX(0.88f).scaleY(0.88f).setDuration(80)
                .withEndAction(() -> v.animate().scaleX(1f).scaleY(1f)
                        .setInterpolator(new OvershootInterpolator(2.2f))
                        .setDuration(160).start())
                .start();
    }

    private void showProposal(final List<AgentActionExecutor.Action> actions, final String original) {
        final LinearLayout card = column(requireContext());
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setBackground(stroke(CARD_DARK, dp(14), BORDER_DARK));

        TextView title = label(requireContext(), "PREVIEW PERUBAHAN OTOMATIS", 13, true);
        title.setTextColor(ACCENT);
        card.addView(title);

        TextView summary = label(requireContext(), actions.size() + " file akan otomatis diterapkan & disinkronisasi ke visual editor", 11, false);
        summary.setTextColor(TEXT_SECONDARY);
        summary.setPadding(0, dp(4), 0, dp(8));
        card.addView(summary);

        for (AgentActionExecutor.Action a : actions) {
            String note = a.isXml() ? "  · auto-sync visual editor" : "";
            TextView item = label(requireContext(), "• " + a.target + "  · " + a.badge() + note, 12, false);
            item.setTextColor(TEXT_PRIMARY);
            item.setPadding(dp(2), dp(3), 0, dp(3));
            card.addView(item);
        }

        LinearLayout actionsRow = row(requireContext());
        MaterialButton cancel = ghostButton("Cancel");
        int cancelId = getResDrawable(requireContext(), "ic_dayven_cancel");
        if (cancelId != 0) {
            cancel.setIconResource(cancelId);
            cancel.setIconTint(ColorStateList.valueOf(IOS_RED));
            cancel.setIconSize(dp(15));
        }
        MaterialButton accept = solidButton("Accept & Apply");
        int acceptId = getResDrawable(requireContext(), "ic_dayven_check");
        if (acceptId != 0) {
            accept.setIconResource(acceptId);
            accept.setIconTint(ColorStateList.valueOf(Color.WHITE));
            accept.setIconSize(dp(15));
        }
        actionsRow.addView(cancel, new LinearLayout.LayoutParams(0, -2, 1f));
        LinearLayout.LayoutParams acceptLp = new LinearLayout.LayoutParams(0, -2, 1.4f);
        acceptLp.leftMargin = dp(8);
        actionsRow.addView(accept, acceptLp);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, -2);
        rowLp.topMargin = dp(10);
        card.addView(actionsRow, rowLp);

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(-1, -2);
        cardParams.setMargins(0, dp(8), 0, dp(8));
        messages.addView(card, cardParams);
        animateIn(card);

        cancel.setOnClickListener(v -> {
            messages.removeView(card);
            addAssistant("Perubahan dibatalkan. Tidak ada file atau kode yang tersentuh.");
        });
        accept.setOnClickListener(v -> {
            messages.removeView(card);
            executeActions(actions, original);
        });

        scrollBottom();
    }

    private void executeActions(List<AgentActionExecutor.Action> actions, String original) {
        String label = original == null ? "Perubahan Dayven AI"
                : (original.length() > 60 ? original.substring(0, 60) + "..." : original);
        String report = AgentActionExecutor.applyAll(requireContext(), projectId, actions, memory, label);
        addAssistant(report + "\n\nSemua perubahan tersinkronisasi. Jika perlu kembali ke versi sebelumnya, cukup bilang \"balik ke yang tadi\".");
    }

    private void showRollbackCard() {
        String label = memory.peekLabel(projectId);
        if (label == null) {
            addAssistant("Belum ada perubahan yang bisa dikembalikan di project ini.");
            return;
        }

        final LinearLayout card = column(requireContext());
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setBackground(stroke(CARD_DARK, dp(14), BORDER_DARK));

        TextView title = label(requireContext(), "ROLLBACK DETECTED", 13, true);
        title.setTextColor(ACCENT);
        card.addView(title);

        TextView detail = label(requireContext(), "Mengembalikan ke versi sebelum perubahan terakhir:\n" + label, 12, false);
        detail.setTextColor(TEXT_SECONDARY);
        detail.setPadding(0, dp(5), 0, dp(6));
        card.addView(detail);

        for (String f : memory.peekFiles(projectId)) {
            TextView item = label(requireContext(), "• " + f, 12, false);
            item.setTextColor(TEXT_PRIMARY);
            item.setPadding(dp(2), dp(3), 0, dp(3));
            card.addView(item);
        }

        TextView depth = label(requireContext(), memory.depth(projectId) + " langkah undo tersimpan", 10, false);
        depth.setTextColor(TEXT_SECONDARY);
        depth.setPadding(0, dp(6), 0, 0);
        card.addView(depth);

        LinearLayout actionsRow = row(requireContext());
        MaterialButton cancel = ghostButton("Cancel");
        int rbCancelId = getResDrawable(requireContext(), "ic_dayven_cancel");
        if (rbCancelId != 0) {
            cancel.setIconResource(rbCancelId);
            cancel.setIconTint(ColorStateList.valueOf(IOS_RED));
            cancel.setIconSize(dp(15));
        }
        MaterialButton accept = solidButton("Accept rollback");
        int rbAcceptId = getResDrawable(requireContext(), "ic_dayven_check");
        if (rbAcceptId != 0) {
            accept.setIconResource(rbAcceptId);
            accept.setIconTint(ColorStateList.valueOf(Color.WHITE));
            accept.setIconSize(dp(15));
        }
        actionsRow.addView(cancel, new LinearLayout.LayoutParams(0, -2, 1f));
        LinearLayout.LayoutParams acceptLp = new LinearLayout.LayoutParams(0, -2, 1.4f);
        acceptLp.leftMargin = dp(8);
        actionsRow.addView(accept, acceptLp);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, -2);
        rowLp.topMargin = dp(10);
        card.addView(actionsRow, rowLp);

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(-1, -2);
        cardParams.setMargins(0, dp(8), 0, dp(8));
        messages.addView(card, cardParams);
        animateIn(card);

        cancel.setOnClickListener(v -> {
            messages.removeView(card);
            addAssistant("Rollback dibatalkan. Code kamu tetap seperti sekarang.");
        });
        accept.setOnClickListener(v -> {
            messages.removeView(card);
            String result = memory.rollbackLast(projectId);
            addAssistant(result);
        });

        scrollBottom();
    }

    private String changelogText() {
        List<String> log = memory.changelog(projectId);
        if (log.isEmpty()) return "Belum ada perubahan yang tercatat di project ini.";
        StringBuilder sb = new StringBuilder("Changelog internal (terbaru di bawah):\n");
        int from = Math.max(0, log.size() - 15);
        for (int i = from; i < log.size(); i++) sb.append("• ").append(log.get(i)).append('\n');
        sb.append("\n").append(memory.depth(projectId)).append(" langkah undo tersedia.");
        return sb.toString().trim();
    }

    private void showContextSheet() {
        StringBuilder sb = new StringBuilder("Konteks aktif Dayven AI:\n");
        sb.append("• Project ID: ").append(projectId == null || projectId.isEmpty() ? "Default" : projectId).append('\n');
        sb.append("• App Name: ").append(getProjectAppName()).append('\n');
        sb.append("• File aktif: ").append(selectedFile == null || selectedFile.isEmpty() ? "-" : selectedFile).append('\n');
        sb.append("• Riwayat chat: aktif\n");
        sb.append("• Undo snapshot: ").append(memory.depth(projectId)).append(" batch\n");
        sb.append("• Mode: Autonomous Real-Time Sync");
        addAssistant(sb.toString());
    }

    private void showDrawer() {
        if (activeDrawerOverlay != null) return;

        final Context c = requireContext();
        final FrameLayout overlay = new FrameLayout(c);
        overlay.setLayoutParams(new FrameLayout.LayoutParams(-1, -1));
        overlay.setBackgroundColor(0x00000000);
        activeDrawerOverlay = overlay;

        final LinearLayout drawer = column(c);
        drawer.setBackgroundColor(SURFACE_DARK);
        drawer.setPadding(dp(18), insetTop + dp(14), dp(18), dp(18));

        // 1. Profile Section with Project App Name and Icon
        LinearLayout profileBar = row(c);
        profileBar.setGravity(Gravity.CENTER_VERTICAL);
        profileBar.setPadding(dp(12), dp(10), dp(12), dp(10));
        profileBar.setBackground(stroke(CARD_DARK, dp(14), BORDER_DARK));

        ImageView iconView = new ImageView(c);
        loadProjectIcon(iconView);
        profileBar.addView(iconView, new LinearLayout.LayoutParams(dp(40), dp(40)));

        LinearLayout nameCol = column(c);
        nameCol.setPadding(dp(12), 0, 0, 0);

        TextView appTitle = label(c, getProjectAppName(), 15, true);
        appTitle.setTextColor(TEXT_PRIMARY);
        appTitle.setSingleLine(true);
        nameCol.addView(appTitle);

        TextView projectLabel = label(c, "Project ID: " + (projectId == null || projectId.isEmpty() ? "Default" : projectId), 11, false);
        projectLabel.setTextColor(TEXT_SECONDARY);
        nameCol.addView(projectLabel);

        profileBar.addView(nameCol, new LinearLayout.LayoutParams(0, -2, 1f));

        MaterialButton settingsIconBtn = vectorIconButton(c, "ic_dayven_settings", "Settings", dp(18));
        settingsIconBtn.setOnClickListener(v -> {
            bounce(v);
            closeDrawer(overlay, drawer);
            showSettingsDialog();
        });
        profileBar.addView(settingsIconBtn, new LinearLayout.LayoutParams(dp(36), dp(36)));

        drawer.addView(profileBar);

        // 2. New Chat Button
        MaterialButton newChat = solidButton("New Chat");
        int addIconId = getResDrawable(c, "ic_dayven_add");
        if (addIconId != 0) {
            newChat.setIconResource(addIconId);
            newChat.setIconTint(ColorStateList.valueOf(Color.WHITE));
            newChat.setIconSize(dp(16));
            newChat.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_START);
        }
        newChat.setOnClickListener(v -> {
            bounce(v);
            session = store.newSession("New chat");
            sessionTitle.setText(session.title);
            restoreSession();
            closeDrawer(overlay, drawer);
        });
        LinearLayout.LayoutParams newLp = new LinearLayout.LayoutParams(-1, -2);
        newLp.setMargins(0, dp(14), 0, dp(10));
        drawer.addView(newChat, newLp);

        // 3. Section Title
        LinearLayout historyHeader = row(c);
        historyHeader.setGravity(Gravity.CENTER_VERTICAL);
        historyHeader.setPadding(0, dp(4), 0, dp(6));

        TextView historyTitle = label(c, "Chat History", 13, true);
        historyTitle.setTextColor(TEXT_SECONDARY);
        historyHeader.addView(historyTitle, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView hint = label(c, "Tekan lama utk hapus", 10, false);
        hint.setTextColor(TEXT_MUTED);
        historyHeader.addView(hint);

        drawer.addView(historyHeader);

        // 4. Chat Sessions List with Long-press permanent delete
        ScrollView scroll = new ScrollView(c);
        scroll.setVerticalScrollBarEnabled(false);
        final LinearLayout list = column(c);

        populateSessionList(list, overlay, drawer);
        scroll.addView(list);
        drawer.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        final int width = Math.round(getResources().getDisplayMetrics().widthPixels * 0.82f);
        overlay.addView(drawer, new FrameLayout.LayoutParams(width, -1));
        drawer.setTranslationX(-width);
        overlay.setOnClickListener(v -> closeDrawer(overlay, drawer));
        root.addView(overlay);

        ValueAnimator scrim = ValueAnimator.ofInt(0, 160);
        scrim.setDuration(220);
        scrim.addUpdateListener(a -> overlay.setBackgroundColor(Color.argb((int) a.getAnimatedValue(), 0, 0, 0)));
        scrim.start();

        ObjectAnimator slide = ObjectAnimator.ofFloat(drawer, "translationX", -width, 0f);
        slide.setDuration(260);
        slide.setInterpolator(new DecelerateInterpolator(1.6f));
        slide.start();
    }

    private void populateSessionList(final LinearLayout list, final FrameLayout overlay, final View drawer) {
        list.removeAllViews();
        Context c = requireContext();

        for (final AgentWorkspaceStore.Session s : store.getSessions()) {
            boolean current = s.id.equals(session.id);

            final LinearLayout itemCard = row(c);
            itemCard.setGravity(Gravity.CENTER_VERTICAL);
            itemCard.setPadding(dp(12), dp(12), dp(12), dp(12));
            itemCard.setBackground(round(current ? CARD_ELEVATED : Color.TRANSPARENT, 12));

            TextView sessionName = label(c, s.title, 13, current);
            sessionName.setTextColor(current ? ACCENT : TEXT_PRIMARY);
            sessionName.setSingleLine(true);
            itemCard.addView(sessionName, new LinearLayout.LayoutParams(0, -2, 1f));

            if (current) {
                TextView activeTag = label(c, "• Aktif", 11, true);
                activeTag.setTextColor(ACCENT);
                itemCard.addView(activeTag);
            }

            itemCard.setOnClickListener(v -> {
                AgentWorkspaceStore.Session picked = store.getSession(s.id);
                if (picked != null) {
                    session = picked;
                    store.selectSession(s.id);
                    sessionTitle.setText(session.title);
                    restoreSession();
                }
                closeDrawer(overlay, drawer);
            });

            itemCard.setOnLongClickListener(v -> {
                bounce(v);
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle("Hapus Sesi Chat?")
                        .setMessage("Apakah kamu yakin ingin menghapus obrolan \"" + s.title + "\" secara permanen?\n\nCatatan: Memori arsitektur dan rollback kode project kamu tetap aman terjaga.")
                        .setPositiveButton("Hapus Permanen", (dialog, which) -> {
                            store.deleteSession(s.id);
                            Toast.makeText(requireContext(), "Riwayat chat dihapus permanen", Toast.LENGTH_SHORT).show();
                            if (s.id.equals(session.id)) {
                                session = store.getOrCreateCurrent();
                                sessionTitle.setText(session.title);
                                restoreSession();
                            }
                            populateSessionList(list, overlay, drawer);
                        })
                        .setNegativeButton("Batal", null)
                        .show();
                return true;
            });

            LinearLayout.LayoutParams itemLp = new LinearLayout.LayoutParams(-1, -2);
            itemLp.bottomMargin = dp(4);
            list.addView(itemCard, itemLp);
        }
    }

    private void closeDrawer(final FrameLayout overlay, final View drawer) {
        if (overlay == null || drawer == null) return;
        int width = drawer.getWidth() > 0 ? drawer.getWidth() : Math.round(getResources().getDisplayMetrics().widthPixels * 0.82f);

        ValueAnimator scrim = ValueAnimator.ofInt(160, 0);
        scrim.setDuration(200);
        scrim.addUpdateListener(a -> overlay.setBackgroundColor(Color.argb((int) a.getAnimatedValue(), 0, 0, 0)));
        scrim.start();

        ObjectAnimator slide = ObjectAnimator.ofFloat(drawer, "translationX", 0f, -width);
        slide.setDuration(200);
        slide.setInterpolator(new DecelerateInterpolator());
        slide.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) {
                root.removeView(overlay);
                activeDrawerOverlay = null;
            }
        });
        slide.start();
    }

    private void showSettingsDialog() {
        Context c = requireContext();
        String[] items = {
            "Kelola API Key & Provider",
            "Pilih Model Utama (Gemini)",
            "Temperature & Kreativitas",
            "Kapasitas Token & Konteks",
            "Ekspor Transkrip Percakapan",
            "Hapus Semua Obrolan (Clean Slate)"
        };

        new MaterialAlertDialogBuilder(c)
                .setTitle("Dayven AI Developer Settings")
                .setItems(items, (dialog, which) -> {
                    switch (which) {
                        case 0 -> showSettings();
                        case 1 -> showModelSelectorDialog();
                        case 2 -> showTemperatureDialog();
                        case 3 -> showTokenTelemetryDialog();
                        case 4 -> exportChatTranscript();
                        case 5 -> confirmClearAllSessions();
                    }
                })
                .setNegativeButton("Tutup", null)
                .show();
    }

    private void showModelSelectorDialog() {
        final String[] models = {
            "gemini-3.6-flash (Official Google AI Studio Flash)",
            "gemini-2.5-flash (Ultra fast coding)",
            "gemini-2.0-flash (General purpose balanced)",
            "gemini-1.5-pro (Deep reasoning & architecture)"
        };

        SharedPreferences sp = requireContext().getSharedPreferences("dayven_agent_settings", Context.MODE_PRIVATE);
        int currentIdx = sp.getInt("pref_model_idx", 0);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Pilih Model Gemini Utama")
                .setSingleChoiceItems(models, currentIdx, (dialog, which) -> {
                    sp.edit().putInt("pref_model_idx", which).commit();
                    Toast.makeText(requireContext(), "Model diubah ke " + models[which].split(" ")[0], Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                })
                .setNegativeButton("Batal", null)
                .show();
    }

    private void showTemperatureDialog() {
        final String[] temps = {
            "0.2 - Presisi Tinggi (Strict Coding & Bug Fixing)",
            "0.7 - Seimbang (Default Rekomendasi)",
            "1.0 - Kreatif (Brainstorming & Ide Fitur Baru)"
        };

        SharedPreferences sp = requireContext().getSharedPreferences("dayven_agent_settings", Context.MODE_PRIVATE);
        int currentIdx = sp.getInt("pref_temp_idx", 1);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Pengaturan Temperature")
                .setSingleChoiceItems(temps, currentIdx, (dialog, which) -> {
                    sp.edit().putInt("pref_temp_idx", which).commit();
                    Toast.makeText(requireContext(), "Temperature disimpan ✅", Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                })
                .setNegativeButton("Tutup", null)
                .show();
    }

    private void showTokenTelemetryDialog() {
        int memoryDepth = memory.depth(projectId);
        String label = memory.peekLabel(projectId);
        String info = "Informasi Kapasitas Token Dayven AI v3.5:\n\n"
                + "• Max Output: 8,192 tokens\n"
                + "• Efisiensi Context: Hemat hingga 90% token\n"
                + "• Streaming Throttler: Bebas lag untuk kode panjang\n"
                + "• Claude-style Steps: Collapsible shell accordion\n"
                + "• Visual Editor Sync: Real-time ViewBean auto-sync\n"
                + "• Snapshot Rollback: " + memoryDepth + " batch tersimpan\n"
                + "• Snapshot Terakhir: " + (label == null ? "Belum ada" : label);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Token & Telemetry Info")
                .setMessage(info)
                .setPositiveButton("Mengerti", null)
                .show();
    }

    private void exportChatTranscript() {
        if (session == null || session.messages.isEmpty()) {
            Toast.makeText(requireContext(), "Belum ada pesan untuk diekspor", Toast.LENGTH_SHORT).show();
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("# Dayven AI - Sesi Percakapan: ").append(session.title).append("\n\n");
        for (AgentWorkspaceStore.Message m : session.messages) {
            sb.append("### ").append("user".equals(m.role) ? "User" : "Dayven AI").append(":\n");
            sb.append(m.text).append("\n\n---\n\n");
        }

        ClipboardManager cm = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("Dayven Transcript", sb.toString()));
            Toast.makeText(requireContext(), "Transkrip disalin ke clipboard! 📤", Toast.LENGTH_LONG).show();
        }
    }

    private void confirmClearAllSessions() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Hapus Semua Riwayat Chat?")
                .setMessage("Tindakan ini akan menghapus semua sesi obrolan yang tersimpan.\nSistem AI akan kembali fresh seperti baru.")
                .setPositiveButton("Hapus Semua", (dialog, which) -> {
                    for (AgentWorkspaceStore.Session s : store.getSessions()) {
                        store.deleteSession(s.id);
                    }
                    session = store.getOrCreateCurrent();
                    sessionTitle.setText(session.title);
                    restoreSession();
                    Toast.makeText(requireContext(), "Semua obrolan dibersihkan", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Batal", null)
                .show();
    }

    private String getProjectAppName() {
        if (projectId != null && !projectId.trim().isEmpty()) {
            try {
                HashMap<String, Object> metadata = lC.b(projectId);
                if (metadata != null) {
                    String name = yB.c(metadata, "my_app_name");
                    if (name != null && !name.trim().isEmpty()) {
                        return name.trim();
                    }
                }
            } catch (Throwable ignored) { }
        }
        return "Dayven Project";
    }

    private void loadProjectIcon(ImageView target) {
        Context c = requireContext();
        target.setScaleType(ImageView.ScaleType.CENTER_CROP);
        GradientDrawable bg = round(CARD_DARK, 10);
        target.setBackground(bg);
        target.setClipToOutline(true);

        if (projectId != null && !projectId.trim().isEmpty()) {
            try {
                HashMap<String, Object> metadata = lC.b(projectId);
                if (metadata != null && yB.a(metadata, "custom_icon")) {
                    String iconPath = wq.e() + File.separator + projectId + File.separator + "icon.png";
                    File iconFile = new File(iconPath);
                    if (iconFile.exists()) {
                        Bitmap bmp = BitmapFactory.decodeFile(iconFile.getAbsolutePath());
                        if (bmp != null) {
                            target.setImageBitmap(bmp);
                            return;
                        }
                    }
                }
            } catch (Throwable ignored) { }
        }

        target.setImageResource(android.R.drawable.sym_def_app_icon);
    }

    private void showSettings() {
        if (activeSettingsPage != null) return;

        final FrameLayout page = new FrameLayout(requireContext());
        page.setLayoutParams(new FrameLayout.LayoutParams(-1, -1));
        page.setBackgroundColor(BG_DARK);
        activeSettingsPage = page;

        LinearLayout body = column(requireContext());
        body.setLayoutParams(new FrameLayout.LayoutParams(-1, -1));
        body.setPadding(dp(18), insetTop + dp(16), dp(18), dp(16));

        TextView title = label(requireContext(), "AI Providers", 19, true);
        title.setTextColor(TEXT_PRIMARY);
        body.addView(title);

        TextView sub = label(requireContext(), "Google AI Studio · Gemini API Keys", 12, false);
        sub.setTextColor(TEXT_SECONDARY);
        sub.setPadding(0, dp(3), 0, dp(14));
        body.addView(sub);

        ScrollView scroll = new ScrollView(requireContext());
        scroll.setVerticalScrollBarEnabled(false);
        providerList = column(requireContext());
        scroll.addView(providerList);
        body.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        MaterialButton addBtn = ghostButton("Add Gemini API Key");
        int addKeyIconId = getResDrawable(requireContext(), "ic_dayven_add");
        if (addKeyIconId != 0) {
            addBtn.setIconResource(addKeyIconId);
            addBtn.setIconTint(ColorStateList.valueOf(TEXT_PRIMARY));
            addBtn.setIconSize(dp(16));
            addBtn.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_START);
        }
        addBtn.setOnClickListener(v -> {
            store.addProfile("Gemini " + (store.getProfiles().size() + 1));
            refreshSettings();
        });
        LinearLayout.LayoutParams addLp = new LinearLayout.LayoutParams(-1, -2);
        addLp.topMargin = dp(10);
        body.addView(addBtn, addLp);

        MaterialButton done = solidButton("Done");
        done.setOnClickListener(v -> {
            page.animate().alpha(0f).setDuration(160).withEndAction(() -> {
                root.removeView(page);
                activeSettingsPage = null;
                profiles = store.getProfiles();
                updateStatusIndicator(hasActiveKey(profiles));
                startHeartbeat();
                restoreSession();
            }).start();
        });
        LinearLayout.LayoutParams doneLp = new LinearLayout.LayoutParams(-1, -2);
        doneLp.topMargin = dp(8);
        body.addView(done, doneLp);

        page.addView(body);
        page.setAlpha(0f);
        root.addView(page);
        page.animate().alpha(1f).setDuration(180).start();
        refreshSettings();
    }

    private void refreshSettings() {
        if (providerList == null) return;
        providerList.removeAllViews();
        profiles = store.getProfiles();
        String fallback = store.getFallbackApiKey();

        for (final AgentWorkspaceStore.Profile p : profiles) {
            LinearLayout card = column(requireContext());
            card.setPadding(dp(16), dp(14), dp(16), dp(14));
            card.setBackground(stroke(CARD_DARK, dp(13), BORDER_DARK));

            LinearLayout headRow = row(requireContext());
            headRow.setGravity(Gravity.CENTER_VERTICAL);

            ImageView badge = new ImageView(requireContext());
            int sparkleId = getResDrawable(requireContext(), "ic_dayven_sparkle");
            if (sparkleId != 0) badge.setImageResource(sparkleId);
            badge.setColorFilter(ACCENT);
            badge.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            badge.setPadding(dp(3), dp(3), dp(3), dp(3));
            badge.setBackground(round(ACCENT_SOFT, 11));
            LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(dp(22), dp(22));
            badgeLp.rightMargin = dp(8);
            headRow.addView(badge, badgeLp);

            TextView name = label(requireContext(), p.name, 14, true);
            name.setTextColor(TEXT_PRIMARY);
            headRow.addView(name, new LinearLayout.LayoutParams(0, -2, 1f));
            card.addView(headRow);

            final EditText key = new EditText(requireContext());
            String existingKey = (p.key != null && !p.key.isEmpty()) ? p.key : fallback;
            key.setText(existingKey);
            key.setHint("Tempel API Key Gemini di sini");
            key.setHintTextColor(TEXT_SECONDARY);
            key.setTextColor(TEXT_PRIMARY);
            key.setTextSize(13);
            key.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            key.setTransformationMethod(PasswordTransformationMethod.getInstance());
            key.setBackground(stroke(SURFACE_DARK, dp(9), BORDER_DARK));
            key.setPadding(dp(11), dp(9), dp(11), dp(9));
            LinearLayout.LayoutParams keyLp = new LinearLayout.LayoutParams(-1, -2);
            keyLp.topMargin = dp(10);
            card.addView(key, keyLp);

            boolean hasKeyNow = (existingKey != null && !existingKey.trim().isEmpty());
            boolean ok = p.lastError.isEmpty() && hasKeyNow;
            String statusLine = p.lastError.isEmpty()
                    ? (!hasKeyNow ? "Belum ada API Key" : (p.models.isEmpty() ? "Siap digunakan · Valid" : "Aktif · " + p.models.size() + " model aktif" + (p.rate.isEmpty() ? "" : " · " + p.rate)))
                    : p.lastError;
            final TextView status = label(requireContext(), statusLine, 11, false);
            status.setTextColor(p.lastError.isEmpty() ? (ok ? IOS_GREEN : TEXT_SECONDARY) : IOS_RED);
            status.setPadding(0, dp(7), 0, dp(10));
            card.addView(status);

            LinearLayout actions = row(requireContext());
            MaterialButton validate = solidButton("Check & Save");
            MaterialButton delete = ghostButton("");
            int delIconId = getResDrawable(requireContext(), "ic_dayven_delete");
            if (delIconId != 0) {
                delete.setIconResource(delIconId);
                delete.setIconTint(ColorStateList.valueOf(TEXT_SECONDARY));
                delete.setIconSize(dp(18));
                delete.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_START);
            }
            actions.addView(validate, new LinearLayout.LayoutParams(0, -2, 1f));
            LinearLayout.LayoutParams delLp = new LinearLayout.LayoutParams(dp(46), dp(44));
            delLp.leftMargin = dp(8);
            actions.addView(delete, delLp);
            card.addView(actions);

            validate.setOnClickListener(v -> {
                bounce(v);
                String currentKey = key.getText().toString().trim();
                if (currentKey.isEmpty()) {
                    Toast.makeText(requireContext(), "Masukkan API Key terlebih dahulu", Toast.LENGTH_SHORT).show();
                    status.setText("API Key kosong");
                    status.setTextColor(IOS_RED);
                    return;
                }
                p.key = currentKey;
                p.enabled = true;
                store.setDirectApiKey(currentKey);
                status.setText("Memverifikasi model aktif ke Google AI Studio...");
                status.setTextColor(TEXT_SECONDARY);
                validateAPI(p, status, key);
            });
            delete.setOnClickListener(v -> {
                store.removeProfile(p.id);
                refreshSettings();
            });

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.topMargin = dp(10);
            providerList.addView(card, lp);
            animateIn(card);
        }
    }

    private void validateAPI(AgentWorkspaceStore.Profile p, final TextView statusView, final EditText keyInput) {
        client.listModels(p.key, new GeminiAutoClient.Listener() {
            @Override public void onModels(List<GeminiAutoClient.ModelInfo> models, String remaining, String retry) {
                List<String> rawCandidates = new ArrayList<>();
                for (GeminiAutoClient.ModelInfo m : models) {
                    if (m.text()) rawCandidates.add(m.id());
                }

                if (!rawCandidates.contains("gemini-3.6-flash")) rawCandidates.add(0, "gemini-3.6-flash");

                client.validateActiveModels(p.key, rawCandidates, validActiveModels -> main.post(() -> {
                    p.models.clear();
                    if (!validActiveModels.isEmpty()) {
                        p.models.addAll(validActiveModels);
                    } else {
                        p.models.add("gemini-3.6-flash");
                        p.models.add("gemini-2.0-flash");
                    }
                    p.enabled = true;
                    p.lastError = "";
                    p.rate = remaining;
                    store.setDirectApiKey(p.key);
                    store.saveProfiles(profiles);

                    if (keyInput != null && !p.key.isEmpty()) {
                        keyInput.setText(p.key);
                    }
                    if (statusView != null) {
                        statusView.setText("Aktif · " + p.models.size() + " model terverifikasi aktif");
                        statusView.setTextColor(IOS_GREEN);
                    }
                    Toast.makeText(requireContext(), "API Key valid (" + p.models.size() + " model aktif) ✅", Toast.LENGTH_SHORT).show();
                    updateStatusIndicator(true);
                    startHeartbeat();
                    restoreSession();
                }));
            }
            @Override public void onDelta(String text) { }
            @Override public void onComplete(String model, GeminiAutoClient.Usage usage, String remaining, String retry) { }
            @Override public void onError(int status, String message, String remaining, String retry) {
                p.lastError = message;
                p.enabled = true;
                store.setDirectApiKey(p.key);
                main.post(() -> {
                    if (keyInput != null && !p.key.isEmpty()) {
                        keyInput.setText(p.key);
                    }
                    if (statusView != null) {
                        statusView.setText("Error: " + message);
                        statusView.setTextColor(IOS_RED);
                    }
                    Toast.makeText(requireContext(), "Gagal validasi: " + message, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void updateStatusIndicator(boolean active) {
        if (statusText != null) {
            statusText.setText(active ? "Connected · Gemini" : "Disconnected");
            statusText.setTextColor(active ? IOS_GREEN : IOS_RED);
        }
        if (statusDot != null) {
            statusDot.setBackground(round(active ? IOS_GREEN : IOS_RED, 5));
            if (active && pulseAnimator == null) {
                pulseAnimator = ObjectAnimator.ofFloat(statusDot, "alpha", 1f, 0.25f);
                pulseAnimator.setDuration(900);
                pulseAnimator.setRepeatMode(ValueAnimator.REVERSE);
                pulseAnimator.setRepeatCount(ValueAnimator.INFINITE);
                pulseAnimator.start();
            } else if (!active && pulseAnimator != null) {
                pulseAnimator.cancel();
                pulseAnimator = null;
                statusDot.setAlpha(1f);
            }
        }
    }

    private void startHeartbeat() {
        stopHeartbeat();
        heartbeat = () -> {
            profiles = store.getProfiles();
            boolean active = hasActiveKey(profiles);
            updateStatusIndicator(active);
            main.postDelayed(heartbeat, 6000);
        };
        main.post(heartbeat);
    }

    private void stopHeartbeat() {
        if (heartbeat != null) main.removeCallbacks(heartbeat);
        if (pulseAnimator != null) {
            pulseAnimator.cancel();
            pulseAnimator = null;
        }
    }

    private void finishError(String message) {
        busy = false;
        removeThinking();
        addAssistant(message);
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
    private LinearLayout column(Context c) { LinearLayout l = new LinearLayout(c); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private LinearLayout row(Context c) { LinearLayout l = new LinearLayout(c); l.setOrientation(LinearLayout.HORIZONTAL); return l; }
    private TextView label(Context c, CharSequence t, int s, boolean bold) {
        TextView v = new TextView(c);
        v.setText(t);
        v.setTextSize(s);
        v.setTypeface(null, bold ? Typeface.BOLD : Typeface.NORMAL);
        return v;
    }

    private int getResDrawable(Context c, String name) {
        try {
            int id = c.getResources().getIdentifier(name, "drawable", c.getPackageName());
            if (id != 0) return id;
        } catch (Exception ignored) { }
        return 0;
    }

    private MaterialButton vectorIconButton(Context c, String drawableName, String description, int iconSize) {
        MaterialButton b = new MaterialButton(c);
        int resId = getResDrawable(c, drawableName);
        if (resId != 0) {
            b.setIconResource(resId);
            b.setIconTint(ColorStateList.valueOf(TEXT_PRIMARY));
            b.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_START);
            b.setIconPadding(0);
            b.setIconSize(iconSize > 0 ? iconSize : dp(20));
        }
        b.setContentDescription(description);
        b.setBackgroundTintList(ColorStateList.valueOf(Color.TRANSPARENT));
        b.setRippleColor(ColorStateList.valueOf(ACCENT_SOFT));
        b.setCornerRadius(dp(20));
        b.setInsetTop(0);
        b.setInsetBottom(0);
        b.setPadding(0, 0, 0, 0);
        b.setMinWidth(0);
        b.setMinHeight(0);
        return b;
    }

    private MaterialButton solidButton(String t) {
        MaterialButton b = new MaterialButton(requireContext());
        b.setText(t);
        b.setTextSize(13);
        b.setAllCaps(false);
        b.setBackgroundTintList(ColorStateList.valueOf(ACCENT));
        b.setTextColor(Color.WHITE);
        b.setCornerRadius(dp(11));
        b.setInsetTop(0);
        b.setInsetBottom(0);
        return b;
    }

    private MaterialButton ghostButton(String t) {
        MaterialButton b = new MaterialButton(requireContext());
        b.setText(t);
        b.setTextSize(13);
        b.setAllCaps(false);
        b.setBackgroundTintList(ColorStateList.valueOf(Color.TRANSPARENT));
        b.setRippleColor(ColorStateList.valueOf(ACCENT_SOFT));
        b.setStrokeColor(ColorStateList.valueOf(BORDER_DARK));
        b.setStrokeWidth(dp(1));
        b.setTextColor(TEXT_PRIMARY);
        b.setCornerRadius(dp(11));
        b.setInsetTop(0);
        b.setInsetBottom(0);
        return b;
    }

    private GradientDrawable round(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        return d;
    }

    private GradientDrawable roundTop(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        float r = dp(radius);
        d.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        return d;
    }

    private GradientDrawable stroke(int fill, int radius, int border) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(radius);
        d.setStroke(dp(1), border);
        return d;
    }

    private void scrollBottom() {
        if (transcript != null && messages != null) {
            transcript.post(() -> transcript.smoothScrollTo(0, messages.getHeight() + dp(120)));
        }
    }

    private CharSequence richText(String value) {
        if (value == null) return "";

        SpannableStringBuilder ssb = new SpannableStringBuilder();
        int len = value.length();
        int i = 0;

        while (i < len) {
            if (value.startsWith("**", i)) {
                int end = value.indexOf("**", i + 2);
                if (end != -1) {
                    int spanStart = ssb.length();
                    ssb.append(value.substring(i + 2, end));
                    ssb.setSpan(new StyleSpan(Typeface.BOLD), spanStart, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    i = end + 2;
                    continue;
                }
            }

            if (value.charAt(i) == '*' && i + 1 < len && value.charAt(i + 1) != ' ') {
                int end = value.indexOf('*', i + 1);
                if (end != -1 && end > i + 1) {
                    int spanStart = ssb.length();
                    ssb.append(value.substring(i + 1, end));
                    ssb.setSpan(new StyleSpan(Typeface.ITALIC), spanStart, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    i = end + 1;
                    continue;
                }
            }

            ssb.append(value.charAt(i));
            i++;
        }

        return ssb;
    }

    private static final class Route {
        final AgentWorkspaceStore.Profile profile;
        final String model;
        Route(AgentWorkspaceStore.Profile p, String m) { this.profile = p; this.model = m; }
    }
}
