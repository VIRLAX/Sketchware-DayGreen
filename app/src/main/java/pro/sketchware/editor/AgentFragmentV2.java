package pro.sketchware.editor;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
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
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

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
import java.util.regex.Pattern;

import pro.sketchware.activities.editor.view.ViewCodeEditorActivity;

/**
 * Dayven AI Agent - iOS styled dark chat workspace.
 *
 * Layout contract:
 * - The header bar never slides under the device status bar / control center. Its top padding
 *   always equals the real status bar height while the agent runs in expanded mode.
 * - Header is a single horizontal row (menu | title | actions) so nothing can overlap.
 * - Every message, the typing indicator and the drawer animate in softly.
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

    private static final String GREETING = "Halo! Welcome to Dayven AI \uD83D\uDC4B\u2728\nAPI key kamu sudah aktif dan bisa langsung digunakan secara normal ya!\n\nSekarang saya sudah siap bantu kamu. Ada bug yang mau di-fix, atau mau tambahkan fitur dan code baru ke proyek kamu? Kirim saja detail atau potongan kodenya di sini, nanti saya bantu selesaikan! \uD83D\uDE80";
    private static final String NO_KEY = "Halo! Saya Dayven AI \uD83D\uDC4B\nSilakan buka ikon Settings di kanan atas untuk memasukkan API Key Google AI Studio (Gemini) supaya saya bisa mulai bekerja.";
    private static final String THINKING_BASE = "\uD83D\uDCAD Dayven AI lagi mikir dulu ya...\nsedang menganalisis kode dan menyusun solusinya";

    private static final Pattern MUTATING = Pattern.compile("(?i)\\b(create|make|build|generate|fix|repair|change|modify|update|buat|bikin|perbaiki|ubah|layout|xml|java|kotlin|c\\+\\+|block|code|res|library|hubungkan|download)\\b");

    private final Handler main = new Handler(Looper.getMainLooper());
    private final GeminiAutoClient client = new GeminiAutoClient();
    private final Set<String> triedRoutes = new HashSet<>();

    private AgentWorkspaceStore store;
    private AgentWorkspaceStore.Session session;
    private List<AgentWorkspaceStore.Profile> profiles = new ArrayList<>();

    private FrameLayout root;
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
        session = store.getOrCreateCurrent();
        profiles = store.getProfiles();
        insetTop = systemStatusBarHeight();
        requireActivity().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        return build(requireContext());
    }

    @Override public void onResume() {
        super.onResume();
        startHeartbeat();
        applyTopInset();
    }

    @Override public void onPause() {
        stopHeartbeat();
        stopDots();
        super.onPause();
    }

    /** Called by the host when the Agent tab becomes visible. */
    public void onAgentTabOpened(boolean opened) {
        if (!opened) return;
        expanded = true;
        Activity act = getActivity();
        if (act != null) AgentTabEnhancer.setExpanded(act, true);
        if (expandButton != null) expandButton.setText("\u2921");
        applyTopInset();
    }

    private View build(Context c) {
        root = new FrameLayout(c);
        root.setLayoutParams(new ViewGroup.LayoutParams(-1, -1));
        root.setBackgroundColor(BG_DARK);

        LinearLayout shell = column(c);
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
            return insets;
        });
        ViewCompat.requestApplyInsets(root);

        restoreSession();
        applyTopInset();
        return root;
    }

    /** Keeps the header bar parked right below the control center, never under it. */
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
        TextView subtitle = label(c, "Dayven AI \u00B7 Assistant", 11, false);
        subtitle.setTextColor(TEXT_SECONDARY);
        subtitle.setSingleLine(true);
        titleGroup.addView(subtitle);
        header.addView(titleGroup, new LinearLayout.LayoutParams(0, -2, 1f));

        expandButton = iconButton(c, expanded ? "\u2921" : "\u2922", "Minimize or expand", 16);
        expandButton.setOnClickListener(v -> {
            bounce(v);
            toggleExpand();
        });
        header.addView(expandButton, new LinearLayout.LayoutParams(dp(42), dp(42)));

        MaterialButton settingsBtn = iconButton(c, "\u2699", "AI providers and API keys", 16);
        settingsBtn.setOnClickListener(v -> {
            bounce(v);
            showSettings();
        });
        header.addView(settingsBtn, new LinearLayout.LayoutParams(dp(42), dp(42)));

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
        if (session.messages.isEmpty()) {
            boolean active = profiles.stream().anyMatch(p -> p.enabled && !p.key.isEmpty());
            bubble(active ? GREETING : NO_KEY, false);
        } else {
            for (AgentWorkspaceStore.Message m : session.messages) bubble(m.text, "user".equals(m.role));
        }
        scrollBottom();
    }

    private void addAssistant(String text) {
        bubble(text, false);
    }

    private void send() {
        if (busy) return;
        String prompt = composer.getText().toString().trim();
        if (prompt.isEmpty()) return;

        profiles = store.getProfiles();
        if (profiles.stream().noneMatch(p -> p.enabled && !p.key.isEmpty())) {
            addAssistant("API Key belum dikonfigurasi. Silakan buka Settings untuk menambahkan API Key Gemini.");
            showSettings();
            return;
        }

        composer.setText("");
        store.append(session.id, "user", prompt);
        session = store.getSession(session.id);
        sessionTitle.setText(session.title);

        bubble(prompt, true);
        busy = true;
        triedRoutes.clear();
        addThinking();

        String request = AgentPromptOptimizer.build(prompt, AgentProjectContext.build(requireContext(), projectId, selectedFile));
        runDiscoveryThenRoute(request, prompt, 0);
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
        final View v = messages.findViewWithTag("thinking");
        if (v == null) return;
        v.setTag(null);
        v.animate().alpha(0f).translationY(dp(-6)).setDuration(140)
                .withEndAction(() -> messages.removeView(v)).start();
    }

    private void runDiscoveryThenRoute(String request, String original, int index) {
        profiles = store.getProfiles();
        if (index >= profiles.size()) {
            runRoutes(request, original);
            return;
        }

        AgentWorkspaceStore.Profile p = profiles.get(index);
        if (!p.enabled || p.key.isEmpty() || !p.models.isEmpty()) {
            runDiscoveryThenRoute(request, original, index + 1);
            return;
        }

        client.listModels(p.key, new GeminiAutoClient.Listener() {
            @Override public void onModels(List<GeminiAutoClient.ModelInfo> models, String remaining, String retry) {
                p.models.clear();
                for (GeminiAutoClient.ModelInfo m : models) if (m.text()) p.models.add(m.id());
                p.rate = remaining;
                store.saveProfiles(profiles);
                main.post(() -> runDiscoveryThenRoute(request, original, index + 1));
            }
            @Override public void onDelta(String text) { }
            @Override public void onComplete(String model, GeminiAutoClient.Usage usage, String remaining, String retry) { }
            @Override public void onError(int status, String message, String remaining, String retry) {
                p.lastError = message;
                store.saveProfiles(profiles);
                main.post(() -> runDiscoveryThenRoute(request, original, index + 1));
            }
        });
    }

    private void runRoutes(String request, String original) {
        List<Route> routes = routesFor(original);
        if (routes.isEmpty()) {
            finishError("Tidak ada model AI yang valid. Silakan periksa API Key di Settings.");
            return;
        }
        streamRoute(request, original, routes, 0);
    }

    private List<Route> routesFor(String prompt) {
        List<Route> out = new ArrayList<>();
        profiles = store.getProfiles();

        int tier = 1; // 0 = lite, 1 = medium, 2 = pro
        if (MUTATING.matcher(prompt).find() || prompt.length() > 250) tier = 2;
        else if (prompt.length() < 50) tier = 0;

        for (AgentWorkspaceStore.Profile p : profiles) {
            if (!p.enabled || p.key.isEmpty()) continue;
            List<String> models = new ArrayList<>(p.models);
            if (models.isEmpty()) {
                models.add("gemini-1.5-flash");
                models.add("gemini-1.5-pro");
            }
            final int t = tier;
            models.sort((a, b) -> Integer.compare(scoreModel(a, t), scoreModel(b, t)));
            for (String m : models) out.add(new Route(p, m));
        }
        return out;
    }

    private int scoreModel(String model, int tier) {
        String m = model.toLowerCase();
        if (tier == 2) {
            if (m.contains("pro")) return 0;
            if (m.contains("flash")) return 1;
            return 2;
        }
        if (tier == 0) {
            if (m.contains("lite") || m.contains("8b")) return 0;
            if (m.contains("flash")) return 1;
            return 2;
        }
        if (m.contains("flash")) return 0;
        if (m.contains("pro")) return 1;
        return 2;
    }

    private void streamRoute(String request, String original, List<Route> routes, int index) {
        if (index >= routes.size()) {
            finishError("Limit tercapai di semua API Key. Tambahkan API Key cadangan di Settings.");
            return;
        }

        Route route = routes.get(index);
        if (!triedRoutes.add(route.profile.id + ":" + route.model)) {
            streamRoute(request, original, routes, index + 1);
            return;
        }

        liveAssistant = null;
        client.stream(route.profile.key, route.model, request, new GeminiAutoClient.Listener() {
            @Override public void onModels(List<GeminiAutoClient.ModelInfo> models, String remaining, String retry) { }
            @Override public void onDelta(String text) {
                main.post(() -> {
                    removeThinking();
                    if (liveAssistant == null) liveAssistant = addAssistantBubble();
                    liveAssistant.append(text);
                    scrollBottom();
                });
            }
            @Override public void onComplete(String model, GeminiAutoClient.Usage usage, String remaining, String retry) {
                main.post(() -> {
                    busy = false;
                    route.profile.rate = remaining;
                    store.saveProfiles(profiles);
                    tokenText.setText(usage.label());
                    if (liveAssistant != null) {
                        String full = liveAssistant.getText().toString();
                        liveAssistant.setText(richText(full));
                        store.append(session.id, "assistant", full);
                        if (MUTATING.matcher(original).find()) showProposal(full);
                    }
                });
            }
            @Override public void onError(int status, String message, String remaining, String retry) {
                main.post(() -> {
                    if (liveAssistant != null && liveAssistant.getText().length() > 0) {
                        busy = false;
                        return;
                    }
                    streamRoute(request, original, routes, index + 1);
                });
            }
        });
    }

    private void bubble(String text, boolean user) {
        LinearLayout line = row(requireContext());
        line.setGravity(user ? Gravity.END : Gravity.START);

        if (!user) line.addView(aiAvatar(), avatarParams());

        TextView view = label(requireContext(), user ? text : richText(text), 14, false);
        view.setTextColor(TEXT_PRIMARY);
        view.setLineSpacing(dp(3), 1f);
        view.setPadding(dp(14), dp(11), dp(14), dp(11));
        view.setBackground(round(user ? USER_BUBBLE : AI_BUBBLE, 16));
        view.setMaxWidth(Math.round(getResources().getDisplayMetrics().widthPixels * 0.78f));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMargins(0, dp(5), 0, dp(5));
        line.addView(view, lp);

        messages.addView(line);
        animateIn(line);
    }

    private TextView addAssistantBubble() {
        LinearLayout line = row(requireContext());
        line.setGravity(Gravity.START);
        line.addView(aiAvatar(), avatarParams());

        TextView view = label(requireContext(), "", 14, false);
        view.setTextColor(TEXT_PRIMARY);
        view.setLineSpacing(dp(3), 1f);
        view.setPadding(dp(14), dp(11), dp(14), dp(11));
        view.setBackground(round(AI_BUBBLE, 16));
        view.setMaxWidth(Math.round(getResources().getDisplayMetrics().widthPixels * 0.78f));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMargins(0, dp(5), 0, dp(5));
        line.addView(view, lp);

        messages.addView(line);
        animateIn(line);
        return view;
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

    private void showProposal(String answer) {
        AgentActionExecutor.Action parsed = AgentActionExecutor.parse(answer);
        if (parsed == null) return;

        LinearLayout card = column(requireContext());
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setBackground(stroke(CARD_DARK, dp(14), BORDER_DARK));

        TextView title = label(requireContext(), "Review perubahan sebelum diterapkan", 13, true);
        title.setTextColor(ACCENT);
        card.addView(title);

        TextView file = label(requireContext(), "Target: " + parsed.target, 12, false);
        file.setTextColor(TEXT_SECONDARY);
        file.setPadding(0, dp(4), 0, dp(12));
        card.addView(file);

        LinearLayout actions = row(requireContext());
        MaterialButton cancel = ghostButton("Cancel");
        MaterialButton accept = solidButton("Accept & Apply");
        actions.addView(cancel, new LinearLayout.LayoutParams(0, -2, 1f));
        LinearLayout.LayoutParams acceptLp = new LinearLayout.LayoutParams(0, -2, 1.4f);
        acceptLp.leftMargin = dp(8);
        actions.addView(accept, acceptLp);
        card.addView(actions);

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
            executeAction(parsed);
        });

        scrollBottom();
    }

    private void executeAction(AgentActionExecutor.Action action) {
        if (action.isXml()) {
            Intent i = new Intent(requireContext(), ViewCodeEditorActivity.class);
            i.putExtra("sc_id", projectId);
            i.putExtra("title", action.target);
            i.putExtra("content", action.content);
            startActivity(i);
            addAssistant("Proposal diterima. Kode dibuka di Direct XML Editor untuk disimpan.");
        } else {
            String res = AgentActionExecutor.apply(requireContext(), projectId, action);
            addAssistant(res.startsWith("APPLIED") ? "Perubahan berhasil diterapkan ke proyek kamu. \u2728" : "Gagal menerapkan: " + res);
        }
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

        if (profiles.isEmpty()) {
            TextView empty = label(requireContext(), "Belum ada API Key.\nTambahkan lewat tombol di bawah.", 13, false);
            empty.setTextColor(TEXT_SECONDARY);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(28), 0, dp(28));
            providerList.addView(empty);
            return;
        }

        for (AgentWorkspaceStore.Profile p : profiles) {
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

            EditText key = new EditText(requireContext());
            key.setText(p.key);
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

            boolean ok = p.lastError.isEmpty() && !p.models.isEmpty();
            String statusLine = p.lastError.isEmpty()
                    ? (p.models.isEmpty() ? "Belum divalidasi" : "Aktif \u00B7 " + p.models.size() + " model siap" + (p.rate.isEmpty() ? "" : " \u00B7 " + p.rate))
                    : p.lastError;
            TextView status = label(requireContext(), statusLine, 11, false);
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
                p.key = key.getText().toString().trim();
                status.setText("Memeriksa API Key...");
                status.setTextColor(TEXT_SECONDARY);
                validateAPI(p);
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

    private void validateAPI(AgentWorkspaceStore.Profile p) {
        client.listModels(p.key, new GeminiAutoClient.Listener() {
            @Override public void onModels(List<GeminiAutoClient.ModelInfo> models, String remaining, String retry) {
                p.models.clear();
                for (GeminiAutoClient.ModelInfo m : models) if (m.text()) p.models.add(m.id());
                p.enabled = !p.models.isEmpty();
                p.lastError = "";
                p.rate = remaining;
                store.saveProfiles(profiles);
                main.post(() -> {
                    refreshSettings();
                    if (session.messages.isEmpty()) restoreSession();
                    else addAssistant("API Key " + p.name + " aktif dan siap digunakan. \u2705");
                });
            }
            @Override public void onDelta(String text) { }
            @Override public void onComplete(String model, GeminiAutoClient.Usage usage, String remaining, String retry) { }
            @Override public void onError(int status, String message, String remaining, String retry) {
                p.lastError = message;
                p.enabled = false;
                store.saveProfiles(profiles);
                main.post(() -> {
                    refreshSettings();
                    addAssistant("Gagal memvalidasi " + p.name + ": " + message);
                });
            }
        });
    }

    private void startHeartbeat() {
        stopHeartbeat();
        heartbeat = () -> {
            profiles = store.getProfiles();
            boolean active = profiles.stream().anyMatch(p -> p.enabled && !p.key.isEmpty());
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
        transcript.post(() -> transcript.smoothScrollTo(0, messages.getHeight()));
    }

    private CharSequence richText(String value) {
        if (value == null) return "";
        SpannableStringBuilder ssb = new SpannableStringBuilder(value);
        int start = value.indexOf("```");
        while (start != -1) {
            int end = value.indexOf("```", start + 3);
            if (end == -1) break;
            ssb.setSpan(new TypefaceSpan("monospace"), start, end + 3, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            ssb.setSpan(new BackgroundColorSpan(CODE_BG), start, end + 3, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            ssb.setSpan(new ForegroundColorSpan(0xFFE4E9F2), start, end + 3, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            start = value.indexOf("```", end + 3);
        }
        int bold = value.indexOf("**");
        while (bold != -1) {
            int close = value.indexOf("**", bold + 2);
            if (close == -1) break;
            ssb.setSpan(new StyleSpan(Typeface.BOLD), bold, close + 2, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            bold = value.indexOf("**", close + 2);
        }
        return ssb;
    }

    private static final class Route {
        final AgentWorkspaceStore.Profile profile;
        final String model;
        Route(AgentWorkspaceStore.Profile p, String m) { this.profile = p; this.model = m; }
    }
}
