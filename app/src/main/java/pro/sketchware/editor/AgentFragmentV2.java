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
import android.widget.PopupMenu;
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
import com.google.android.material.progressindicator.LinearProgressIndicator;

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

/** Agent chat rebuilt as an explicit safe-area layout. */
public final class AgentFragmentV2 extends Fragment {
    private static final int BG = Color.rgb(10, 10, 10);
    private static final int SURFACE = Color.rgb(28, 28, 30);
    private static final int SURFACE_2 = Color.rgb(38, 38, 40);
    private static final int TEXT = Color.WHITE;
    private static final int MUTED = Color.rgb(155, 155, 160);
    private static final int MINT = Color.rgb(0, 200, 150);
    private static final int AMBER = Color.rgb(224, 177, 92);
    private static final int BORDER = Color.rgb(58, 58, 60);
    private static final Pattern MUTATING = Pattern.compile("(?i)\\b(create|make|build|generate|fix|repair|change|modify|update|buat|bikin|perbaiki|ubah|layout|xml|java|kotlin|c\\+\\+|block|code)\\b");
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
    private LinearLayout thinkingPanel;
    private LinearLayout profileList;
    private TextView thinkingText;
    private TextView statusText;
    private TextView sessionTitle;
    private EditText composer;
    private MaterialButton sendButton;
    private MaterialButton micButton;
    private String projectId;
    private String selectedFile;
    private TextView liveAssistant;
    private String liveAnswer = "";
    private boolean busy;
    private Runnable heartbeat;

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
        root = new FrameLayout(c);
        root.setBackgroundColor(BG);
        root.setClipChildren(true);
        root.setClipToPadding(true);

        content = column(c);
        content.setPadding(dp(14), 0, dp(14), 0);
        root.addView(content, new FrameLayout.LayoutParams(-1, -1));

        header = new FrameLayout(c);
        header.setBackground(roundStroke(SURFACE, dp(22), BORDER, dp(1)));
        header.setElevation(dp(4));
        MaterialButton menu = iconButton(c, "☰", "Chat menu");
        menu.setBackground(roundStroke(SURFACE_2, dp(18), BORDER, dp(1)));
        menu.setOnClickListener(v -> showMenu(menu));
        header.addView(menu, frameParams(dp(48), dp(48), Gravity.START | Gravity.CENTER_VERTICAL));

        LinearLayout title = column(c);
        title.setGravity(Gravity.CENTER);
        sessionTitle = label(c, session.title, 16, false);
        sessionTitle.setGravity(Gravity.CENTER);
        sessionTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.addView(sessionTitle);
        TextView subtitle = label(c, "Dayven AI", 11, false);
        subtitle.setTextColor(MUTED);
        subtitle.setGravity(Gravity.CENTER);
        title.addView(subtitle);
        FrameLayout.LayoutParams titleParams = frameParams(-1, -2, Gravity.CENTER);
        titleParams.leftMargin = dp(60);
        titleParams.rightMargin = dp(60);
        header.addView(title, titleParams);

        LinearLayout actions = row(c);
        MaterialButton settings = iconButton(c, "⚙", "Settings");
        settings.setBackground(roundStroke(SURFACE_2, dp(18), BORDER, dp(1)));
        settings.setOnClickListener(v -> showSettings());
        MaterialButton exit = iconButton(c, "×", "Exit Agent");
        exit.setBackground(roundStroke(SURFACE_2, dp(18), BORDER, dp(1)));
        exit.setOnClickListener(v -> AgentTabEnhancer.exitAgent(requireActivity()));
        actions.addView(settings, new LinearLayout.LayoutParams(dp(48), dp(48)));
        actions.addView(exit, new LinearLayout.LayoutParams(dp(48), dp(48)));
        FrameLayout.LayoutParams actionParams = frameParams(-2, dp(48), Gravity.END | Gravity.CENTER_VERTICAL);
        actionParams.rightMargin = dp(4);
        header.addView(actions, actionParams);
        root.addView(header, frameParams(-1, dp(60), Gravity.TOP));

