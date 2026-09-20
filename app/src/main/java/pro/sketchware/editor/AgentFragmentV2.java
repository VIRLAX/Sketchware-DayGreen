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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.content.res.ColorStateList;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;

import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;

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

/** 
 * Dayven AI Agent - A premium, intelligent coding assistant for Sketchware-DayDream.
 * Features: Multi-API management, smart model routing (Lite/Medium/Pro), 
 * session memory, and guided code proposals.
 */
public final class AgentFragmentV2 extends Fragment {
    // Premium Color Palette (Claude-inspired)
    private static final int BG = Color.rgb(249, 248, 246); // Off-white/Cream
    private static final int SURFACE = Color.WHITE;
    private static final int TEXT_PRIMARY = Color.rgb(27, 27, 27);
    private static final int TEXT_SECONDARY = Color.rgb(107, 107, 107);
    private static final int ACCENT = Color.rgb(217, 119, 87); // Claude Orange-ish
    private static final int ACCENT_LIGHT = Color.rgb(252, 240, 235);
    private static final int BORDER = Color.rgb(230, 228, 220);
    private static final int USER_BUBBLE = Color.rgb(240, 238, 233);
    private static final int AI_BUBBLE = Color.WHITE;
    private static final int CODE_BG = Color.rgb(30, 30, 30);
    private static final int SUCCESS = Color.rgb(34, 197, 94);
    private static final int ERROR = Color.rgb(239, 68, 68);

    private static final String GREETING = "Halo! Welcome to Dayven AI 👋✨\nAPI key kamu sudah aktif dan bisa langsung digunakan secara normal ya!\n\nSekarang saya sudah siap bantu kamu. Ada bug yang mau di-fix, atau mau tambahkan fitur dan code baru ke proyek kamu? Kirim saja detail atau potongan kodenya di sini, nanti saya bantu selesaikan! 🚀";
    private static final String THINKING = "💭 Dayven AI lagi mikir dulu ya...\nsedang menganalisis kode dan menyusun solusinya... ⏳";
    
    private static final Pattern MUTATING = Pattern.compile("(?i)\\b(create|make|build|generate|fix|repair|change|modify|update|buat|bikin|perbaiki|ubah|layout|xml|java|kotlin|c\\+\\+|block|code|res|library|hubungkan|download)\\b");

    private final Handler main = new Handler(Looper.getMainLooper());
    private final GeminiAutoClient client = new GeminiAutoClient();
    private final Set<String> triedRoutes = new HashSet<>();
    
    private AgentWorkspaceStore store;
    private AgentWorkspaceStore.Session session;
    private List<AgentWorkspaceStore.Profile> profiles = new ArrayList<>();
    
