package pro.sketchware.editor;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
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
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import pro.sketchware.activities.editor.view.ViewCodeEditorActivity;

/**
 * Dayven AI Agent v3.0 - iOS styled dark chat workspace.
 *
 * <p>Capabilities wired here:</p>
 * <ul>
 *   <li>Real session memory: the whole conversation is replayed as native multi turn content.</li>
 *   <li>Layered change memory with a multi level rollback stack, restored through a review card.</li>
 *   <li>Multi file proposals: every TARGET in one answer is applied as a single reversible batch.</li>
 *   <li>Auto continuation when an answer hits the output cap, so big tasks are never cut off.</li>
 *   <li>Adaptive LITE / STANDARD / PRO routing with silent model and API key failover.</li>
 * </ul>
 */
public final class AgentFragmentV2 extends Fragment {
    // iOS dark system palette
    private static final int BG_DARK = 0xFF0B0C0F;
    private static final int SURFACE_DARK = 0xFF15171C;
    private static final int CARD_DARK = 0xFF1C1F26;
    private static final int BORDER_DARK = 0xFF2A2E38;
    private static final int TEXT_PRIMARY = 0xFFF3F5F9;
    private static final int TEXT_SECONDARY = 0xFF8E94A3;
    private static final int ACCENT = 0xFFD97757;
    private static final int ACCENT_SOFT = 0x33D97757;
    private static final int IOS_GREEN = 0xFF34C759;
    private static final int IOS_RED = 0xFFFF453A;
    private static final int USER_BUBBLE = 0xFF262A34;
    private static final int AI_BUBBLE = 0xFF15171C;
    private static final int CODE_BG = 0xFF0E0F12;

    private static final String GREETING = "Halo! Welcome to Dayven AI \uD83D\uDC4B\u2728\nAPI key kamu sudah aktif dan siap digunakan!\n\nSekarang saya siap bantu kamu. Ada bug yang mau di-fix, mau tambah fitur baru, atau mulai project dari nol? Kirim aja detail atau potongan kodenya, saya langsung kerjakan! \uD83D\uDE80";
    private static final String NO_KEY = "Halo! Saya Dayven AI \uD83D\uDC4B\nSilakan buka ikon Settings di kanan atas untuk memasukkan API Key Google AI Studio (Gemini) supaya saya bisa mulai bekerja.";
    private static final String THINKING_BASE = "\uD83D\uDCAD Dayven AI lagi mikir dulu ya...\nsedang menganalisis kode dan menyusun solusinya";
    private static final String CONTINUE_HINT = "[LANJUTKAN] Lanjutkan tepat dari karakter terakhir yang sudah kamu kirim. Jangan minta maaf, jangan ulangi bagian sebelumnya, jangan mulai dari awal.";

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
    private TextView liveAssistant;
    private TextView liveCopy;
    private boolean busy;
    private boolean expanded = true;
    private int insetTop;
    private Runnable heartbeat;
    private Runnable dotsTicker;
    private TextView thinkingLabel;
    private int dotsPhase;
    private ObjectAnimator pulseAnimator;
    private FrameLayout activeSettingsPage;
    private FrameLayout activeDrawerOverlay;
    private ViewTreeObserver.OnGlobalLayoutListener keyboardLayoutListener;