        LinearLayout status = row(c);
        status.setGravity(Gravity.CENTER_VERTICAL);
        status.setPadding(dp(4), dp(2), dp(4), dp(2));
        TextView dot = label(c, "●", 14, false);
        dot.setGravity(Gravity.CENTER);
        dot.setTextColor(MINT);
        status.addView(dot, new LinearLayout.LayoutParams(dp(40), dp(40)));
        statusText = label(c, "Offline · add an API profile", 12, false);
        statusText.setTextColor(MUTED);
        status.addView(statusText, weight(1));
        TextView auto = label(c, "AUTO", 10, true);
        auto.setGravity(Gravity.CENTER);
        auto.setTextColor(MINT);
        auto.setPadding(dp(12), 0, dp(12), 0);
        auto.setBackground(roundStroke(Color.TRANSPARENT, dp(18), Color.rgb(0, 130, 100), dp(1)));
        status.addView(auto, new LinearLayout.LayoutParams(-2, dp(40)));
        content.addView(status, new LinearLayout.LayoutParams(-1, dp(48)));

        transcript = new ScrollView(c);
        transcript.setFillViewport(true);
        transcript.setSmoothScrollingEnabled(true);
        transcript.setClipToPadding(false);
        messages = column(c);
        messages.setPadding(0, dp(8), 0, dp(8));
        transcript.addView(messages, new ViewGroup.LayoutParams(-1, -2));
        content.addView(transcript, new LinearLayout.LayoutParams(-1, 0, 1));

        thinkingPanel = buildThinking(c);
        thinkingPanel.setVisibility(View.GONE);
        content.addView(thinkingPanel);

