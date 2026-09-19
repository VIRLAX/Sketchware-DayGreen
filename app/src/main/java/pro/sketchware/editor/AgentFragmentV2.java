package pro.sketchware.editor;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.text.method.PasswordTransformationMethod;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import pro.sketchware.activities.editor.view.ViewCodeEditorActivity;

/** Dayven AI chat, multi-API Gemini setup, sessions, model routing, and guarded proposals. */
public final class AgentFragmentV2 extends Fragment {
    private static final int BG = Color.rgb(10, 10, 10);
    private static final int SURFACE = Color.rgb(28, 28, 30);
    private static final int SURFACE_2 = Color.rgb(38, 38, 40);
    private static final int TEXT = Color.WHITE;
    private static final int MUTED = Color.rgb(155, 155, 160);
    private static final int MINT = Color.rgb(0, 200, 150);
    private static final int RED = Color.rgb(245, 110, 110);
    private static final int BORDER = Color.rgb(58, 58, 60);
    private static final String GREETING = "Halo! Welcome to Dayven AI 👋✨\nAPI key kamu sudah aktif dan bisa langsung digunakan secara normal ya!\nSekarang saya siap bantu kamu. Ada bug yang mau di-fix, atau mau tambahkan fitur dan code baru ke proyek kamu? Kirim detailnya di sini, nanti saya bantu dengan teliti. 🚀";
    private static final String THINKING = "💭 Dayven AI lagi mikir dulu ya...\nsedang menganalisis kode dan menyusun solusinya... ⏳";
    private static final Pattern MUTATING = Pattern.compile("(?i)\\b(create|make|build|generate|fix|repair|change|modify|update|buat|bikin|perbaiki|ubah|layout|xml|java|kotlin|c\\+\\+|block|code|res|library|hubungkan)\\b");

    private final Handler main = new Handler(Looper.getMainLooper());
    private final GeminiAutoClient client = new GeminiAutoClient();
    private final Set<String> triedRoutes = new HashSet<>();
    private AgentWorkspaceStore store;
    private AgentWorkspaceStore.Session session;
    private List<AgentWorkspaceStore.Profile> profiles = new ArrayList<>();
    private FrameLayout root;
    private FrameLayout header;
    private LinearLayout content;
    private LinearLayout messages;
    private ScrollView transcript;
    private LinearLayout composerRow;
    private LinearLayout providerList;
    private TextView statusText;
    private TextView tokenText;
    private TextView sessionTitle;
    private EditText composer;
    private MaterialButton sendButton;
    private MaterialButton micButton;
    private String projectId;
    private String selectedFile;
    private TextView liveAssistant;
    private boolean busy;
    private Runnable heartbeat;
    private FrameLayout activeSettingsPage;

    public static AgentFragmentV2 newInstance(String projectId, String selectedFile) {
        AgentFragmentV2 f = new AgentFragmentV2();
        Bundle b = new Bundle();
        b.putString("project_id", projectId == null ? "" : projectId);
        b.putString("selected_file", selectedFile == null ? "" : selectedFile);
        f.setArguments(b);
        return f;
    }

    @Nullable @Override public View onCreateView(@NonNull android.view.LayoutInflater inflater, @Nullable ViewGroup parent, @Nullable Bundle state) {
        projectId = getArguments() == null ? "" : getArguments().getString("project_id", "");
        selectedFile = getArguments() == null ? "" : getArguments().getString("selected_file", "");
        store = new AgentWorkspaceStore(requireContext());
        session = store.getOrCreateCurrent();
        profiles = store.getProfiles();
        requireActivity().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        return build(requireContext());
    }

    @Override public void onResume() { super.onResume(); startHeartbeat(); }
    @Override public void onPause() { stopHeartbeat(); super.onPause(); }
    @Override public void onDestroyView() { stopHeartbeat(); super.onDestroyView(); }