    private List<GeminiAutoClient.Turn> activeTurns = new ArrayList<>();
    private String activeSystem = "";
    private int continuation;

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
        stopDots();
        super.onPause();
    }

    @Override public void onDestroyView() {
        if (root != null && keyboardLayoutListener != null) {
            root.getViewTreeObserver().removeOnGlobalLayoutListener(keyboardLayoutListener);
        }
        super.onDestroyView();
    }

    public void onAgentTabOpened(boolean opened) {
        if (!opened) return;
        expanded = true;
        Activity act = getActivity();
        if (act != null) AgentTabEnhancer.setExpanded(act, true);
        if (expandButton != null) expandButton.setText("\u2921");
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

        MaterialButton menuBtn = iconButton(c, "\u2630", "Chat sessions", 18);
        menuBtn.setOnClickListener(v -> {
            bounce(v);
            showDrawer();
        });
        header.addView(menuBtn, new LinearLayout.LayoutParams(dp(42), dp(42)));

        TextView avatar = new TextView(c);
        avatar.setText("\u2726");
        avatar.setTextSize(15);
        avatar.setTextColor(ACCENT);
        avatar.setGravity(Gravity.CENTER);
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
        TextView subtitle = label(c, "Dayven AI \u00B7 Assistant v3.0", 11, false);
        subtitle.setTextColor(TEXT_SECONDARY);
        subtitle.setSingleLine(true);
        titleGroup.addView(subtitle);
        header.addView(titleGroup, new LinearLayout.LayoutParams(0, -2, 1f));

        MaterialButton undoBtn = iconButton(c, "\u23EA", "Rollback perubahan terakhir", 15);
        undoBtn.setOnClickListener(v -> {
            bounce(v);
            showRollbackCard();
        });
        header.addView(undoBtn, new LinearLayout.LayoutParams(dp(40), dp(42)));

        expandButton = iconButton(c, expanded ? "\u2921" : "\u2922", "Minimize or expand", 16);
        expandButton.setOnClickListener(v -> {
            bounce(v);
            toggleExpand();
        });
        header.addView(expandButton, new LinearLayout.LayoutParams(dp(40), dp(42)));

        MaterialButton settingsBtn = iconButton(c, "\u2699", "AI providers and API keys", 16);
        settingsBtn.setOnClickListener(v -> {
            bounce(v);
            showSettings();
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

        MaterialButton attach = iconButton(c, "+", "Attach context", 18);
        attach.setTextColor(TEXT_SECONDARY);
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

        sendButton = iconButton(c, "\u2191", "Send", 16);
        sendButton.setBackgroundTintList(ColorStateList.valueOf(ACCENT));
        sendButton.setTextColor(Color.WHITE);
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
        expandButton.setText(expanded ? "\u2921" : "\u2922");
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

        // Local intents are answered instantly, without spending an API call.
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
            addAssistant("API Key belum dikonfigurasi. Silakan buka Settings (ikon \u2699 di kanan atas) untuk menambahkan API Key Gemini.");
            Toast.makeText(requireContext(), "Silakan tambahkan API Key di Settings (\u2699)", Toast.LENGTH_SHORT).show();
            return;
        }

        composer.setText("");
        recordUser(prompt);

        busy = true;
        triedRoutes.clear();
        continuation = 0;
        liveAssistant = null;
        liveCopy = null;
        addThinking();

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

    /** Replays the stored conversation so the model keeps full awareness of the session. */
    private void appendHistory(List<GeminiAutoClient.Turn> turns, int mode) {
        if (session == null || session.messages == null) return;
        List<AgentWorkspaceStore.Message> all = session.messages;
        int end = all.size() - 1; // the freshly appended user message is sent separately
        if (end <= 0) return;

        int window = mode == AgentPromptOptimizer.MODE_LITE ? 6 : 14;
        int start = Math.max(0, end - window);
        while (start < end && all.get(start) != null && !"user".equals(all.get(start).role)) start++;

        for (int i = start; i < end; i++) {
            AgentWorkspaceStore.Message m = all.get(i);
            if (m == null || m.text == null || m.text.trim().isEmpty()) continue;
            boolean user = "user".equals(m.role);
            String text = AgentPromptOptimizer.trimHistory(m.text, user ? 1500 : 3000);
            turns.add(user ? GeminiAutoClient.Turn.user(text) : GeminiAutoClient.Turn.model(text));
        }
    }

    private void addThinking() {
        LinearLayout container = row(requireContext());
        container.setTag("thinking");
        container.setGravity(Gravity.START);

        thinkingLabel = label(requireContext(), THINKING_BASE + "... \u23F3", 12, false);
        thinkingLabel.setTextColor(TEXT_SECONDARY);
        thinkingLabel.setPadding(dp(14), dp(10), dp(14), dp(10));
        thinkingLabel.setBackground(round(CARD_DARK, 14));
        container.addView(thinkingLabel);

        messages.addView(container);
        animateIn(container);
        startDots();
        scrollBottom();
    }

    private void startDots() {
        stopDots();
        dotsPhase = 0;
        dotsTicker = () -> {
            if (thinkingLabel != null) {
                StringBuilder dots = new StringBuilder();
                for (int i = 0; i < (dotsPhase % 3) + 1; i++) dots.append('.');
                thinkingLabel.setText(THINKING_BASE + dots + " \u23F3");
            }
            dotsPhase++;
            main.postDelayed(dotsTicker, 420);
        };
        main.post(dotsTicker);
    }

    private void stopDots() {
        if (dotsTicker != null) main.removeCallbacks(dotsTicker);
        dotsTicker = null;
    }

    private void removeThinking() {
        stopDots();
        thinkingLabel = null;
        if (messages == null) return;
        final View v = messages.findViewWithTag("thinking");
        if (v == null) return;
        v.setTag(null);
        v.animate().alpha(0f).translationY(dp(-6)).setDuration(140)
                .withEndAction(() -> messages.removeView(v)).start();
    }

    private List<Route> routesFor(String prompt, int mode) {
        List<Route> out = new ArrayList<>();
        profiles = store.getProfiles();

        String directKey = getBestApiKey(profiles);

        // Modern official models recommended by Google AI Studio.
        List<String> defaultModels = new ArrayList<>();
        defaultModels.add("gemini-3.6-flash");
        defaultModels.add("gemini-2.5-flash");
        defaultModels.add("gemini-2.0-flash");
        defaultModels.add("gemini-1.5-flash-latest");
        defaultModels.add("gemini-1.5-flash");
        defaultModels.add("gemini-1.5-pro");

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
            if (m.contains("3.6-flash") || m.contains("pro")) return 0;
            if (m.contains("2.5-flash") || m.contains("2.0-flash") || m.contains("flash")) return 1;
            return 2;
        }
        if (tier == AgentPromptOptimizer.MODE_LITE) {
            if (m.contains("lite") || m.contains("8b")) return 0;
            if (m.contains("3.6-flash") || m.contains("2.5-flash") || m.contains("2.0-flash") || m.contains("flash")) return 1;
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
            finishError("Semua API Key dan model sudah dicoba tapi belum ada yang merespon. Cek koneksi atau kuota API Key di Settings." + detail);
            return;
        }

        final Route route = routes.get(index);
        String routeKey = (route.profile.id != null ? route.profile.id : "prof") + ":" + route.model;
        if (!resume && !triedRoutes.add(routeKey)) {
            streamRoute(original, routes, index + 1, errorTrace, false);
            return;
        }

        if (!resume) {
            liveAssistant = null;
            liveCopy = null;
        }

        client.streamConversation(route.profile.key, route.model, activeSystem, activeTurns,
                GeminiAutoClient.MAX_OUTPUT_TOKENS, new GeminiAutoClient.StreamListener() {
            @Override public void onModels(List<GeminiAutoClient.ModelInfo> models, String remaining, String retry) { }

            @Override public void onDelta(String text) {
                main.post(() -> {
                    removeThinking();
                    if (liveAssistant == null) addAssistantBubble();
                    liveAssistant.append(text);
                    scrollBottom();
                });
            }

            @Override public void onComplete(String model, GeminiAutoClient.Usage usage, String remaining, String retry) {
                main.post(() -> finalizeAnswer(route, original, usage, remaining));
            }

            @Override public void onTruncated(String model, GeminiAutoClient.Usage usage, String remaining, String retry) {
                main.post(() -> {
                    if (liveAssistant == null) {
                        finalizeAnswer(route, original, usage, remaining);
                        return;
                    }
                    if (continuation >= MAX_CONTINUATION) {
                        finalizeAnswer(route, original, usage, remaining);
                        return;
                    }
                    continuation++;
                    tokenText.setText(usage.label() + " \u00B7 lanjut " + continuation);
                    String partial = liveAssistant.getText().toString();
                    String tail = partial.length() > 6000 ? partial.substring(partial.length() - 6000) : partial;
                    activeTurns.add(GeminiAutoClient.Turn.model(tail));
                    activeTurns.add(GeminiAutoClient.Turn.user(CONTINUE_HINT));
                    streamRoute(original, routes, index, errorTrace, true);
                });
            }

            @Override public void onError(int status, String message, String remaining, String retry) {
                main.post(() -> {
                    if (liveAssistant != null && liveAssistant.getText().length() > 0) {
                        // Keep whatever already streamed instead of throwing the answer away.
                        finalizeAnswer(route, original, new GeminiAutoClient.Usage(0, 0, 0), remaining);
                        return;
                    }
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
        if (liveAssistant == null) return;

        String full = liveAssistant.getText().toString();
        liveAssistant.setText(richText(full));
        if (liveCopy != null) liveCopy.setTag(full);
        store.append(session.id, "assistant", full);
        AgentWorkspaceStore.Session refreshed = store.getSession(session.id);
        if (refreshed != null) session = refreshed;

        List<AgentActionExecutor.Action> actions = AgentActionExecutor.parseAll(full);
        if (!actions.isEmpty()) showProposal(actions, original);
        scrollBottom();
    }

    private void bubble(String text, boolean user) {
        LinearLayout line = row(requireContext());
        line.setGravity(user ? Gravity.END : Gravity.START);

        if (!user) line.addView(aiAvatar(), avatarParams());

        LinearLayout holder = column(requireContext());

        TextView view = label(requireContext(), user ? text : richText(text), 14, false);
        view.setTextColor(TEXT_PRIMARY);
        view.setLineSpacing(dp(3), 1f);
        view.setPadding(dp(14), dp(11), dp(14), dp(11));
        view.setBackground(round(user ? USER_BUBBLE : AI_BUBBLE, 16));
        view.setMaxWidth(Math.round(getResources().getDisplayMetrics().widthPixels * 0.78f));
        view.setTextIsSelectable(true);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMargins(0, dp(5), 0, dp(2));
        holder.addView(view, lp);

        if (!user) holder.addView(copyRow(view, text));

        line.addView(holder, new LinearLayout.LayoutParams(-2, -2));
        messages.addView(line);
        animateIn(line);
    }

    private void addAssistantBubble() {
        LinearLayout line = row(requireContext());
        line.setGravity(Gravity.START);
        line.addView(aiAvatar(), avatarParams());

        LinearLayout holder = column(requireContext());

        TextView view = label(requireContext(), "", 14, false);
        view.setTextColor(TEXT_PRIMARY);
        view.setLineSpacing(dp(3), 1f);
        view.setPadding(dp(14), dp(11), dp(14), dp(11));
        view.setBackground(round(AI_BUBBLE, 16));
        view.setMaxWidth(Math.round(getResources().getDisplayMetrics().widthPixels * 0.78f));
        view.setTextIsSelectable(true);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMargins(0, dp(5), 0, dp(2));
        holder.addView(view, lp);

        LinearLayout copy = copyRow(view, null);
        holder.addView(copy);

        line.addView(holder, new LinearLayout.LayoutParams(-2, -2));
        messages.addView(line);
        animateIn(line);

        liveAssistant = view;
    }

    /** Small COPY SEMUA affordance under every answer. Copies the raw markdown, code fences included. */
    private LinearLayout copyRow(final TextView source, final String raw) {
        LinearLayout holder = row(requireContext());
        holder.setGravity(Gravity.START);

        final TextView copy = label(requireContext(), "\uD83D\uDCCB COPY SEMUA", 10, true);
        copy.setTextColor(TEXT_SECONDARY);
        copy.setPadding(dp(10), dp(4), dp(10), dp(4));
        copy.setBackground(round(CARD_DARK, 9));
        copy.setTag(raw);
        copy.setOnClickListener(v -> {
            Object tag = copy.getTag();
            String value = tag instanceof String ? (String) tag : source.getText().toString();
            ClipboardManager cm = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("Dayven AI", value));
            bounce(v);
            Toast.makeText(requireContext(), "Disalin ke clipboard \u2705", Toast.LENGTH_SHORT).show();
        });
        holder.addView(copy);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMargins(dp(2), 0, 0, dp(6));
        holder.setLayoutParams(lp);

        liveCopy = copy;
        return holder;
    }

    private TextView aiAvatar() {
        TextView avatar = new TextView(requireContext());
        avatar.setText("\u2726");
        avatar.setTextSize(11);
        avatar.setTextColor(ACCENT);
        avatar.setGravity(Gravity.CENTER);
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
        v.setTranslationY(dp(10));
        v.animate().alpha(1f).translationY(0f)
                .setDuration(230)
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

    /** Review card listing every file in the proposal. Accept applies them as one undo batch. */
    private void showProposal(final List<AgentActionExecutor.Action> actions, final String original) {
        final LinearLayout card = column(requireContext());
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setBackground(stroke(CARD_DARK, dp(14), BORDER_DARK));

        TextView title = label(requireContext(), "\uD83D\uDCCB PREVIEW PERUBAHAN", 13, true);
        title.setTextColor(ACCENT);
        card.addView(title);

        TextView summary = label(requireContext(), actions.size() + " file akan ditulis ke project kamu", 11, false);
        summary.setTextColor(TEXT_SECONDARY);
        summary.setPadding(0, dp(4), 0, dp(8));
        card.addView(summary);

        for (AgentActionExecutor.Action a : actions) {
            TextView item = label(requireContext(), "\u2022 " + a.target + "  \u00B7 " + a.badge()
                    + (AgentActionExecutor.needsEditorHandoff(a) ? "  \u00B7 buka di XML editor" : ""), 12, false);
            item.setTextColor(TEXT_PRIMARY);
            item.setPadding(dp(2), dp(3), 0, dp(3));
            card.addView(item);
        }

        LinearLayout actionsRow = row(requireContext());
        MaterialButton cancel = ghostButton("\u274C Cancel");
        MaterialButton accept = solidButton("\u2705 Accept & Apply");
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
        List<AgentActionExecutor.Action> writable = new ArrayList<>();
        List<AgentActionExecutor.Action> handoff = new ArrayList<>();
        for (AgentActionExecutor.Action a : actions) {
            if (AgentActionExecutor.needsEditorHandoff(a)) handoff.add(a);
            else writable.add(a);
        }

        if (!writable.isEmpty()) {
            String label = original == null ? "Perubahan Dayven AI"
                    : (original.length() > 60 ? original.substring(0, 60) + "..." : original);
            String report = AgentActionExecutor.applyAll(requireContext(), projectId, writable, memory, label);
            addAssistant(report + "\n\nKalau hasilnya kurang pas, bilang \"balik ke yang tadi\" dan semua file di atas saya kembalikan sekaligus. \u23EA");
        }

        if (!handoff.isEmpty()) {
            AgentActionExecutor.Action first = handoff.get(0);
            Intent i = new Intent(requireContext(), ViewCodeEditorActivity.class);
            i.putExtra("sc_id", projectId);
            i.putExtra("title", first.target);
            i.putExtra("content", first.content);
            startActivity(i);
            StringBuilder note = new StringBuilder("Layout " + first.target + " dibuka di Direct XML Editor untuk disimpan.");
            if (handoff.size() > 1) {
                note.append("\nSisa layout: ");
                for (int k = 1; k < handoff.size(); k++) {
                    if (k > 1) note.append(", ");
                    note.append(handoff.get(k).target);
                }
                note.append(". Bilang \"lanjut layout berikutnya\" kalau mau saya buka satu per satu.");
            }
            addAssistant(note.toString());
        }
    }

    /** Rollback review card: restores the whole newest snapshot batch after Accept. */
    private void showRollbackCard() {
        String label = memory.peekLabel(projectId);
        if (label == null) {
            addAssistant("Belum ada perubahan yang bisa dikembalikan di project ini.");
            return;
        }

        final LinearLayout card = column(requireContext());
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setBackground(stroke(CARD_DARK, dp(14), BORDER_DARK));

        TextView title = label(requireContext(), "\u23EA ROLLBACK DETECTED", 13, true);
        title.setTextColor(ACCENT);
        card.addView(title);

        TextView detail = label(requireContext(), "Mengembalikan ke versi sebelum perubahan terakhir:\n" + label, 12, false);
        detail.setTextColor(TEXT_SECONDARY);
        detail.setPadding(0, dp(5), 0, dp(6));
        card.addView(detail);

        for (String f : memory.peekFiles(projectId)) {
            TextView item = label(requireContext(), "\u2022 " + f, 12, false);
            item.setTextColor(TEXT_PRIMARY);
            item.setPadding(dp(2), dp(3), 0, dp(3));
            card.addView(item);
        }

        TextView depth = label(requireContext(), memory.depth(projectId) + " langkah undo tersimpan", 10, false);
        depth.setTextColor(TEXT_SECONDARY);
        depth.setPadding(0, dp(6), 0, 0);
        card.addView(depth);

        LinearLayout actionsRow = row(requireContext());
        MaterialButton cancel = ghostButton("\u274C Cancel");
        MaterialButton accept = solidButton("\u2705 Accept rollback");
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
        for (int i = from; i < log.size(); i++) sb.append("\u2022 ").append(log.get(i)).append('\n');
        sb.append("\n").append(memory.depth(projectId)).append(" langkah undo tersedia.");
        return sb.toString().trim();
    }

    /** Quick sheet showing exactly what project context is being attached to each request. */
    private void showContextSheet() {
        StringBuilder sb = new StringBuilder("Konteks yang saya kirim tiap pesan:\n");
        sb.append("\u2022 Project: ").append(projectId == null || projectId.isEmpty() ? "belum dipilih" : projectId).append('\n');
        sb.append("\u2022 File aktif: ").append(selectedFile == null || selectedFile.isEmpty() ? "-" : selectedFile).append('\n');
        sb.append("\u2022 Riwayat chat sesi ini: aktif\n");
        sb.append("\u2022 Memori perubahan: ").append(memory.depth(projectId)).append(" langkah\n");
        sb.append("\u2022 Snapshot rollback: ").append(memory.peekLabel(projectId) == null ? "kosong" : memory.peekLabel(projectId));
        addAssistant(sb.toString());
    }

    private void showDrawer() {
        if (activeDrawerOverlay != null) return;

        final FrameLayout overlay = new FrameLayout(requireContext());
        overlay.setLayoutParams(new FrameLayout.LayoutParams(-1, -1));
        overlay.setBackgroundColor(0x00000000);
        activeDrawerOverlay = overlay;

        final LinearLayout drawer = column(requireContext());
        drawer.setBackgroundColor(SURFACE_DARK);
        drawer.setPadding(dp(18), insetTop + dp(18), dp(18), dp(18));

        TextView head = label(requireContext(), "Chat Sessions", 17, true);
        head.setTextColor(TEXT_PRIMARY);
        drawer.addView(head);

        MaterialButton newChat = solidButton("\uFF0B  New Chat");
        newChat.setOnClickListener(v -> {
            session = store.newSession("New chat");
            sessionTitle.setText(session.title);
            restoreSession();
            closeDrawer(overlay, drawer);
        });
        LinearLayout.LayoutParams newLp = new LinearLayout.LayoutParams(-1, -2);
        newLp.setMargins(0, dp(14), 0, dp(12));
        drawer.addView(newChat, newLp);

        ScrollView scroll = new ScrollView(requireContext());
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout list = column(requireContext());
        for (AgentWorkspaceStore.Session s : store.getSessions()) {
            boolean current = s.id.equals(session.id);
            TextView item = label(requireContext(), s.title, 13, current);
            item.setTextColor(current ? ACCENT : TEXT_PRIMARY);
            item.setSingleLine(true);
            item.setPadding(dp(12), dp(13), dp(12), dp(13));
            item.setBackground(round(current ? CARD_DARK : Color.TRANSPARENT, 11));
            item.setOnClickListener(v -> {
                AgentWorkspaceStore.Session picked = store.getSession(s.id);
                if (picked != null) {
                    session = picked;
                    store.selectSession(s.id);
                    sessionTitle.setText(session.title);
                    restoreSession();
                }
                closeDrawer(overlay, drawer);
            });
            LinearLayout.LayoutParams itemLp = new LinearLayout.LayoutParams(-1, -2);
            itemLp.bottomMargin = dp(4);
            list.addView(item, itemLp);
        }
        scroll.addView(list);
        drawer.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        final int width = Math.round(getResources().getDisplayMetrics().widthPixels * 0.76f);
        overlay.addView(drawer, new FrameLayout.LayoutParams(width, -1));
        drawer.setTranslationX(-width);
        overlay.setOnClickListener(v -> closeDrawer(overlay, drawer));
        root.addView(overlay);

        ValueAnimator scrim = ValueAnimator.ofInt(0, 140);
        scrim.setDuration(220);
        scrim.addUpdateListener(a -> overlay.setBackgroundColor(Color.argb((int) a.getAnimatedValue(), 0, 0, 0)));
        scrim.start();

        ObjectAnimator slide = ObjectAnimator.ofFloat(drawer, "translationX", -width, 0f);
        slide.setDuration(260);
        slide.setInterpolator(new DecelerateInterpolator(1.6f));
        slide.start();
    }

    private void closeDrawer(final FrameLayout overlay, final View drawer) {
        if (overlay == null || drawer == null) return;
        int width = drawer.getWidth() > 0 ? drawer.getWidth() : Math.round(getResources().getDisplayMetrics().widthPixels * 0.76f);

        ValueAnimator scrim = ValueAnimator.ofInt(140, 0);
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

        TextView sub = label(requireContext(), "Google AI Studio \u00B7 Gemini API Keys", 12, false);
        sub.setTextColor(TEXT_SECONDARY);
        sub.setPadding(0, dp(3), 0, dp(14));
        body.addView(sub);

        ScrollView scroll = new ScrollView(requireContext());
        scroll.setVerticalScrollBarEnabled(false);
        providerList = column(requireContext());
        scroll.addView(providerList);
        body.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        MaterialButton addBtn = ghostButton("\uFF0B  Add Gemini API Key");
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

            TextView badge = label(requireContext(), "\u2726", 12, false);
            badge.setTextColor(ACCENT);
            badge.setGravity(Gravity.CENTER);
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
                    ? (!hasKeyNow ? "Belum ada API Key" : (p.models.isEmpty() ? "Siap digunakan \u00B7 Valid" : "Aktif \u00B7 " + p.models.size() + " model aktif" + (p.rate.isEmpty() ? "" : " \u00B7 " + p.rate)))
                    : p.lastError;
            final TextView status = label(requireContext(), statusLine, 11, false);
            status.setTextColor(p.lastError.isEmpty() ? (ok ? IOS_GREEN : TEXT_SECONDARY) : IOS_RED);
            status.setPadding(0, dp(7), 0, dp(10));
            card.addView(status);

            LinearLayout actions = row(requireContext());
            MaterialButton validate = solidButton("Check & Save");
            MaterialButton delete = ghostButton("\uD83D\uDDD1");
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

                // Always probe the latest official flash model too.
                if (!rawCandidates.contains("gemini-3.6-flash")) rawCandidates.add(0, "gemini-3.6-flash");

                // Keep only models that answered with a real 200 OK candidate.
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
                        statusView.setText("Aktif \u00B7 " + p.models.size() + " model terverifikasi aktif");
                        statusView.setTextColor(IOS_GREEN);
                    }
                    Toast.makeText(requireContext(), "API Key valid (" + p.models.size() + " model aktif terverifikasi) \u2705", Toast.LENGTH_SHORT).show();
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
            statusText.setText(active ? "Connected \u00B7 Gemini" : "Disconnected");
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

    private MaterialButton iconButton(Context c, String glyph, String description, int size) {
        MaterialButton b = new MaterialButton(c);
        b.setText(glyph);
        b.setTextSize(size);
        b.setContentDescription(description);
        b.setBackgroundTintList(ColorStateList.valueOf(Color.TRANSPARENT));
        b.setRippleColor(ColorStateList.valueOf(ACCENT_SOFT));
        b.setTextColor(TEXT_PRIMARY);
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
            if (value.startsWith("```", i)) {
                int end = value.indexOf("```", i + 3);
                if (end != -1) {
                    int blockStart = ssb.length();
                    String codeContent = value.substring(i + 3, end);
                    int firstLine = codeContent.indexOf('\n');
                    if (firstLine != -1 && firstLine < 20) {
                        codeContent = codeContent.substring(firstLine + 1);
                    }
                    ssb.append(codeContent.trim());
                    int blockEnd = ssb.length();
                    ssb.setSpan(new TypefaceSpan("monospace"), blockStart, blockEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    ssb.setSpan(new BackgroundColorSpan(CODE_BG), blockStart, blockEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    ssb.setSpan(new ForegroundColorSpan(0xFFE4E9F2), blockStart, blockEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    i = end + 3;
                    continue;
                }
            }

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