    private FrameLayout root;
    private FrameLayout header;
    private LinearLayout messages;
    private ScrollView transcript;
    private LinearLayout composerRow;
    private LinearLayout providerList;
    private TextView statusText;
    private TextView tokenText;
    private TextView sessionTitle;
    private EditText composer;
    private MaterialButton sendButton;
    
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

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup parent, @Nullable Bundle state) {
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

    private View build(Context c) {
        root = new FrameLayout(c); 
        root.setLayoutParams(new ViewGroup.LayoutParams(-1, -1));
        root.setBackgroundColor(BG);
        
        // Header
        header = new FrameLayout(c);
        header.setBackgroundColor(SURFACE);
        header.setElevation(dp(2));
        
        MaterialButton menuBtn = icon(c, "☰", "Menu");
        menuBtn.setOnClickListener(v -> showDrawer());
        header.addView(menuBtn, frame(dp(56), dp(56), Gravity.START | Gravity.CENTER_VERTICAL));
        
        LinearLayout titleGroup = column(c);
        titleGroup.setGravity(Gravity.CENTER);
        sessionTitle = label(c, session.title, 16, true);
        sessionTitle.setTextColor(TEXT_PRIMARY);
        titleGroup.addView(sessionTitle);
        TextView subtitle = label(c, "Dayven AI · Assistant", 11, false);
        subtitle.setTextColor(TEXT_SECONDARY);
        titleGroup.addView(subtitle);
        header.addView(titleGroup, frame(-1, -2, Gravity.CENTER));
        
        MaterialButton settingsBtn = icon(c, "⚙", "Settings");
        settingsBtn.setOnClickListener(v -> showSettings());
        header.addView(settingsBtn, frame(dp(56), dp(56), Gravity.END | Gravity.CENTER_VERTICAL));
        
        root.addView(header, frame(-1, dp(64), Gravity.TOP));

        // Content Area
        LinearLayout content = column(c);
        
        // Status Bar (Heartbeat & Tokens)
        LinearLayout statusBar = row(c);
        statusBar.setGravity(Gravity.CENTER_VERTICAL);
        statusBar.setPadding(dp(16), dp(8), dp(16), dp(8));
        statusBar.setBackgroundColor(SURFACE);
        
        View indicator = new View(c);
        indicator.setBackground(round(Color.LTGRAY, 6));
        statusBar.addView(indicator, new LinearLayout.LayoutParams(dp(8), dp(8)));
        
        statusText = label(c, "Connecting...", 12, false);
        statusText.setPadding(dp(8), 0, 0, 0);
        statusText.setTextColor(TEXT_SECONDARY);
        statusBar.addView(statusText, weight(1));
        
        tokenText = label(c, "0 tokens", 11, false);
        tokenText.setTextColor(TEXT_SECONDARY);
        statusBar.addView(tokenText);
        
        content.addView(statusBar, new LinearLayout.LayoutParams(-1, -2));

        // Transcript
        transcript = new ScrollView(c);
        transcript.setFillViewport(true);
        messages = column(c);
        messages.setPadding(dp(16), dp(16), dp(16), dp(80));
        transcript.addView(messages, new ViewGroup.LayoutParams(-1, -2));
        content.addView(transcript, weight(1));

        FrameLayout.LayoutParams contentParams = frame(-1, -1, Gravity.TOP);
        contentParams.topMargin = dp(64);
        root.addView(content, contentParams);

        // Composer Bar
        composerRow = buildComposer(c);
        root.addView(composerRow, frame(-1, -2, Gravity.BOTTOM));

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            int bottom = Math.max(bars.bottom, ime.bottom);
            composerRow.setPadding(dp(12), dp(12), dp(12), bottom + dp(12));
            return insets;
        });

        restoreSession();
        return root;
    }

    private LinearLayout buildComposer(Context c) {
        LinearLayout bar = row(c);
        bar.setGravity(Gravity.BOTTOM);
        bar.setPadding(dp(12), dp(12), dp(12), dp(12));
        
        LinearLayout inputArea = row(c);
        inputArea.setGravity(Gravity.BOTTOM);
        inputArea.setBackground(stroke(SURFACE, dp(24), BORDER));
        inputArea.setPadding(dp(8), dp(4), dp(8), dp(4));
        
        MaterialButton attachButton = icon(c, "＋", "Attach");
        inputArea.addView(attachButton, new LinearLayout.LayoutParams(dp(40), dp(40)));
        
        composer = new EditText(c);
        composer.setHint("Message Dayven AI...");
        composer.setHintTextColor(TEXT_SECONDARY);
        composer.setTextColor(TEXT_PRIMARY);
        composer.setBackground(null);
        composer.setTextSize(15);
        composer.setMaxLines(6);
        composer.setPadding(dp(8), dp(8), dp(8), dp(8));
        inputArea.addView(composer, weight(1));
        
        sendButton = icon(c, "↑", "Send");
        sendButton.setBackgroundTintList(ColorStateList.valueOf(ACCENT));
        sendButton.setTextColor(Color.WHITE);
        sendButton.setVisibility(View.GONE);
        sendButton.setOnClickListener(v -> send());
        inputArea.addView(sendButton, new LinearLayout.LayoutParams(dp(40), dp(40)));
        
        bar.addView(inputArea, weight(1));
        
        composer.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                boolean hasText = s != null && s.toString().trim().length() > 0;
                sendButton.setVisibility(hasText ? View.VISIBLE : View.GONE);
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        
        return bar;
    }

    private void restoreSession() {
        messages.removeAllViews();
        if (session.messages.isEmpty()) {
            addAssistant(GREETING);
        } else {
            for (AgentWorkspaceStore.Message m : session.messages) {
                bubble(m.text, "user".equals(m.role));
            }
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
        
        if (profiles.stream().noneMatch(p -> p.enabled && !p.key.isEmpty())) {
            addAssistant("API Key belum dikonfigurasi. Silakan ke Settings untuk menambahkan API Key Gemini.");
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
        LinearLayout container = column(requireContext());
        container.setTag("thinking");
        
        TextView t = label(requireContext(), THINKING, 13, false);
        t.setTextColor(TEXT_SECONDARY);
        t.setPadding(dp(12), dp(8), dp(12), dp(8));
        t.setBackground(round(AI_BUBBLE, 12));
        
        container.addView(t);
        messages.addView(container);
        scrollBottom();
    }

    private void removeThinking() {
        View v = messages.findViewWithTag("thinking");
        if (v != null) messages.removeView(v);
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
            @Override public void onDelta(String text) {}
            @Override public void onComplete(String model, GeminiAutoClient.Usage usage, String remaining, String retry) {}
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
            finishError("No valid AI models found. Please check your API keys in Settings.");
            return;
        }
        streamRoute(request, original, routes, 0);
    }

    private List<Route> routesFor(String prompt) {
        List<Route> out = new ArrayList<>();
        profiles = store.getProfiles();
        
        // Intelligence levels
        boolean complex = MUTATING.matcher(prompt).find() || prompt.length() > 300;
        
        for (AgentWorkspaceStore.Profile p : profiles) {
            if (p.enabled && !p.key.isEmpty()) {
                List<String> models = new ArrayList<>(p.models);
                if (models.isEmpty()) {
                    models.add("gemini-1.5-flash");
                    models.add("gemini-1.5-pro");
                }
                
                // Sort by complexity: Pro for complex, Flash for simple
                models.sort((m1, m2) -> {
                    int r1 = rank(m1, complex);
                    int r2 = rank(m2, complex);
                    return Integer.compare(r1, r2);
                });
                
                for (String m : models) out.add(new Route(p, m));
            }
        }
        return out;
    }

    private int rank(String model, boolean complex) {
        String m = model.toLowerCase();
        if (complex) return m.contains("pro") ? 0 : 1;
        return m.contains("flash") ? 0 : 1;
    }

    private void streamRoute(String request, String original, List<Route> routes, int index) {
        if (index >= routes.size()) {
            finishError("Limit tercapai atau API gagal. Coba tambah API Key cadangan.");
            return;
        }

        Route route = routes.get(index);
        if (!triedRoutes.add(route.profile.id + ":" + route.model)) {
            streamRoute(request, original, routes, index + 1);
            return;
        }

        liveAssistant = null;
        client.stream(route.profile.key, route.model, request, new GeminiAutoClient.Listener() {
            @Override public void onModels(List<GeminiAutoClient.ModelInfo> models, String remaining, String retry) {}
            @Override public void onDelta(String text) {
                main.post(() -> {
                    removeThinking();
                    if (liveAssistant == null) liveAssistant = addAssistantBubble("");
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
        
        LinearLayout col = column(requireContext());
        col.setGravity(user ? Gravity.END : Gravity.START);
        
        TextView bubble = label(requireContext(), user ? text : richText(text), 15, false);
        bubble.setTextColor(TEXT_PRIMARY);
        bubble.setPadding(dp(16), dp(12), dp(16), dp(12));
        bubble.setBackground(round(user ? USER_BUBBLE : AI_BUBBLE, 18));
        bubble.setMaxWidth(Math.round(getResources().getDisplayMetrics().widthPixels * 0.8f));
        
        col.addView(bubble);
        
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMargins(0, dp(4), 0, dp(4));
        line.addView(col, lp);
        
        messages.addView(line);
    }

    private TextView addAssistantBubble(String text) {
        LinearLayout line = row(requireContext());
        line.setGravity(Gravity.START);
        
        TextView bubble = label(requireContext(), richText(text), 15, false);
        bubble.setTextColor(TEXT_PRIMARY);
        bubble.setPadding(dp(16), dp(12), dp(16), dp(12));
        bubble.setBackground(round(AI_BUBBLE, 18));
        bubble.setMaxWidth(Math.round(getResources().getDisplayMetrics().widthPixels * 0.8f));
        
        line.addView(bubble);
        messages.addView(line);
        return bubble;
    }

    private void showProposal(String answer) {
        AgentActionExecutor.Action parsed = AgentActionExecutor.parse(answer);
        if (parsed == null) return;

        LinearLayout card = column(requireContext());
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.setBackground(stroke(SURFACE, dp(16), BORDER));
        
        TextView title = label(requireContext(), "Proposed Changes", 14, true);
        title.setTextColor(ACCENT);
        card.addView(title);
        
        TextView file = label(requireContext(), parsed.target, 12, false);
        file.setTextColor(TEXT_SECONDARY);
        file.setPadding(0, dp(4), 0, dp(8));
        card.addView(file);
        
        LinearLayout actions = row(requireContext());
        MaterialButton cancel = ghostButton("Cancel");
        MaterialButton accept = solidButton("Accept Changes");
        
        actions.addView(cancel, weight(1));
        actions.addView(accept, weight(1.5f));
        card.addView(actions);
        
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(-1, -2);
        cardParams.setMargins(0, dp(12), 0, dp(12));
        messages.addView(card, cardParams);
        
        cancel.setOnClickListener(v -> {
            messages.removeView(card);
            addAssistant("Changes cancelled.");
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
            addAssistant("Proposal diterima. Silakan tinjau dan simpan di Direct XML Editor.");
        } else {
            String res = AgentActionExecutor.apply(requireContext(), projectId, action);
            addAssistant(res.startsWith("APPLIED") ? "Perubahan berhasil diterapkan! ✨" : "Gagal menerapkan: " + res);
        }
    }

    private void showDrawer() {
        final FrameLayout overlay = new FrameLayout(requireContext());
        overlay.setLayoutParams(new FrameLayout.LayoutParams(-1, -1));
        overlay.setBackgroundColor(Color.argb(100, 0, 0, 0));
        
        LinearLayout drawer = column(requireContext());
        drawer.setBackgroundColor(SURFACE);
        drawer.setPadding(dp(20), dp(20), dp(20), dp(20));
        
        TextView head = label(requireContext(), "Chat History", 20, true);
        head.setPadding(0, 0, 0, dp(20));
        drawer.addView(head);
        
        MaterialButton newChat = solidButton("＋ New Chat");
        newChat.setOnClickListener(v -> {
            session = store.newSession("New Chat");
            sessionTitle.setText(session.title);
            restoreSession();
            root.removeView(overlay);
        });
        drawer.addView(newChat, new LinearLayout.LayoutParams(-1, -2));
        
        ScrollView scroll = new ScrollView(requireContext());
        LinearLayout list = column(requireContext());
        for (AgentWorkspaceStore.Session s : store.getSessions()) {
            TextView item = label(requireContext(), s.title, 14, false);
            item.setPadding(dp(12), dp(16), dp(12), dp(16));
            item.setBackground(round(s.id.equals(session.id) ? ACCENT_LIGHT : Color.TRANSPARENT, 8));
            item.setOnClickListener(v -> {
                session = store.getSession(s.id);
                store.selectSession(s.id);
                sessionTitle.setText(session.title);
                restoreSession();
                root.removeView(overlay);
            });
            list.addView(item);
        }
        scroll.addView(list);
        drawer.addView(scroll, weight(1));
        
        overlay.addView(drawer, new FrameLayout.LayoutParams(Math.round(getResources().getDisplayMetrics().widthPixels * 0.8f), -1));
        overlay.setOnClickListener(v -> root.removeView(overlay));
        root.addView(overlay);
    }

    private void showSettings() {
        if (activeSettingsPage != null) return;
        
        final FrameLayout page = new FrameLayout(requireContext());
        page.setLayoutParams(new FrameLayout.LayoutParams(-1, -1));
        activeSettingsPage = page;
        page.setBackgroundColor(BG);
        
        LinearLayout body = column(requireContext());
        body.setLayoutParams(new FrameLayout.LayoutParams(-1, -1));
        body.setPadding(dp(20), dp(40), dp(20), dp(20));
        
        TextView t = label(requireContext(), "AI Providers", 22, true);
        body.addView(t);
        
        ScrollView scroll = new ScrollView(requireContext());
        providerList = column(requireContext());
        scroll.addView(providerList);
        body.addView(scroll, weight(1));
        
        MaterialButton addBtn = ghostButton("＋ Add Gemini API Key");
        addBtn.setOnClickListener(v -> {
            store.addProfile("Gemini " + (store.getProfiles().size() + 1));
            refreshSettings();
        });
        LinearLayout.LayoutParams addParams = new LinearLayout.LayoutParams(-1, -2);
        addParams.topMargin = dp(12);
        body.addView(addBtn, addParams);
        
        MaterialButton close = solidButton("Close");
        close.setOnClickListener(v -> {
            root.removeView(page);
            activeSettingsPage = null;
        });
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(-1, -2);
        closeParams.topMargin = dp(12);
        body.addView(close, closeParams);
        
        page.addView(body);
        root.addView(page);
        refreshSettings();
    }

    private void refreshSettings() {
        if (providerList == null) return;
        providerList.removeAllViews();
        profiles = store.getProfiles();
        
        if (profiles.isEmpty()) {
            TextView empty = label(requireContext(), "No API keys added yet.", 14, false);
            empty.setPadding(0, dp(20), 0, dp(20));
            empty.setGravity(Gravity.CENTER);
            providerList.addView(empty);
            return;
        }
        
        for (AgentWorkspaceStore.Profile p : profiles) {
            LinearLayout card = column(requireContext());
            card.setPadding(dp(16), dp(16), dp(16), dp(16));
            card.setBackground(stroke(SURFACE, dp(12), BORDER));
            
            TextView name = label(requireContext(), p.name, 14, true);
            card.addView(name);
            
            EditText key = new EditText(requireContext());
            key.setText(p.key);
            key.setHint("API Key");
            key.setHintTextColor(TEXT_SECONDARY);
            key.setTextColor(TEXT_PRIMARY);
            key.setTextSize(14);
            key.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            key.setTransformationMethod(PasswordTransformationMethod.getInstance());
            LinearLayout.LayoutParams keyParams = new LinearLayout.LayoutParams(-1, -2);
            keyParams.topMargin = dp(8);
            card.addView(key, keyParams);
            
            TextView status = label(requireContext(), p.lastError.isEmpty() ? (p.models.isEmpty() ? "Not validated" : "Valid: " + p.models.size() + " models") : p.lastError, 11, false);
            status.setTextColor(p.lastError.isEmpty() ? TEXT_SECONDARY : ERROR);
            status.setPadding(0, dp(4), 0, dp(8));
            card.addView(status);
            
            LinearLayout actions = row(requireContext());
            MaterialButton validate = solidButton("Check");
            MaterialButton delete = ghostButton("🗑");
            
            actions.addView(validate, weight(1));
            LinearLayout.LayoutParams delParams = new LinearLayout.LayoutParams(dp(48), dp(48));
            delParams.leftMargin = dp(8);
            actions.addView(delete, delParams);
            card.addView(actions);
            
            validate.setOnClickListener(v -> {
                String val = key.getText().toString().trim();
                p.key = val;
                validateAPI(p);
            });
            
            delete.setOnClickListener(v -> {
                store.removeProfile(p.id);
                refreshSettings();
            });
            
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.topMargin = dp(12);
            providerList.addView(card, lp);
        }
    }

    private void validateAPI(AgentWorkspaceStore.Profile p) {
        client.listModels(p.key, new GeminiAutoClient.Listener() {
            @Override public void onModels(List<GeminiAutoClient.ModelInfo> models, String remaining, String retry) {
                p.models.clear();
                for (GeminiAutoClient.ModelInfo m : models) if (m.text()) p.models.add(m.id());
                p.enabled = !p.models.isEmpty();
                p.lastError = "";
                store.saveProfiles(profiles);
                main.post(() -> {
                    refreshSettings();
                    addAssistant("API Key " + p.name + " berhasil divalidasi! ✅");
                });
            }
            @Override public void onDelta(String text) {}
            @Override public void onComplete(String model, GeminiAutoClient.Usage usage, String remaining, String retry) {}
            @Override public void onError(int status, String message, String remaining, String retry) {
                p.lastError = message;
                p.enabled = false;
                store.saveProfiles(profiles);
                main.post(() -> {
                    refreshSettings();
                    addAssistant("Gagal validasi " + p.name + ": " + message);
                });
            }
        });
    }

    private void startHeartbeat() {
        heartbeat = () -> {
            profiles = store.getProfiles();
            boolean active = profiles.stream().anyMatch(p -> p.enabled && !p.key.isEmpty());
            if (statusText != null) {
                statusText.setText(active ? "Connected" : "Disconnected");
                statusText.setTextColor(active ? SUCCESS : ERROR);
            }
            main.postDelayed(heartbeat, 10000);
        };
        main.post(heartbeat);
    }

    private void stopHeartbeat() {
        if (heartbeat != null) main.removeCallbacks(heartbeat);
    }

    private void finishError(String message) {
        busy = false;
        removeThinking();
        addAssistant(message);
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
    private LinearLayout column(Context c) { LinearLayout l = new LinearLayout(c); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private LinearLayout row(Context c) { LinearLayout l = new LinearLayout(c); l.setOrientation(LinearLayout.HORIZONTAL); return l; }
    private LinearLayout.LayoutParams weight(float w) { return new LinearLayout.LayoutParams(0, -2, w); }
    private FrameLayout.LayoutParams frame(int w, int h, int g) { return new FrameLayout.LayoutParams(w, h, g); }
    private TextView label(Context c, CharSequence t, int s, boolean b) { TextView v = new TextView(c); v.setText(t); v.setTextSize(s); v.setTypeface(null, b ? Typeface.BOLD : Typeface.NORMAL); return v; }
    private MaterialButton icon(Context c, String t, String d) { MaterialButton b = new MaterialButton(c); b.setText(t); b.setContentDescription(d); b.setBackgroundColor(Color.TRANSPARENT); b.setTextColor(TEXT_PRIMARY); return b; }
    
    private MaterialButton solidButton(String t) { 
        MaterialButton b = new MaterialButton(requireContext()); 
        b.setText(t); 
        b.setBackgroundTintList(ColorStateList.valueOf(ACCENT));
        b.setTextColor(Color.WHITE); 
        return b; 
    }
    
    private MaterialButton ghostButton(String t) { 
        MaterialButton b = new MaterialButton(requireContext()); 
        b.setText(t); 
        b.setBackgroundTintList(ColorStateList.valueOf(Color.TRANSPARENT));
        b.setStrokeColor(ColorStateList.valueOf(BORDER));
        b.setStrokeWidth(dp(1));
        b.setTextColor(TEXT_PRIMARY); 
        return b; 
    }
    
    private GradientDrawable round(int c, int r) { GradientDrawable d = new GradientDrawable(); d.setColor(c); d.setCornerRadius(dp(r)); return d; }
    private GradientDrawable stroke(int f, int r, int c) { GradientDrawable d = round(f, r); d.setStroke(dp(1), c); return d; }
    private void scrollBottom() { transcript.post(() -> transcript.fullScroll(View.FOCUS_DOWN)); }
    
    private CharSequence richText(String value) {
        if (value == null) return "";
        SpannableStringBuilder ssb = new SpannableStringBuilder(value);
        int start = value.indexOf("```");
        while (start != -1) {
            int end = value.indexOf("```", start + 3);
            if (end == -1) break;
            ssb.setSpan(new TypefaceSpan("monospace"), start, end + 3, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            ssb.setSpan(new BackgroundColorSpan(CODE_BG), start, end + 3, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            ssb.setSpan(new ForegroundColorSpan(Color.WHITE), start, end + 3, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            start = value.indexOf("```", end + 3);
        }
        return ssb;
    }

    private static final class Route {
        final AgentWorkspaceStore.Profile profile;
        final String model;
        Route(AgentWorkspaceStore.Profile p, String m) { this.profile = p; this.model = m; }
    }
}