        composerRow = buildComposer(c);
        root.addView(composerRow, frameParams(-1, -2, Gravity.BOTTOM));
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> applyInsets(insets));
        ViewCompat.requestApplyInsets(root);
        restoreSession();
        return root;
    }

    private WindowInsetsCompat applyInsets(WindowInsetsCompat insets) {
        Insets bars = insets.getInsets(WindowInsetsCompat.Type.statusBars() | WindowInsetsCompat.Type.displayCutout() | WindowInsetsCompat.Type.navigationBars());
        Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
        int density = Math.round(requireContext().getResources().getDisplayMetrics().density);
        int minTopPx = Math.round(96f);
        int top = Math.max(bars.top + dp(18), minTopPx);
        int bottom = Math.max(bars.bottom, ime.bottom) + dp(12);
        FrameLayout.LayoutParams headerParams = (FrameLayout.LayoutParams) header.getLayoutParams();
        headerParams.topMargin = top;
        header.setLayoutParams(headerParams);
        FrameLayout.LayoutParams contentParams = (FrameLayout.LayoutParams) content.getLayoutParams();
        contentParams.topMargin = top + dp(68);
        contentParams.bottomMargin = bottom + dp(58);
        content.setLayoutParams(contentParams);
        FrameLayout.LayoutParams composerParams = (FrameLayout.LayoutParams) composerRow.getLayoutParams();
        composerParams.leftMargin = dp(10);
        composerParams.rightMargin = dp(10);
        composerParams.bottomMargin = bottom;
        composerRow.setLayoutParams(composerParams);
        if (ime.bottom > 0) {
            root.postDelayed(() -> { scrollBottom(); composer.requestRectangleOnScreen(new android.graphics.Rect(0, 0, composer.getWidth(), composer.getHeight()), false); }, 90);
        }
        return insets;
    }

    private LinearLayout buildComposer(Context c) {
        LinearLayout bar = row(c);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(4), dp(4), dp(4), dp(4));
        bar.setElevation(dp(8));
        bar.setBackground(roundStroke(SURFACE, dp(28), BORDER, dp(1)));
        MaterialButton attach = iconButton(c, "+", "Attach");
        attach.setBackground(round(SURFACE_2, dp(22)));
        bar.addView(attach, new LinearLayout.LayoutParams(dp(48), dp(48)));
        composer = new EditText(c);
        composer.setHint("Message...");
        composer.setHintTextColor(Color.rgb(130, 130, 135));
        composer.setTextColor(TEXT);
        composer.setTextSize(15);
        composer.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        composer.setPadding(dp(10), 0, dp(8), 0);
        composer.setMinHeight(dp(48));
        composer.setMaxLines(5);
        composer.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        composer.setBackgroundColor(Color.TRANSPARENT);
        composer.setOnFocusChangeListener((v, focused) -> { if (focused) v.postDelayed(() -> { ViewCompat.requestApplyInsets(root); scrollBottom(); }, 100); });
        bar.addView(composer, weight(1));
        micButton = iconButton(c, "♩", "Voice input");
        micButton.setBackground(round(SURFACE_2, dp(22)));
        bar.addView(micButton, new LinearLayout.LayoutParams(dp(48), dp(48)));
        sendButton = iconButton(c, "↑", "Send message");
        sendButton.setTextColor(Color.BLACK);
        sendButton.setBackground(round(MINT, dp(24)));
        sendButton.setVisibility(View.GONE);
        bar.addView(sendButton, new LinearLayout.LayoutParams(dp(48), dp(48)));
        sendButton.setOnClickListener(v -> { v.setScaleX(.86f); v.setScaleY(.86f); v.animate().scaleX(1f).scaleY(1f).setDuration(150).start(); send(); });
        composer.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { boolean has = s != null && s.toString().trim().length() > 0; sendButton.setVisibility(has ? View.VISIBLE : View.GONE); micButton.setVisibility(has ? View.GONE : View.VISIBLE); }
            @Override public void afterTextChanged(Editable s) { }
        });
        return bar;
    }

    private LinearLayout buildThinking(Context c) {
        LinearLayout box = row(c);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setPadding(dp(6), dp(2), dp(6), dp(4));
        TextView arrow = label(c, "▶", 11, false);
        arrow.setTextColor(MINT);
        box.addView(arrow, new LinearLayout.LayoutParams(dp(48), dp(32)));
        thinkingText = label(c, "Dayven AI reasoning...", 12, false);
        thinkingText.setTextColor(MUTED);
        box.addView(thinkingText, weight(1));
        LinearProgressIndicator progress = new LinearProgressIndicator(c);
        progress.setIndeterminate(true);
        progress.setTrackThickness(dp(2));
        box.addView(progress, new LinearLayout.LayoutParams(dp(72), dp(8)));
        arrow.setOnClickListener(v -> thinkingText.setText("Read context  ·  route model  ·  stream answer"));
        return box;
    }

    private void restoreSession() { messages.removeAllViews(); if (session.messages.isEmpty()) addAssistant("I’m ready. Ask me to inspect, explain, or prepare a project change. I’ll show the action first and wait for Accept."); else for (AgentWorkspaceStore.Message m : session.messages) bubble(m.text, "user".equals(m.role)); scrollBottom(); }
    private void send() { if (busy) return; String prompt = composer.getText().toString().trim(); if (prompt.isEmpty()) return; if (profiles.stream().noneMatch(p -> p.enabled && !p.key.isEmpty())) { addAssistant("Add a Gemini API profile in Settings first. You only need to do it once."); showSettings(); return; } composer.setText(""); store.append(session.id, "user", prompt); session = store.getSession(session.id); bubble(prompt, true); busy = true; triedRoutes.clear(); thinkingPanel.setVisibility(View.VISIBLE); status("Dayven AI is working...", AMBER); liveAnswer = ""; liveAssistant = null; runDiscoveryThenRoute(promptFor(prompt, AgentProjectContext.build(requireContext(), projectId, selectedFile)), prompt, 0); }
    private String promptFor(String prompt, String context) { return "You are Dayven AI embedded in Sketchware-DayGreen. Work only from this project context. Be concise and accurate. Never claim a change was applied. If the user asks for a mutation, finish with exactly one proposal: TARGET: relative/path/to/file, then one fenced code block with complete replacement content. Use XML, java, kotlin, cpp, or blocks/<JavaFile>/<eventKey> as appropriate. Do not invent block formats.\n\nPROJECT CONTEXT:\n" + context + "\n\nUSER REQUEST:\n" + prompt; }

    private void runDiscoveryThenRoute(String request, String original, int index) { profiles = store.getProfiles(); if (index >= profiles.size()) { runRoutes(request, original); return; } AgentWorkspaceStore.Profile p = profiles.get(index); if (!p.enabled || p.key.isEmpty() || !p.models.isEmpty()) { runDiscoveryThenRoute(request, original, index + 1); return; } status("Checking " + p.name + " models...", AMBER); client.listModels(p.key, new GeminiAutoClient.Listener() { @Override public void onModels(List<GeminiAutoClient.ModelInfo> models, String remaining, String retry) { p.models.clear(); for (GeminiAutoClient.ModelInfo m : models) p.models.add(m.id()); p.lastError = models.isEmpty() ? "No text models" : ""; p.rate = remaining; store.saveProfiles(profiles); main.post(() -> runDiscoveryThenRoute(request, original, index + 1)); } @Override public void onDelta(String text) { } @Override public void onComplete(String model, GeminiAutoClient.Usage usage, String remaining, String retry) { } @Override public void onError(int status, String message, String remaining, String retry) { p.lastError = message; p.failures++; store.saveProfiles(profiles); main.post(() -> runDiscoveryThenRoute(request, original, index + 1)); } }); }
    private void runRoutes(String request, String original) { List<Route> routes = routesFor(original); if (routes.isEmpty()) { finishError("No valid Gemini model is available. Open Settings and validate at least one API profile."); return; } streamRoute(request, original, routes, 0); }
    private List<Route> routesFor(String prompt) { List<Route> out = new ArrayList<>(); profiles = store.getProfiles(); boolean hard = prompt.length() > 240 || MUTATING.matcher(prompt).find() || prompt.toLowerCase(Locale.US).contains("error"); boolean easy = prompt.length() < 90 && !MUTATING.matcher(prompt).find(); for (AgentWorkspaceStore.Profile p : profiles) if (p.enabled && !p.key.isEmpty()) { List<String> models = new ArrayList<>(p.models); models.sort(Comparator.comparingInt(m -> rank(m, hard, easy))); for (String m : models) out.add(new Route(p, m)); } return out; }
    private int rank(String model, boolean hard, boolean easy) { String m = model.toLowerCase(Locale.US); boolean pro = m.contains("pro"); boolean lite = m.contains("lite"); if (hard) return pro ? 0 : lite ? 3 : 1; if (easy) return lite ? 0 : pro ? 3 : 1; return m.contains("flash") && !lite ? 0 : pro ? 1 : 2; }
    private void streamRoute(String request, String original, List<Route> routes, int index) { if (index >= routes.size()) { finishError("All configured Gemini profiles or their available quotas failed. Add another profile or wait for reset."); return; } Route route = routes.get(index); String key = route.profile.id + ":" + route.model; if (!triedRoutes.add(key)) { streamRoute(request, original, routes, index + 1); return; } status("Connected · " + route.profile.name + " · auto", MINT); thinkingPanel.setVisibility(View.VISIBLE); final StringBuilder answer = new StringBuilder(); client.stream(route.profile.key, route.model, request, new GeminiAutoClient.Listener() { @Override public void onModels(List<GeminiAutoClient.ModelInfo> models, String remaining, String retryAfter) { } @Override public void onDelta(String text) { main.post(() -> { if (liveAssistant == null) liveAssistant = addAssistantBubble(""); liveAnswer += text; liveAssistant.setText(liveAnswer); scrollBottom(); }); } @Override public void onComplete(String model, GeminiAutoClient.Usage usage, String remaining, String retryAfter) { main.post(() -> { busy = false; thinkingPanel.setVisibility(View.GONE); status("Connected · " + route.profile.name + (usage.label().isEmpty() ? "" : " · " + usage.label()), MINT); if (liveAssistant == null) liveAssistant = addAssistantBubble(""); liveAssistant.setText(liveAnswer); store.append(session.id, "assistant", liveAnswer); session = store.getSession(session.id); if (MUTATING.matcher(original).find()) showProposal(liveAnswer); }); } @Override public void onError(int status, String message, String remaining, String retryAfter) { main.post(() -> { route.profile.lastError = message; route.profile.failures++; route.profile.rate = remaining.isEmpty() ? retryAfter : remaining; store.saveProfiles(profiles); if (status == 429 || status == 403 || status == 404 || status == 0) streamRoute(request, original, routes, index + 1); else finishError(message); }); } }); }
    private void showProposal(String answer) { AgentActionExecutor.Action parsed = AgentActionExecutor.parse(answer); LinearLayout box = column(requireContext()); box.setPadding(dp(12), dp(8), dp(12), dp(8)); TextView heading = label(requireContext(), "Proposed change", 13, true); heading.setTextColor(MINT); box.addView(heading); box.addView(label(requireContext(), parsed == null ? "No safe target was found, so nothing can be applied automatically." : "TARGET  " + parsed.target + "\n" + preview(parsed.content), 12, false)); LinearLayout buttons = row(requireContext()); MaterialButton cancel = smallButton("Cancel"); MaterialButton accept = smallButton("Accept"); buttons.addView(cancel, weight(1)); buttons.addView(accept, weight(1)); box.addView(buttons); messages.addView(box); cancel.setOnClickListener(v -> { box.setVisibility(View.GONE); addAssistant("Cancelled. Nothing was changed."); }); accept.setOnClickListener(v -> { box.setVisibility(View.GONE); accept(parsed); }); scrollBottom(); }
    private void accept(AgentActionExecutor.Action action) { if (action == null) { addAssistant("I did not apply anything because the proposal was incomplete."); return; } if (action.isXml()) { Intent i = new Intent(requireContext(), ViewCodeEditorActivity.class); i.putExtra("sc_id", projectId); i.putExtra("title", action.target); i.putExtra("content", action.content); startActivity(i); addAssistant("Accepted. XML is open in Direct XML and remains under Sketchware model sync."); return; } String result = AgentActionExecutor.apply(requireContext(), projectId, action); addAssistant(result.startsWith("APPLIED") ? "Accepted. " + result : "Not applied: " + result); }
    private void showMenu(View anchor) { PopupMenu p = new PopupMenu(requireContext(), anchor); p.getMenu().add("New chat").setOnMenuItemClickListener(i -> { session = store.newSession("New chat"); sessionTitle.setText(session.title); restoreSession(); return true; }); p.getMenu().add("Chat history").setOnMenuItemClickListener(i -> { showHistory(); return true; }); p.getMenu().add("Rate limit").setOnMenuItemClickListener(i -> { Toast.makeText(requireContext(), "Usage is shown beside the connection status after each response.", Toast.LENGTH_SHORT).show(); return true; }); p.getMenu().add("Settings").setOnMenuItemClickListener(i -> { showSettings(); return true; }); p.show(); }
    private void showHistory() { LinearLayout list = column(requireContext()); list.setPadding(dp(4), dp(4), dp(4), dp(4)); for (AgentWorkspaceStore.Session s : store.getSessions()) { TextView item = label(requireContext(), s.title, 14, false); item.setTextColor(TEXT); item.setPadding(dp(12), dp(14), dp(12), dp(14)); item.setBackground(round(SURFACE_2, 12)); item.setMinHeight(dp(48)); item.setOnClickListener(v -> { session = store.getSession(s.id); store.selectSession(s.id); sessionTitle.setText(session.title); restoreSession(); }); list.addView(item, new LinearLayout.LayoutParams(-1, -2)); } if (list.getChildCount() == 0) list.addView(label(requireContext(), "No saved chats", 14, false)); new MaterialAlertDialogBuilder(requireContext()).setTitle("Chat history").setView(list).setPositiveButton("Close", null).show(); }
    private void showSettings() { LinearLayout page = column(requireContext()); page.setBackgroundColor(BG); page.setPadding(dp(16), dp(12), dp(16), dp(12)); LinearLayout top = row(requireContext()); MaterialButton back = iconButton(requireContext(), "‹", "Back"); back.setOnClickListener(v -> ((ViewGroup) page.getParent()).removeView(page)); top.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48))); top.addView(label(requireContext(), "AI settings", 20, true), weight(1)); page.addView(top); page.addView(label(requireContext(), "Auto routing and live model discovery", 12, false)); profileList = column(requireContext()); page.addView(profileList, new LinearLayout.LayoutParams(-1, 0, 1)); MaterialButton add = smallButton("＋  Add API profile"); add.setOnClickListener(v -> { store.addProfile("Gemini " + (store.getProfiles().size() + 1)); renderProfiles(); }); page.addView(add); ((FrameLayout) requireView()).addView(page, new FrameLayout.LayoutParams(-1, -1)); renderProfiles(); }
    private void renderProfiles() { if (profileList == null) return; profileList.removeAllViews(); profiles = store.getProfiles(); for (AgentWorkspaceStore.Profile p : profiles) addProfileRow(p); }
    private void addProfileRow(AgentWorkspaceStore.Profile p) { LinearLayout block = column(requireContext()); block.setPadding(dp(12), dp(10), dp(12), dp(10)); block.setBackground(round(SURFACE, 16)); TextView name = label(requireContext(), p.name + (p.models.isEmpty() ? "  ·  not checked" : "  ·  " + p.models.size() + " models"), 15, true); block.addView(name); EditText key = field("", p.key.isEmpty() ? "Paste Gemini API key" : "Saved key, paste only to replace"); block.addView(key); TextView info = label(requireContext(), p.lastError.isEmpty() ? "Only real text models are used." : p.lastError, 11, false); info.setTextColor(MUTED); block.addView(info); LinearLayout actions = row(requireContext()); MaterialButton check = smallButton("Check"); MaterialButton save = smallButton("Save"); actions.addView(check, weight(1)); actions.addView(save, weight(1)); block.addView(actions); check.setOnClickListener(v -> { String candidate = key.getText().toString().trim(); if (candidate.isEmpty()) candidate = p.key; if (candidate.isEmpty()) { info.setText("Paste a key first."); return; } p.key = candidate; info.setText("Checking..."); client.listModels(candidate, new GeminiAutoClient.Listener() { @Override public void onModels(List<GeminiAutoClient.ModelInfo> models, String remaining, String retry) { p.models.clear(); for (GeminiAutoClient.ModelInfo m : models) p.models.add(m.id()); p.lastError = models.isEmpty() ? "No text-capable model is available." : "Validated · auto mode ready"; p.rate = remaining; store.saveProfiles(profiles); main.post(() -> { info.setText(p.lastError); renderProfiles(); }); } @Override public void onDelta(String text) { } @Override public void onComplete(String model, GeminiAutoClient.Usage usage, String remaining, String retry) { } @Override public void onError(int status, String message, String remaining, String retry) { p.lastError = message; store.saveProfiles(profiles); main.post(() -> info.setText(message)); } }); }); save.setOnClickListener(v -> { if (!key.getText().toString().trim().isEmpty()) p.key = key.getText().toString().trim(); store.saveProfiles(profiles); Toast.makeText(requireContext(), "Profile saved", Toast.LENGTH_SHORT).show(); }); profileList.addView(block, new LinearLayout.LayoutParams(-1, -2)); }

    private void bubble(String text, boolean user) { LinearLayout line = row(requireContext()); line.setGravity(user ? Gravity.END : Gravity.START | Gravity.CENTER_VERTICAL); if (!user) line.addView(avatar(), new LinearLayout.LayoutParams(dp(32), dp(32))); LinearLayout col = column(requireContext()); col.setGravity(user ? Gravity.END : Gravity.START); TextView bubble = label(requireContext(), text, 15, false); bubble.setTextColor(Color.WHITE); bubble.setTextIsSelectable(true); bubble.setMaxWidth(Math.round(requireContext().getResources().getDisplayMetrics().widthPixels * .75f)); bubble.setPadding(dp(12), dp(10), dp(12), dp(10)); bubble.setBackground(round(user ? MINT : SURFACE, 18)); col.addView(bubble); TextView time = label(requireContext(), new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date()), 10, false); time.setTextColor(MUTED); col.addView(time); LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, -2); p.setMargins(user ? dp(48) : dp(8), dp(4), user ? 0 : dp(48), dp(4)); line.addView(col, p); messages.addView(line, new LinearLayout.LayoutParams(-1, -2)); line.setAlpha(0f); line.setTranslationY(dp(8)); line.animate().alpha(1f).translationY(0).setDuration(150).start(); }
    private TextView addAssistantBubble(String text) { bubble(text, false); return (TextView) ((ViewGroup) messages.getChildAt(messages.getChildCount() - 1)).findViewByType(TextView.class); }
    private TextView avatar() { TextView a = label(requireContext(), "+", 17, true); a.setGravity(Gravity.CENTER); a.setTextColor(MINT); a.setBackground(roundStroke(SURFACE_2, dp(18), Color.rgb(0, 130, 100), dp(1))); return a; }
    private void addAssistant(String text) { bubble(text, false); }
    private void scrollBottom() { if (transcript != null) transcript.post(() -> { View child = transcript.getChildAt(0); if (child != null) transcript.smoothScrollTo(0, child.getBottom()); }); }
    private void showThinking(String text) { thinkingText.setText(text); thinkingPanel.setVisibility(View.VISIBLE); }
    private void status(String text, int color) { if (statusText != null) { statusText.setText(text); statusText.setTextColor(color); } }
    private void finishError(String text) { busy = false; thinkingPanel.setVisibility(View.GONE); status(text, Color.RED); addAssistant(text); }
    private String preview(String value) { return value.length() > 1600 ? value.substring(0, 1600) + "…" : value; }
    private LinearLayout column(Context c) { LinearLayout l = new LinearLayout(c); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private LinearLayout row(Context c) { LinearLayout l = new LinearLayout(c); l.setOrientation(LinearLayout.HORIZONTAL); return l; }
    private LinearLayout.LayoutParams weight(float w) { return new LinearLayout.LayoutParams(0, -2, w); }
    private FrameLayout.LayoutParams frameParams(int width, int height, int gravity) { FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(width, height, gravity); return p; }
    private TextView label(Context c, String text, int size, boolean bold) { TextView v = new TextView(c); v.setText(text); v.setTextSize(size); v.setTextColor(TEXT); v.setTypeface(null, bold ? Typeface.BOLD : Typeface.NORMAL); return v; }
    private EditText field(String value, String hint) { EditText e = new EditText(requireContext()); e.setText(value); e.setHint(hint); e.setHintTextColor(MUTED); e.setTextColor(TEXT); e.setTextSize(14); e.setSingleLine(true); e.setMinHeight(dp(48)); e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD); e.setTransformationMethod(PasswordTransformationMethod.getInstance()); e.setPadding(dp(12), dp(9), dp(12), dp(9)); e.setBackground(round(SURFACE_2, 12)); return e; }
    private MaterialButton iconButton(Context c, String text, String description) { MaterialButton b = new MaterialButton(c); b.setText(text); b.setContentDescription(description); b.setTextSize(18); b.setTextColor(TEXT); b.setMinHeight(dp(48)); b.setMinWidth(dp(48)); b.setPadding(0, 0, 0, 0); b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.TRANSPARENT)); return b; }
    private MaterialButton smallButton(String text) { MaterialButton b = new MaterialButton(requireContext()); b.setText(text); b.setTextSize(12); b.setTextColor(TEXT); b.setMinHeight(dp(48)); b.setMinWidth(dp(48)); b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(SURFACE_2)); return b; }
    private GradientDrawable round(int color, int radius) { GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d; }
    private GradientDrawable roundStroke(int fill, int radius, int strokeColor, int strokeWidth) { GradientDrawable d = round(fill, radius); d.setStroke(strokeWidth, strokeColor); return d; }
    private int dp(int value) { return Math.round(value * requireContext().getResources().getDisplayMetrics().density); }
    private void startHeartbeat() { stopHeartbeat(); heartbeat = () -> { profiles = store.getProfiles(); boolean connected = profiles.stream().anyMatch(p -> p.enabled && !p.key.isEmpty()); status(connected ? "Connected · auto" : "Offline · add an API profile", connected ? MINT : MUTED); main.postDelayed(heartbeat, 30000); }; main.post(heartbeat); }
    private void stopHeartbeat() { if (heartbeat != null) main.removeCallbacks(heartbeat); heartbeat = null; }
    private static final class Route { final AgentWorkspaceStore.Profile profile; final String model; Route(AgentWorkspaceStore.Profile profile, String model) { this.profile = profile; this.model = model; } }
}