    private View build(Context c) {
        root = new FrameLayout(c); root.setBackgroundColor(BG); root.setClipChildren(true); root.setClipToPadding(true);
        content = column(c); content.setPadding(dp(14), 0, dp(14), 0); root.addView(content, frame(-1, -1, Gravity.TOP));
        header = new FrameLayout(c); header.setBackground(stroke(SURFACE, dp(22), BORDER)); header.setElevation(dp(4));
        MaterialButton menu = icon(c, "☰", "Chat history and new chat"); menu.setBackground(stroke(SURFACE_2, dp(18), BORDER)); menu.setOnClickListener(v -> showDrawer()); header.addView(menu, frame(dp(48), dp(48), Gravity.START | Gravity.CENTER_VERTICAL));
        LinearLayout title = column(c); title.setGravity(Gravity.CENTER); sessionTitle = label(c, session.title, 16, false); sessionTitle.setGravity(Gravity.CENTER); sessionTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL)); title.addView(sessionTitle); TextView subtitle = label(c, "Dayven AI", 11, false); subtitle.setTextColor(MUTED); subtitle.setGravity(Gravity.CENTER); title.addView(subtitle); FrameLayout.LayoutParams tp = frame(-1, -2, Gravity.CENTER); tp.leftMargin = dp(60); tp.rightMargin = dp(112); header.addView(title, tp);
        LinearLayout actions = row(c);
        MaterialButton settings = icon(c, "⚙", "AI providers and settings");
        settings.setBackground(stroke(SURFACE_2, dp(18), BORDER));
        settings.setOnClickListener(v -> {
            if (activeSettingsPage != null) closeSettings();
            else showSettings();
        });
        MaterialButton exit = icon(c, "×", "Exit Agent"); exit.setBackground(stroke(SURFACE_2, dp(18), BORDER)); exit.setOnClickListener(v -> AgentTabEnhancer.exitAgent(requireActivity()));
        actions.addView(settings, new LinearLayout.LayoutParams(dp(48), dp(48))); actions.addView(exit, new LinearLayout.LayoutParams(dp(48), dp(48)));
        FrameLayout.LayoutParams ap = frame(-2, dp(48), Gravity.END | Gravity.CENTER_VERTICAL); ap.rightMargin = dp(4); header.addView(actions, ap); root.addView(header, frame(-1, dp(60), Gravity.TOP));

        LinearLayout status = row(c); status.setGravity(Gravity.CENTER_VERTICAL); status.setPadding(dp(4), dp(2), dp(4), dp(2)); TextView dot = label(c, "●", 14, false); dot.setGravity(Gravity.CENTER); dot.setTextColor(MINT); status.addView(dot, new LinearLayout.LayoutParams(dp(40), dp(40))); statusText = label(c, "Offline · add an API profile", 12, false); statusText.setTextColor(MUTED); status.addView(statusText, weight(1)); tokenText = label(c, "", 10, false); tokenText.setTextColor(MUTED); status.addView(tokenText, new LinearLayout.LayoutParams(-2, dp(40))); TextView auto = label(c, "AUTO", 10, true); auto.setGravity(Gravity.CENTER); auto.setTextColor(MINT); auto.setPadding(dp(12), 0, dp(12), 0); auto.setBackground(stroke(Color.TRANSPARENT, dp(18), Color.rgb(0, 130, 100))); status.addView(auto, new LinearLayout.LayoutParams(-2, dp(40))); content.addView(status, new LinearLayout.LayoutParams(-1, dp(48)));

        transcript = new ScrollView(c); transcript.setFillViewport(true); transcript.setSmoothScrollingEnabled(true); transcript.setClipToPadding(false);
        messages = column(c); messages.setPadding(0, dp(8), 0, dp(8)); transcript.addView(messages, new ViewGroup.LayoutParams(-1, -2)); content.addView(transcript, new LinearLayout.LayoutParams(-1, 0, 1));

        composerRow = composerBar(c); root.addView(composerRow, frame(-1, -2, Gravity.BOTTOM));
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> applyInsets(insets));
        ViewCompat.requestApplyInsets(root);
        restoreSession();
        return root;
    }

    private WindowInsetsCompat applyInsets(WindowInsetsCompat insets) {
        Insets bars = insets.getInsets(WindowInsetsCompat.Type.statusBars() | WindowInsetsCompat.Type.displayCutout() | WindowInsetsCompat.Type.navigationBars());
        Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
        int top = Math.max(bars.top + dp(18), dp(96)); int bottom = Math.max(bars.bottom, ime.bottom) + dp(12);
        FrameLayout.LayoutParams hp = (FrameLayout.LayoutParams) header.getLayoutParams(); hp.topMargin = top; header.setLayoutParams(hp);
        FrameLayout.LayoutParams cp = (FrameLayout.LayoutParams) content.getLayoutParams(); cp.topMargin = top + dp(68); cp.bottomMargin = bottom + dp(58); content.setLayoutParams(cp);
        FrameLayout.LayoutParams bp = (FrameLayout.LayoutParams) composerRow.getLayoutParams(); bp.leftMargin = dp(10); bp.rightMargin = dp(10); bp.bottomMargin = bottom; composerRow.setLayoutParams(bp);
        if (ime.bottom > 0) root.postDelayed(() -> { scrollBottom(); composer.requestRectangleOnScreen(new android.graphics.Rect(0, 0, composer.getWidth(), composer.getHeight()), false); }, 90);
        return insets;
    }

    private LinearLayout composerBar(Context c) {
        LinearLayout bar = row(c); bar.setGravity(Gravity.CENTER_VERTICAL); bar.setPadding(dp(4), dp(4), dp(4), dp(4)); bar.setElevation(dp(8)); bar.setBackground(stroke(SURFACE, dp(28), BORDER));
        MaterialButton attach = icon(c, "+", "Attach"); attach.setBackground(round(SURFACE_2, dp(22))); bar.addView(attach, new LinearLayout.LayoutParams(dp(48), dp(48)));
        composer = new EditText(c); composer.setHint("Message..."); composer.setHintTextColor(Color.rgb(130, 130, 135)); composer.setTextColor(TEXT); composer.setTextSize(15); composer.setGravity(Gravity.CENTER_VERTICAL | Gravity.START); composer.setPadding(dp(10), 0, dp(8), 0); composer.setMinHeight(dp(48)); composer.setMaxLines(5); composer.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_MULTI_LINE); composer.setBackgroundColor(Color.TRANSPARENT);
        composer.setOnFocusChangeListener((v, focused) -> { if (focused) v.postDelayed(() -> { ViewCompat.requestApplyInsets(root); scrollBottom(); }, 100); }); bar.addView(composer, weight(1));
        micButton = icon(c, "♩", "Voice input"); micButton.setBackground(round(SURFACE_2, dp(22))); bar.addView(micButton, new LinearLayout.LayoutParams(dp(48), dp(48)));
        sendButton = icon(c, "↑", "Send message"); sendButton.setTextColor(Color.BLACK); sendButton.setBackground(round(MINT, dp(24))); sendButton.setVisibility(View.GONE); bar.addView(sendButton, new LinearLayout.LayoutParams(dp(48), dp(48)));
        sendButton.setOnClickListener(v -> { v.setScaleX(.86f); v.setScaleY(.86f); v.animate().scaleX(1f).scaleY(1f).setDuration(150).start(); send(); });
        composer.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { boolean has = s != null && s.toString().trim().length() > 0; sendButton.setVisibility(has ? View.VISIBLE : View.GONE); micButton.setVisibility(has ? View.GONE : View.VISIBLE); }
            @Override public void afterTextChanged(Editable s) { }
        });
        return bar;
    }

    private void restoreSession() {
        messages.removeAllViews();
        if (session.messages.isEmpty()) {
            addAssistant("Saya siap membantu. Kirim bug, ide fitur, atau potongan kode. Untuk perubahan project, saya akan tampilkan proposal dulu sebelum menerapkan apa pun.");
        } else {
            for (AgentWorkspaceStore.Message m : session.messages) bubble(m.text, "user".equals(m.role));
        }
        scrollBottom();
    }

    private void send() {
        if (busy) return;
        String prompt = composer.getText().toString().trim();
        if (prompt.isEmpty()) return;
        if (profiles.stream().noneMatch(p -> p.enabled && !p.key.isEmpty())) {
            addAssistant("Gemini belum aktif. Buka Settings, masukkan API key, lakukan Check, lalu Save & aktifkan profilnya.");
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
        runDiscoveryThenRoute(promptFor(prompt), prompt, 0);
    }

    private void addThinking() {
        LinearLayout line = row(requireContext()); line.setGravity(Gravity.START | Gravity.CENTER_VERTICAL); line.setTag("thinking");
        line.addView(avatar(), new LinearLayout.LayoutParams(dp(32), dp(32)));
        TextView t = label(requireContext(), THINKING, 14, false); t.setTextColor(MUTED); t.setPadding(dp(12), dp(10), dp(12), dp(10)); t.setBackground(round(SURFACE, 18));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, -2); p.setMargins(dp(8), dp(4), dp(48), dp(4)); line.addView(t, p);
        messages.addView(line, new LinearLayout.LayoutParams(-1, -2)); scrollBottom();
    }

    private void removeThinking() {
        for (int i = messages.getChildCount() - 1; i >= 0; i--) {
            View v = messages.getChildAt(i);
            if ("thinking".equals(v.getTag())) messages.removeViewAt(i);
        }
    }

    private String promptFor(String prompt) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are Dayven AI, an intelligent coding companion inside Sketchware-DayGreen.\n");
        sb.append("Follow these rules strictly:\n");
        sb.append("1. Answer the user's immediate question or greeting directly, naturally, and relevantly.\n");
        sb.append("2. If the user sends a greeting (like 'hai', 'halo', 'siapa kamu'), respond directly and warmly in the same language. Do NOT output compilation error diagnoses or unsolicited code unless specifically asked.\n");
        sb.append("3. For any project modification, finish with exactly one proposal: TARGET: safe relative path, then one fenced code block with replacement content.\n");

        boolean needsProjectContext = MUTATING.matcher(prompt).find() || prompt.toLowerCase(Locale.US).contains("error") || prompt.toLowerCase(Locale.US).contains("bug");
        if (needsProjectContext) {
            String ctx = AgentProjectContext.build(requireContext(), projectId, selectedFile);
            sb.append("\nPROJECT CONTEXT:\n").append(ctx).append("\n");
        }

        sb.append("\nRECENT CONVERSATION:\n");
        int start = Math.max(0, session.messages.size() - 6);
        for (int i = start; i < session.messages.size(); i++) {
            AgentWorkspaceStore.Message m = session.messages.get(i);
            sb.append(m.role).append(": ").append(m.text).append("\n");
        }
        sb.append("\nUSER: ").append(prompt);
        return sb.toString();
    }

    private void runDiscoveryThenRoute(String request, String original, int index) {
        profiles = store.getProfiles();
        if (index >= profiles.size()) { runRoutes(request, original); return; }
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
                p.lastError = p.models.isEmpty() ? "No valid text model" : "Validated";
                store.saveProfiles(profiles);
                main.post(() -> runDiscoveryThenRoute(request, original, index + 1));
            }
            @Override public void onDelta(String text) { }
            @Override public void onComplete(String model, GeminiAutoClient.Usage usage, String remaining, String retry) { }
            @Override public void onError(int status, String message, String remaining, String retry) {
                p.lastError = message;
                p.rate = remaining;
                p.failures++;
                store.saveProfiles(profiles);
                main.post(() -> runDiscoveryThenRoute(request, original, index + 1));
            }
        });
    }

    private void runRoutes(String request, String original) {
        List<Route> routes = routesFor(original);
        if (routes.isEmpty()) {
            finishError("Tidak ada model Gemini teks yang valid. Buka Settings dan validasi API key.");
            return;
        }
        streamRoute(request, original, routes, 0);
    }

    private List<Route> routesFor(String prompt) {
        List<Route> out = new ArrayList<>();
        profiles = store.getProfiles();
        boolean hard = prompt.length() > 240 || MUTATING.matcher(prompt).find() || prompt.toLowerCase(Locale.US).contains("error");
        boolean easy = prompt.length() < 90 && !MUTATING.matcher(prompt).find();
        for (AgentWorkspaceStore.Profile p : profiles) {
            if (p.enabled && !p.key.isEmpty()) {
                List<String> models = new ArrayList<>(p.models);
                if (models.isEmpty()) {
                    models.add("gemini-1.5-flash");
                    models.add("gemini-2.0-flash");
                    models.add("gemini-1.5-pro");
                }
                models.sort(Comparator.comparingInt(m -> rank(m, hard, easy)));
                for (String m : models) out.add(new Route(p, m));
            }
        }
        return out;
    }

    private int rank(String model, boolean hard, boolean easy) {
        String m = model.toLowerCase(Locale.US);
        if (m.contains("tts") || m.contains("audio")) return 99;
        boolean pro = m.contains("pro");
        boolean lite = m.contains("flash") || m.contains("lite");
        if (hard) return pro ? 0 : lite ? 2 : 1;
        if (easy) return lite ? 0 : pro ? 2 : 1;
        return lite ? 0 : pro ? 1 : 2;
    }

    private void streamRoute(String request, String original, List<Route> routes, int index) {
        if (index >= routes.size()) {
            busy = false;
            removeThinking();
            finishError("Semua API key/model yang aktif gagal atau terkena limit. Tambahkan atau cek API key cadangan di Settings.");
            return;
        }
        Route route = routes.get(index);
        String key = route.profile.id + ":" + route.model;
        if (!triedRoutes.add(key)) { streamRoute(request, original, routes, index + 1); return; }
        route.profile.lastUsed = System.currentTimeMillis();
        liveAssistant = null;

        client.stream(route.profile.key, route.model, request, new GeminiAutoClient.Listener() {
            @Override public void onModels(List<GeminiAutoClient.ModelInfo> models, String remaining, String retryAfter) { }
            @Override public void onDelta(String text) {
                main.post(() -> {
                    removeThinking();
                    if (liveAssistant == null) liveAssistant = addAssistantBubble("");
                    liveAssistant.append(text);
                    scrollBottom();
                });
            }
            @Override public void onComplete(String model, GeminiAutoClient.Usage usage, String remaining, String retryAfter) {
                main.post(() -> {
                    busy = false;
                    removeThinking();
                    route.profile.rate = remaining;
                    route.profile.lastError = "";
                    store.saveProfiles(profiles);
                    tokenText.setText(usage.label().isEmpty() ? remaining : usage.label());
                    if (liveAssistant != null) {
                        String full = liveAssistant.getText().toString();
                        store.append(session.id, "assistant", full);
                        session = store.getSession(session.id);
                        if (MUTATING.matcher(original).find()) showProposal(full);
                    }
                });
            }
            @Override public void onError(int status, String message, String remaining, String retryAfter) {
                main.post(() -> {
                    route.profile.lastError = message;
                    route.profile.rate = remaining;
                    route.profile.failures++;
                    store.saveProfiles(profiles);
                    if (liveAssistant != null && liveAssistant.getText().length() > 0) {
                        busy = false;
                        removeThinking();
                        return;
                    }
                    if (status == 429 || status == 403 || status == 404 || status == 400 || status == 0) {
                        streamRoute(request, original, routes, index + 1);
                    } else {
                        busy = false;
                        removeThinking();
                        finishError(message);
                    }
                });
            }
        });
    }

    private void showProposal(String answer) {
        AgentActionExecutor.Action parsed = AgentActionExecutor.parse(answer);
        if (parsed == null) return;
        LinearLayout line = row(requireContext()); line.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        line.addView(avatar(), new LinearLayout.LayoutParams(dp(32), dp(32)));
        LinearLayout card = column(requireContext()); card.setPadding(dp(12), dp(10), dp(12), dp(10)); card.setBackground(round(SURFACE_2, 18));
        card.addView(label(requireContext(), "Proposal: " + parsed.target, 13, true));
        TextView preview = label(requireContext(), preview(parsed.content), 12, false); preview.setTextColor(MUTED); card.addView(preview);
        LinearLayout buttons = row(requireContext());
        MaterialButton cancel = smallButton("Cancel");
        MaterialButton accept = smallButton("Accept");
        buttons.addView(cancel, weight(1)); buttons.addView(accept, weight(1));
        card.addView(buttons);
        line.addView(card, new LinearLayout.LayoutParams(-2, -2));
        messages.addView(line, new LinearLayout.LayoutParams(-1, -2));
        cancel.setOnClickListener(v -> { messages.removeView(line); addAssistant("Dibatalkan. Tidak ada perubahan yang diterapkan."); });
        accept.setOnClickListener(v -> { messages.removeView(line); accept(parsed); });
        scrollBottom();
    }

    private void accept(AgentActionExecutor.Action action) {
        if (action == null) return;
        if (action.isXml()) {
            Intent i = new Intent(requireContext(), ViewCodeEditorActivity.class);
            i.putExtra("sc_id", projectId); i.putExtra("title", action.target); i.putExtra("content", action.content);
            startActivity(i);
            addAssistant("Proposal diterima. XML dibuka di Direct XML untuk ditinjau dan disimpan.");
            return;
        }
        String result = AgentActionExecutor.apply(requireContext(), projectId, action);
        addAssistant(result.startsWith("APPLIED") ? "Selesai. Perubahan diterapkan sesuai proposal." : "Belum diterapkan: " + result);
    }

    private void showDrawer() {
        final FrameLayout layer = new FrameLayout(requireContext());
        layer.setBackgroundColor(Color.argb(150, 0, 0, 0));
        LinearLayout panel = column(requireContext());
        panel.setBackgroundColor(BG);
        panel.setPadding(dp(20), dp(20), dp(12), dp(12));
        panel.addView(label(requireContext(), "Dayven AI", 25, true));
        panel.addView(label(requireContext(), "Chat log", 12, false));

        MaterialButton fresh = smallButton("＋  Chat baru");
        fresh.setOnClickListener(v -> {
            session = store.newSession("New chat");
            sessionTitle.setText(session.title);
            restoreSession();
            ((ViewGroup) layer.getParent()).removeView(layer);
        });
        panel.addView(fresh, new LinearLayout.LayoutParams(-1, dp(52)));

        ScrollView listScroll = new ScrollView(requireContext());
        listScroll.setFillViewport(true);
        LinearLayout list = column(requireContext());
        for (AgentWorkspaceStore.Session s : store.getSessions()) {
            TextView item = label(requireContext(), s.title, 14, false);
            item.setTextColor(TEXT);
            item.setPadding(dp(12), dp(14), dp(12), dp(14));
            item.setMinHeight(dp(48));
            item.setBackground(round(s.id.equals(session.id) ? SURFACE_2 : Color.TRANSPARENT, 14));
            item.setOnClickListener(v -> {
                session = store.getSession(s.id);
                store.selectSession(s.id);
                sessionTitle.setText(session.title);
                restoreSession();
                ((ViewGroup) layer.getParent()).removeView(layer);
            });
            list.addView(item);
        }
        listScroll.addView(list);
        panel.addView(listScroll, new LinearLayout.LayoutParams(-1, 0, 1));

        FrameLayout.LayoutParams pp = frame(-1, -1, Gravity.START);
        pp.width = Math.round(getResources().getDisplayMetrics().widthPixels * .86f);
        pp.topMargin = dp(96); pp.bottomMargin = dp(12);
        layer.addView(panel, pp);
        layer.setOnClickListener(v -> { if (v == layer) ((ViewGroup) layer.getParent()).removeView(layer); });
        root.addView(layer, frame(-1, -1, Gravity.TOP));
    }

    private void closeSettings() {
        if (activeSettingsPage != null) {
            ViewGroup parent = (ViewGroup) activeSettingsPage.getParent();
            if (parent != null) parent.removeView(activeSettingsPage);
            activeSettingsPage = null;
        }
    }

    private void showSettings() {
        closeSettings();
        final FrameLayout page = new FrameLayout(requireContext());
        activeSettingsPage = page;
        page.setBackgroundColor(BG);

        LinearLayout body = column(requireContext());
        body.setPadding(dp(18), dp(18), dp(18), dp(18));

        LinearLayout top = row(requireContext());
        top.setGravity(Gravity.CENTER_VERTICAL);
        MaterialButton back = icon(requireContext(), "‹", "Back to Chat");
        back.setBackground(round(SURFACE_2, 14));
        back.setOnClickListener(v -> closeSettings());
        top.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));

        TextView title = label(requireContext(), "AI Providers & Settings", 18, true);
        title.setPadding(dp(12), 0, 0, 0);
        top.addView(title, weight(1));

        MaterialButton closeBtn = icon(requireContext(), "×", "Close Settings");
        closeBtn.setBackground(round(SURFACE_2, 14));
        closeBtn.setOnClickListener(v -> closeSettings());
        top.addView(closeBtn, new LinearLayout.LayoutParams(dp(48), dp(48)));
        body.addView(top);

        TextView sub = label(requireContext(), "Kelola Google AI Studio / Gemini API Key (API 1, API 2, API 3, dst)", 12, false);
        sub.setTextColor(MUTED);
        sub.setPadding(0, dp(10), 0, dp(10));
        body.addView(sub);

        providerList = column(requireContext());
        ScrollView scroll = new ScrollView(requireContext());
        scroll.setFillViewport(true);
        scroll.addView(providerList);
        body.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout bottomActions = row(requireContext());
        bottomActions.setPadding(0, dp(10), 0, dp(4));

        MaterialButton add = smallButton("＋ Tambah API Gemini Baru");
        add.setOnClickListener(v -> {
            store.addProfile("Gemini " + (store.getProfiles().size() + 1));
            renderProviders();
        });
        bottomActions.addView(add, weight(1));

        MaterialButton backChat = smallButton("← Kembali ke Chat");
        backChat.setTextColor(MINT);
        backChat.setOnClickListener(v -> closeSettings());
        bottomActions.addView(backChat, weight(1));
        body.addView(bottomActions);

        page.addView(body, frame(-1, -1, Gravity.TOP));
        root.addView(page, frame(-1, -1, Gravity.TOP));

        ViewCompat.setOnApplyWindowInsetsListener(page, (v, insets) -> {
            Insets b = insets.getInsets(WindowInsetsCompat.Type.statusBars() | WindowInsetsCompat.Type.displayCutout() | WindowInsetsCompat.Type.navigationBars());
            body.setPadding(dp(18), Math.max(b.top + dp(14), dp(48)), dp(18), Math.max(b.bottom + dp(14), dp(16)));
            return insets;
        });
        ViewCompat.requestApplyInsets(page);
        renderProviders();
    }

    private void renderProviders() {
        if (providerList == null) return;
        providerList.removeAllViews();
        profiles = store.getProfiles();
        for (AgentWorkspaceStore.Profile p : profiles) addProviderCard(p);
        if (profiles.isEmpty()) {
            providerList.addView(label(requireContext(), "Belum ada API profile. Klik tombol tambah di bawah.", 14, false));
        }
    }

    private void addProviderCard(AgentWorkspaceStore.Profile p) {
        LinearLayout card = column(requireContext());
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        card.setBackground(stroke(SURFACE, dp(18), BORDER));

        LinearLayout head = row(requireContext());
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView logo = label(requireContext(), "G", 18, true);
        logo.setGravity(Gravity.CENTER);
        logo.setTextColor(Color.WHITE);
        logo.setBackground(round(MINT, 12));
        head.addView(logo, new LinearLayout.LayoutParams(dp(40), dp(40)));

        LinearLayout names = column(requireContext());
        names.setPadding(dp(10), 0, 0, 0);
        names.addView(label(requireContext(), "Google AI Studio · " + p.name, 15, true));
        names.addView(label(requireContext(), p.models.isEmpty() ? "Belum divalidasi" : p.models.size() + " model teks siap pakai", 11, false));
        head.addView(names, weight(1));

        MaterialButton toggle = smallButton(p.enabled && !p.key.isEmpty() ? "ON" : "OFF");
        toggle.setTextColor(p.enabled && !p.key.isEmpty() ? MINT : MUTED);
        toggle.setBackground(round(p.enabled && !p.key.isEmpty() ? Color.rgb(20, 60, 45) : SURFACE_2, 14));
        head.addView(toggle, new LinearLayout.LayoutParams(dp(68), dp(44)));
        card.addView(head);

        EditText keyField = new EditText(requireContext());
        keyField.setText(p.key);
        keyField.setHint("Tempel Gemini API key di sini");
        keyField.setHintTextColor(MUTED);
        keyField.setTextColor(TEXT);
        keyField.setTextSize(13);
        keyField.setSingleLine(true);
        keyField.setMinHeight(dp(46));
        keyField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        keyField.setTransformationMethod(PasswordTransformationMethod.getInstance());
        keyField.setPadding(dp(12), dp(8), dp(12), dp(8));
        keyField.setBackground(round(SURFACE_2, 12));
        LinearLayout.LayoutParams kfp = new LinearLayout.LayoutParams(-1, -2);
        kfp.setMargins(0, dp(10), 0, 0);
        card.addView(keyField, kfp);

        TextView info = label(requireContext(), p.lastError.isEmpty() ? (p.rate.isEmpty() ? "Check API key untuk validasi & auto-detect model." : "Valid · " + p.rate) : p.lastError, 11, false);
        info.setTextColor(p.lastError.isEmpty() ? MUTED : RED);
        info.setPadding(0, dp(6), 0, dp(6));
        card.addView(info);

        LinearLayout buttons = row(requireContext());
        MaterialButton check = smallButton("Check API");
        MaterialButton save = smallButton("Save & Aktifkan");
        MaterialButton delete = smallButton("Hapus");
        delete.setTextColor(RED);

        buttons.addView(check, weight(1));
        buttons.addView(save, weight(1));
        buttons.addView(delete, new LinearLayout.LayoutParams(dp(64), dp(44)));
        card.addView(buttons);

        toggle.setOnClickListener(v -> {
            p.enabled = !p.enabled;
            store.saveProfiles(profiles);
            renderProviders();
        });

        save.setOnClickListener(v -> {
            String value = keyField.getText().toString().trim();
            if (!value.isEmpty()) p.key = value;
            p.enabled = !p.key.isEmpty();
            store.saveProfiles(profiles);
            renderProviders();
            if (p.enabled) welcomeIfNeeded();
        });

        check.setOnClickListener(v -> validateProfile(p, keyField, info));

        delete.setOnClickListener(v -> {
            store.removeProfile(p.id);
            renderProviders();
        });

        providerList.addView(card, new LinearLayout.LayoutParams(-1, -2) {{
            setMargins(0, dp(10), 0, 0);
        }});
    }

    private void validateProfile(AgentWorkspaceStore.Profile p, EditText key, TextView info) {
        String candidate = key.getText().toString().trim();
        if (candidate.isEmpty()) candidate = p.key;
        if (candidate.isEmpty()) { info.setText("Masukkan API key dulu."); return; }
        final String keyValue = candidate;
        info.setText("Checking key dan model aktif...");
        client.listModels(keyValue, new GeminiAutoClient.Listener() {
            @Override public void onModels(List<GeminiAutoClient.ModelInfo> models, String remaining, String retry) {
                p.key = keyValue;
                p.models.clear();
                for (GeminiAutoClient.ModelInfo m : models) if (m.text()) p.models.add(m.id());
                p.rate = remaining;
                p.lastError = p.models.isEmpty() ? "Key valid, tapi model teks tidak ditemukan." : "API key valid · " + p.models.size() + " model aktif";
                store.saveProfiles(profiles);
                main.post(() -> {
                    info.setText(p.lastError);
                    renderProviders();
                });
            }
            @Override public void onDelta(String text) { }
            @Override public void onComplete(String model, GeminiAutoClient.Usage usage, String remaining, String retry) { }
            @Override public void onError(int status, String message, String remaining, String retry) {
                p.lastError = message;
                store.saveProfiles(profiles);
                main.post(() -> info.setText(message));
            }
        });
    }

    private void welcomeIfNeeded() {
        session = store.getSession(session.id);
        boolean hasAssistant = false;
        for (AgentWorkspaceStore.Message m : session.messages) if ("assistant".equals(m.role)) hasAssistant = true;
        if (!hasAssistant) {
            store.append(session.id, "assistant", GREETING);
            session = store.getSession(session.id);
            restoreSession();
        }
        status("API aktif · Gemini", MINT);
    }

    private void bubble(String text, boolean user) {
        LinearLayout line = row(requireContext());
        line.setGravity(user ? Gravity.END : Gravity.START | Gravity.CENTER_VERTICAL);
        if (!user) line.addView(avatar(), new LinearLayout.LayoutParams(dp(32), dp(32)));
        LinearLayout col = column(requireContext());
        col.setGravity(user ? Gravity.END : Gravity.START);
        TextView bubble = label(requireContext(), text, 15, false);
        bubble.setTextColor(Color.WHITE);
        bubble.setTextIsSelectable(true);
        bubble.setMaxWidth(Math.round(requireContext().getResources().getDisplayMetrics().widthPixels * .75f));
        bubble.setPadding(dp(12), dp(10), dp(12), dp(10));
        bubble.setBackground(round(user ? MINT : SURFACE, 18));
        col.addView(bubble);
        TextView time = label(requireContext(), new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date()), 10, false);
        time.setTextColor(MUTED);
        col.addView(time);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, -2);
        p.setMargins(user ? dp(48) : dp(8), dp(4), user ? 0 : dp(48), dp(4));
        line.addView(col, p);
        messages.addView(line, new LinearLayout.LayoutParams(-1, -2));
        line.setAlpha(0f); line.setTranslationY(dp(8));
        line.animate().alpha(1f).translationY(0).setDuration(150).start();
    }

    private TextView addAssistantBubble(String text) {
        LinearLayout line = row(requireContext());
        line.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        line.addView(avatar(), new LinearLayout.LayoutParams(dp(32), dp(32)));
        LinearLayout col = column(requireContext());
        col.setGravity(Gravity.START);
        TextView bubble = label(requireContext(), text, 15, false);
        bubble.setTextColor(TEXT);
        bubble.setTextIsSelectable(true);
        bubble.setMaxWidth(Math.round(requireContext().getResources().getDisplayMetrics().widthPixels * .75f));
        bubble.setPadding(dp(12), dp(10), dp(12), dp(10));
        bubble.setBackground(round(SURFACE, 18));
        col.addView(bubble);
        TextView time = label(requireContext(), new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date()), 10, false);
        time.setTextColor(MUTED);
        col.addView(time);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, -2);
        p.setMargins(dp(8), dp(4), dp(48), dp(4));
        line.addView(col, p);
        messages.addView(line, new LinearLayout.LayoutParams(-1, -2));
        line.setAlpha(0f); line.setTranslationY(dp(8));
        line.animate().alpha(1f).translationY(0).setDuration(150).start();
        return bubble;
    }

    private TextView avatar() {
        TextView a = label(requireContext(), "+", 17, true);
        a.setGravity(Gravity.CENTER);
        a.setTextColor(MINT);
        a.setBackground(stroke(SURFACE_2, dp(18), Color.rgb(0, 130, 100)));
        a.setContentDescription("Dayven AI");
        return a;
    }

    private void addAssistant(String text) { bubble(text, false); }

    private void scrollBottom() {
        if (transcript != null) {
            transcript.post(() -> {
                View child = transcript.getChildAt(0);
                if (child != null) transcript.smoothScrollTo(0, child.getBottom());
            });
        }
    }

    private void status(String text, int color) {
        if (statusText != null) {
            statusText.setText(text);
            statusText.setTextColor(color);
        }
    }

    private void finishError(String text) {
        busy = false;
        removeThinking();
        status(text, RED);
        addAssistant(text);
    }

    private String preview(String value) { return value.length() > 1600 ? value.substring(0, 1600) + "…" : value; }
    private LinearLayout column(Context c) { LinearLayout l = new LinearLayout(c); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private LinearLayout row(Context c) { LinearLayout l = new LinearLayout(c); l.setOrientation(LinearLayout.HORIZONTAL); return l; }
    private LinearLayout.LayoutParams weight(float w) { return new LinearLayout.LayoutParams(0, -2, w); }
    private FrameLayout.LayoutParams frame(int width, int height, int gravity) { return new FrameLayout.LayoutParams(width, height, gravity); }
    private TextView label(Context c, String text, int size, boolean bold) { TextView v = new TextView(c); v.setText(text); v.setTextSize(size); v.setTextColor(TEXT); v.setTypeface(null, bold ? Typeface.BOLD : Typeface.NORMAL); return v; }
    private MaterialButton icon(Context c, String text, String description) { MaterialButton b = new MaterialButton(c); b.setText(text); b.setContentDescription(description); b.setTextSize(18); b.setTextColor(TEXT); b.setMinHeight(dp(48)); b.setMinWidth(dp(48)); b.setPadding(0, 0, 0, 0); b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.TRANSPARENT)); return b; }
    private MaterialButton smallButton(String text) { MaterialButton b = new MaterialButton(requireContext()); b.setText(text); b.setTextSize(12); b.setTextColor(TEXT); b.setMinHeight(dp(44)); b.setMinWidth(dp(44)); b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(SURFACE_2)); return b; }
    private GradientDrawable round(int color, int radius) { GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d; }
    private GradientDrawable stroke(int fill, int radius, int color) { GradientDrawable d = round(fill, radius); d.setStroke(dp(1), color); return d; }
    private int dp(int value) { return Math.round(value * requireContext().getResources().getDisplayMetrics().density); }

    private void startHeartbeat() {
        stopHeartbeat();
        heartbeat = () -> {
            profiles = store.getProfiles();
            boolean connected = profiles.stream().anyMatch(p -> p.enabled && !p.key.isEmpty());
            status(connected ? "API aktif · Gemini" : "Offline · add an API profile", connected ? MINT : MUTED);
            main.postDelayed(heartbeat, 30000);
        };
        main.post(heartbeat);
    }

    private void stopHeartbeat() {
        if (heartbeat != null) main.removeCallbacks(heartbeat);
        heartbeat = null;
    }

    private static final class Route {
        final AgentWorkspaceStore.Profile profile;
        final String model;
        Route(AgentWorkspaceStore.Profile profile, String model) {
            this.profile = profile;
            this.model = model;
        }
    }
}
