package pro.sketchware.editor;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import pro.sketchware.activities.editor.view.ViewCodeEditorActivity;

/** Dark, compact, session-aware Agent surface. All writes remain behind Accept. */
public final class AgentFragmentV2 extends Fragment {
    private static final int BG = Color.rgb(15, 20, 18);
    private static final int SURFACE = Color.rgb(22, 29, 26);
    private static final int SURFACE_2 = Color.rgb(29, 39, 35);
    private static final int TEXT = Color.rgb(228, 237, 231);
    private static final int MUTED = Color.rgb(150, 164, 156);
    private static final int MINT = Color.rgb(157, 229, 198);
    private static final int AMBER = Color.rgb(224, 177, 92);
    private static final Pattern MUTATING = Pattern.compile("(?i)\\b(create|make|build|generate|fix|repair|change|modify|update|buat|bikin|perbaiki|ubah|layout|xml|java|kotlin|c\\+\\+|block|code)\\b");

    private final Handler main = new Handler(Looper.getMainLooper());
    private final GeminiAutoClient client = new GeminiAutoClient();
    private final Set<String> triedRoutes = new HashSet<>();
    private AgentWorkspaceStore store;
    private AgentWorkspaceStore.Session session;
    private List<AgentWorkspaceStore.Profile> profiles = new ArrayList<>();
    private LinearLayout messages;
    private ScrollView transcript;
    private LinearLayout settingsView;
    private LinearLayout profileList;
    private LinearLayout thinkingPanel;
    private LinearLayout composerRow;
    private TextView thinkingText;
    private TextView statusText;
    private TextView sessionTitle;
    private EditText composer;
    private TextView liveAssistant;
    private String liveAnswer = "";
    private String projectId;
    private String selectedFile;
    private boolean busy;
    private Runnable heartbeat;

    public static AgentFragmentV2 newInstance(String projectId, String selectedFile) {
        AgentFragmentV2 f = new AgentFragmentV2();
        Bundle b = new Bundle(); b.putString("project_id", projectId == null ? "" : projectId); b.putString("selected_file", selectedFile == null ? "" : selectedFile); f.setArguments(b); return f;
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
        FrameLayout root = new FrameLayout(c); root.setBackgroundColor(BG);
        LinearLayout agent = column(c); agent.setPadding(dp(16), dp(18), dp(16), dp(10)); root.addView(agent, new FrameLayout.LayoutParams(-1, -1));
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            int bottomInset = Math.max(bars.bottom, ime.bottom);
            agent.setPadding(dp(16), dp(18) + bars.top, dp(16), dp(10) + bottomInset);
            if (ime.bottom > 0) {
                root.post(() -> {
                    if (transcript != null) transcript.fullScroll(View.FOCUS_DOWN);
                    if (composer != null && composer.getWidth() > 0 && composer.getHeight() > 0) {
                        composer.requestRectangleOnScreen(new android.graphics.Rect(0, 0, composer.getWidth(), composer.getHeight()), false);
                    }
                });
            }
            return insets;
        });
        ViewCompat.requestApplyInsets(root);
        LinearLayout header = row(c); header.setGravity(Gravity.CENTER_VERTICAL);
        MaterialButton menu = iconButton(c, "☰", "Chat menu"); menu.setOnClickListener(v -> showMenu(menu)); header.addView(menu, new LinearLayout.LayoutParams(dp(44), dp(44)));
        LinearLayout titleBox = column(c); titleBox.setGravity(Gravity.CENTER_VERTICAL); sessionTitle = label(c, session.title, 16, true); titleBox.addView(sessionTitle); TextView sub = label(c, "Dayven AI", 11, false); sub.setTextColor(MUTED); titleBox.addView(sub); header.addView(titleBox, weight(1));
        MaterialButton settings = iconButton(c, "⚙", "Settings"); settings.setOnClickListener(v -> showSettings()); header.addView(settings, new LinearLayout.LayoutParams(dp(44), dp(44)));
        MaterialButton exit = iconButton(c, "×", "Exit Agent"); exit.setOnClickListener(v -> AgentTabEnhancer.exitAgent(requireActivity())); header.addView(exit, new LinearLayout.LayoutParams(dp(44), dp(44))); agent.addView(header);

        LinearLayout connection = row(c); connection.setGravity(Gravity.CENTER_VERTICAL); TextView dot = label(c, "●", 14, false); dot.setTextColor(MINT); connection.addView(dot, new LinearLayout.LayoutParams(dp(22), dp(34))); statusText = label(c, "Ready", 12, false); statusText.setTextColor(MUTED); connection.addView(statusText, weight(1)); TextView auto = label(c, "AUTO", 10, true); auto.setTextColor(MINT); auto.setPadding(dp(8), dp(3), dp(8), dp(3)); auto.setBackground(round(SURFACE_2, 10)); connection.addView(auto); agent.addView(connection);

        transcript = new ScrollView(c); transcript.setFillViewport(true); messages = column(c); messages.setPadding(0, dp(8), 0, dp(8)); transcript.addView(messages, new ViewGroup.LayoutParams(-1, -2)); agent.addView(transcript, new LinearLayout.LayoutParams(-1, 0, 1));
        thinkingPanel = buildThinking(c); thinkingPanel.setVisibility(View.GONE); agent.addView(thinkingPanel);
        composerRow = row(c); composerRow.setGravity(Gravity.BOTTOM); composer = new EditText(c); composer.setHint("Ask Dayven AI anything..."); composer.setHintTextColor(Color.rgb(111, 127, 118)); composer.setTextColor(TEXT); composer.setTextSize(15); composer.setGravity(Gravity.TOP | Gravity.START); composer.setPadding(dp(14), dp(12), dp(12), dp(12)); composer.setMinLines(1); composer.setMaxLines(5); composer.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_MULTI_LINE); composer.setOnFocusChangeListener((v, hasFocus) -> { if (hasFocus) v.postDelayed(() -> { ViewCompat.requestApplyInsets(root); if (transcript != null) transcript.fullScroll(View.FOCUS_DOWN); composer.requestRectangleOnScreen(new android.graphics.Rect(0, 0, composer.getWidth(), composer.getHeight()), false); }, 160); }); composerRow.addView(composer, weight(1)); MaterialButton send = iconButton(c, "↑", "Send"); send.setTextColor(BG); send.setBackgroundTintList(android.content.res.ColorStateList.valueOf(MINT)); send.setOnClickListener(v -> send()); composerRow.addView(send, new LinearLayout.LayoutParams(dp(48), dp(48))); agent.addView(composerRow);
        restoreSession(); return root;
    }

    private LinearLayout buildThinking(Context c) {
        LinearLayout box = row(c); box.setGravity(Gravity.CENTER_VERTICAL); box.setPadding(dp(6), dp(2), dp(6), dp(4)); TextView arrow = label(c, "▶", 11, false); arrow.setTextColor(MINT); arrow.setPadding(dp(4), dp(4), dp(10), dp(4)); box.addView(arrow); thinkingText = label(c, "Dayven AI resesioning...", 12, false); thinkingText.setTextColor(MUTED); box.addView(thinkingText, weight(1)); LinearProgressIndicator line = new LinearProgressIndicator(c); line.setIndeterminate(true); line.setTrackThickness(dp(2)); box.addView(line, new LinearLayout.LayoutParams(dp(72), dp(8))); arrow.setOnClickListener(v -> thinkingText.setText(thinkingText.getText().toString().contains("Read") ? "Dayven AI resesioning..." : "Read context  ·  route model  ·  stream answer")); return box;
    }

    private void restoreSession() {
        messages.removeAllViews();
        if (session.messages.isEmpty()) addAssistant("I’m ready. Ask me to inspect, explain, or prepare a project change. I’ll show the action first and wait for Accept.");
        else for (AgentWorkspaceStore.Message m : session.messages) bubble(m.text, "user".equals(m.role));
        scrollBottom();
    }

    private void send() {
        if (busy) return; String prompt = composer == null ? "" : composer.getText().toString().trim(); if (prompt.isEmpty()) return;
        if (profiles.stream().noneMatch(p -> p.enabled && !p.key.isEmpty())) { addAssistant("Add a Gemini API profile in Settings first. You only need to do it once."); showSettings(); return; }
        composer.setText(""); store.append(session.id, "user", prompt); session = store.getSession(session.id); bubble(prompt, true); busy = true; triedRoutes.clear(); thinkingPanel.setVisibility(View.VISIBLE); status("Dayven AI is working...", AMBER);
        String context = AgentProjectContext.build(requireContext(), projectId, selectedFile); String request = promptFor(prompt, context); liveAnswer = ""; liveAssistant = null; runDiscoveryThenRoute(request, prompt, 0);
    }

    private String promptFor(String prompt, String context) { return "You are Dayven AI embedded in Sketchware-DayGreen. Work only from this project context. Be concise and accurate. Never claim a change was applied. If the user asks for a mutation, finish with exactly one proposal: TARGET: relative/path/to/file, then one fenced code block with complete replacement content. Use XML, java, kotlin, cpp, or blocks/<JavaFile>/<eventKey> as appropriate. Do not invent block formats.\n\nPROJECT CONTEXT:\n" + context + "\n\nUSER REQUEST:\n" + prompt; }

    private void runDiscoveryThenRoute(String request, String original, int profileIndex) {
        profiles = store.getProfiles(); if (profileIndex >= profiles.size()) { runRoutes(request, original); return; }
        AgentWorkspaceStore.Profile p = profiles.get(profileIndex); if (!p.enabled || p.key.isEmpty() || !p.models.isEmpty()) { runDiscoveryThenRoute(request, original, profileIndex + 1); return; }
        status("Checking " + p.name + " models...", AMBER); client.listModels(p.key, new GeminiAutoClient.Listener() {
            @Override public void onModels(List<GeminiAutoClient.ModelInfo> models, String remaining, String retry) { p.models.clear(); for (GeminiAutoClient.ModelInfo m : models) p.models.add(m.id()); p.lastError = models.isEmpty() ? "No text models" : ""; p.rate = remaining; store.saveProfiles(profiles); main.post(() -> runDiscoveryThenRoute(request, original, profileIndex + 1)); }
            @Override public void onDelta(String text) { }
            @Override public void onComplete(String model, GeminiAutoClient.Usage usage, String remaining, String retry) { }
            @Override public void onError(int status, String message, String remaining, String retry) { p.lastError = message; p.failures++; store.saveProfiles(profiles); main.post(() -> runDiscoveryThenRoute(request, original, profileIndex + 1)); }
        });
    }

    private void runRoutes(String request, String original) {
        List<Route> routes = routesFor(original); if (routes.isEmpty()) { finishError("No valid Gemini model is available. Open Settings and validate at least one API profile."); return; } streamRoute(request, original, routes, 0);
    }

    private List<Route> routesFor(String prompt) {
        List<Route> out = new ArrayList<>(); profiles = store.getProfiles(); boolean hard = prompt.length() > 240 || MUTATING.matcher(prompt).find() || prompt.toLowerCase(Locale.US).contains("error"); boolean easy = prompt.length() < 90 && !MUTATING.matcher(prompt).find();
        for (AgentWorkspaceStore.Profile p : profiles) if (p.enabled && !p.key.isEmpty()) { List<String> models = new ArrayList<>(p.models); models.sort(Comparator.comparingInt(m -> rank(m, hard, easy))); for (String m : models) out.add(new Route(p, m)); }
        return out;
    }

    private int rank(String model, boolean hard, boolean easy) { String m = model.toLowerCase(Locale.US); boolean pro = m.contains("pro"); boolean lite = m.contains("lite"); if (hard) return pro ? 0 : lite ? 3 : 1; if (easy) return lite ? 0 : pro ? 3 : 1; return m.contains("flash") && !lite ? 0 : pro ? 1 : 2; }

    private void streamRoute(String request, String original, List<Route> routes, int index) {
        if (index >= routes.size()) { finishError("All configured Gemini profiles or their available quotas failed. Add another profile or wait for reset."); return; }
        Route route = routes.get(index); String key = route.profile.id + ":" + route.model; if (!triedRoutes.add(key)) { streamRoute(request, original, routes, index + 1); return; }
        status("Connected · " + route.profile.name + " · auto", MINT); showThinking("Dayven AI resesioning..."); final StringBuilder answer = new StringBuilder(); liveAnswer = "";
        client.stream(route.profile.key, route.model, request, new GeminiAutoClient.Listener() {
            @Override public void onModels(List<GeminiAutoClient.ModelInfo> models, String remaining, String retryAfter) { }
            @Override public void onDelta(String text) { main.post(() -> { if (liveAssistant == null) liveAssistant = addAssistantBubble(""); liveAnswer += text; liveAssistant.setText(liveAnswer); scrollBottom(); }); }
            @Override public void onComplete(String model, GeminiAutoClient.Usage usage, String remaining, String retryAfter) { main.post(() -> { busy = false; thinkingPanel.setVisibility(View.GONE); status("Connected · " + route.profile.name + (usage.label().isEmpty() ? "" : " · " + usage.label()), MINT); if (liveAssistant == null) liveAssistant = addAssistantBubble(""); liveAssistant.setText(liveAnswer); store.append(session.id, "assistant", liveAnswer); session = store.getSession(session.id); if (MUTATING.matcher(original).find()) showProposal(liveAnswer); }); }
            @Override public void onError(int status, String message, String remaining, String retryAfter) { main.post(() -> { route.profile.lastError = message; route.profile.failures++; route.profile.rate = remaining.isEmpty() ? retryAfter : remaining; store.saveProfiles(profiles); if (status == 429 || status == 403 || status == 404 || status == 0) streamRoute(request, original, routes, index + 1); else finishError(message); }); }
        });
    }

    private void showProposal(String answer) {
        AgentActionExecutor.Action action = AgentActionExecutor.parse(answer); LinearLayout box = column(requireContext()); box.setPadding(dp(12), dp(8), dp(12), dp(8)); TextView heading = label(requireContext(), "Proposed change", 13, true); heading.setTextColor(MINT); box.addView(heading); box.addView(label(requireContext(), action == null ? "No safe target was found, so nothing can be applied automatically." : "TARGET  " + action.target + "\n" + preview(action.content), 12, false)); LinearLayout buttons = row(requireContext()); MaterialButton cancel = smallButton("Cancel"); MaterialButton accept = smallButton("Accept"); buttons.addView(cancel, weight(1)); buttons.addView(accept, weight(1)); box.addView(buttons); messages.addView(box); cancel.setOnClickListener(v -> { box.setVisibility(View.GONE); addAssistant("Cancelled. Nothing was changed."); }); accept.setOnClickListener(v -> { box.setVisibility(View.GONE); accept(action); }); scrollBottom();
    }

    private void accept(AgentActionExecutor.Action action) { if (action == null) { addAssistant("I did not apply anything because the proposal was incomplete."); return; } if (action.isXml()) { Intent i = new Intent(requireContext(), ViewCodeEditorActivity.class); i.putExtra("sc_id", projectId); i.putExtra("title", action.target); i.putExtra("content", action.content); startActivity(i); addAssistant("Accepted. XML is open in Direct XML and remains under Sketchware model sync."); return; } String result = AgentActionExecutor.apply(requireContext(), projectId, action); addAssistant(result.startsWith("APPLIED") ? "Accepted. " + result : "Not applied: " + result); }

    private void showMenu(View anchor) { PopupMenu p = new PopupMenu(requireContext(), anchor); p.getMenu().add("New chat").setOnMenuItemClickListener(i -> { session = store.newSession("New chat"); sessionTitle.setText(session.title); restoreSession(); return true; }); p.getMenu().add("Chat history").setOnMenuItemClickListener(i -> { showHistory(); return true; }); p.getMenu().add("Rate limit").setOnMenuItemClickListener(i -> { Toast.makeText(requireContext(), "Usage is shown beside the connection status after each response.", Toast.LENGTH_SHORT).show(); return true; }); p.getMenu().add("Settings").setOnMenuItemClickListener(i -> { showSettings(); return true; }); p.show(); }

    private void showHistory() { LinearLayout list = column(requireContext()); list.setPadding(dp(4), dp(4), dp(4), dp(4)); List<AgentWorkspaceStore.Session> sessions = store.getSessions(); for (AgentWorkspaceStore.Session s : sessions) { TextView item = label(requireContext(), s.title, 14, false); item.setTextColor(TEXT); item.setPadding(dp(12), dp(14), dp(12), dp(14)); item.setBackground(round(SURFACE_2, 12)); item.setOnClickListener(v -> { session = store.getSession(s.id); store.selectSession(s.id); sessionTitle.setText(session.title); restoreSession(); }); item.setOnLongClickListener(v -> { new MaterialAlertDialogBuilder(requireContext()).setTitle("Delete chat?").setMessage("This permanently deletes the full conversation.").setNegativeButton("Cancel", null).setPositiveButton("Delete", (d, w) -> { store.deleteSession(s.id); session = store.getOrCreateCurrent(); sessionTitle.setText(session.title); restoreSession(); }).show(); return true; }); list.addView(item, new LinearLayout.LayoutParams(-1, -2) {{ setMargins(0, 0, 0, dp(6)); }}); } if (sessions.isEmpty()) list.addView(label(requireContext(), "No saved chats", 14, false)); new MaterialAlertDialogBuilder(requireContext()).setTitle("Chat history").setView(list).setPositiveButton("Close", null).show(); }

    private void showSettings() { settingsView = buildSettingsView(requireContext()); ((ViewGroup) requireView()).findViewById(android.R.id.content); FrameLayout root = (FrameLayout) requireView(); root.addView(settingsView, new FrameLayout.LayoutParams(-1, -1)); }

    private LinearLayout buildSettingsView(Context c) { LinearLayout page = column(c); page.setBackgroundColor(BG); page.setPadding(dp(16), dp(12), dp(16), dp(12)); LinearLayout h = row(c); MaterialButton back = iconButton(c, "‹", "Back"); back.setOnClickListener(v -> { ((ViewGroup) page.getParent()).removeView(page); settingsView = null; }); h.addView(back, new LinearLayout.LayoutParams(dp(44), dp(44))); h.addView(label(c, "AI settings", 20, true), weight(1)); page.addView(h); page.addView(label(c, "Auto routing, live model discovery, and quota rotation", 12, false)); profileList = column(c); page.addView(profileList, new LinearLayout.LayoutParams(-1, 0, 1)); MaterialButton add = smallButton("＋  Add API profile"); add.setOnClickListener(v -> { store.addProfile("Gemini " + (store.getProfiles().size() + 1)); profiles = store.getProfiles(); renderProfiles(c); }); page.addView(add); renderProfiles(c); return page; }

    private void renderProfiles(Context c) { if (profileList == null) return; profileList.removeAllViews(); profiles = store.getProfiles(); if (profiles.isEmpty()) profileList.addView(label(c, "No API profiles yet.", 14, false)); for (AgentWorkspaceStore.Profile p : profiles) addProfileRow(c, p); }

    private void addProfileRow(Context c, AgentWorkspaceStore.Profile p) { LinearLayout block = column(c); block.setPadding(dp(12), dp(10), dp(12), dp(10)); block.setBackground(round(SURFACE, 16)); LinearLayout head = row(c); TextView name = label(c, p.name + (p.models.isEmpty() ? "  ·  not checked" : "  ·  " + p.models.size() + " models"), 15, true); name.setTextColor(TEXT); head.addView(name, weight(1)); TextView arrow = label(c, "⌄", 18, false); head.addView(arrow); block.addView(head); LinearLayout details = column(c); details.setVisibility(View.GONE); head.setOnClickListener(v -> { details.setVisibility(details.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE); arrow.setText(details.getVisibility() == View.VISIBLE ? "⌃" : "⌄"); }); EditText nameInput = field(c, p.name, "Profile name"); EditText key = field(c, "", p.key.isEmpty() ? "Paste Gemini API key" : "Saved key, paste only to replace"); key.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD); key.setTransformationMethod(PasswordTransformationMethod.getInstance()); details.addView(nameInput); details.addView(key); TextView info = label(c, p.lastError.isEmpty() ? "Only real models returned by Gemini will be used. Model selection stays automatic." : p.lastError, 11, false); info.setTextColor(p.lastError.isEmpty() ? MUTED : Color.rgb(235, 130, 120)); details.addView(info); LinearLayout actions = row(c); MaterialButton check = smallButton("Check"); MaterialButton save = smallButton("Save"); actions.addView(check, weight(1)); actions.addView(save, weight(1)); details.addView(actions); check.setOnClickListener(v -> validateProfile(p, nameInput, key, info, c)); save.setOnClickListener(v -> { p.name = nameInput.getText().toString().trim(); if (!key.getText().toString().trim().isEmpty()) p.key = key.getText().toString().trim(); store.saveProfiles(profiles); Toast.makeText(c, "Profile saved", Toast.LENGTH_SHORT).show(); renderProfiles(c); }); block.addView(details); profileList.addView(block, new LinearLayout.LayoutParams(-1, -2) {{ setMargins(0, dp(10), 0, 0); }}); }

    private void validateProfile(AgentWorkspaceStore.Profile p, EditText name, EditText key, TextView info, Context c) { String candidate = key.getText().toString().trim(); if (candidate.isEmpty()) candidate = p.key; if (candidate.isEmpty()) { info.setText("Paste a key first."); return; } p.name = name.getText().toString().trim(); p.key = candidate; info.setText("Checking key and discovering live models..."); client.listModels(candidate, new GeminiAutoClient.Listener() { @Override public void onModels(List<GeminiAutoClient.ModelInfo> models, String remaining, String retry) { p.models.clear(); for (GeminiAutoClient.ModelInfo m : models) p.models.add(m.id()); p.lastError = models.isEmpty() ? "No text-capable model is available for this key." : "Validated · auto mode ready"; p.rate = remaining; store.saveProfiles(profiles); main.post(() -> { info.setText(p.lastError + "  " + p.models.size() + " models"); renderProfiles(c); }); } @Override public void onDelta(String text) { } @Override public void onComplete(String model, GeminiAutoClient.Usage usage, String remaining, String retry) { } @Override public void onError(int status, String message, String remaining, String retry) { p.lastError = message; store.saveProfiles(profiles); main.post(() -> info.setText(message)); } }); }

    private LinearLayout column(Context c) { LinearLayout l = new LinearLayout(c); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private LinearLayout row(Context c) { LinearLayout l = new LinearLayout(c); l.setOrientation(LinearLayout.HORIZONTAL); return l; }
    private LinearLayout.LayoutParams weight(float w) { return new LinearLayout.LayoutParams(0, -2, w); }
    private TextView label(Context c, String text, int size, boolean bold) { TextView v = new TextView(c); v.setText(text); v.setTextSize(size); v.setTextColor(TEXT); v.setTypeface(null, bold ? Typeface.BOLD : Typeface.NORMAL); return v; }
    private EditText field(Context c, String value, String hint) { EditText e = new EditText(c); e.setText(value); e.setHint(hint); e.setHintTextColor(Color.rgb(111, 127, 118)); e.setTextColor(TEXT); e.setTextSize(14); e.setSingleLine(true); e.setPadding(dp(12), dp(9), dp(12), dp(9)); e.setBackground(round(SURFACE_2, 12)); LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.setMargins(0, dp(8), 0, 0); e.setLayoutParams(p); return e; }
    private MaterialButton iconButton(Context c, String text, String description) { MaterialButton b = new MaterialButton(c); b.setText(text); b.setContentDescription(description); b.setTextSize(18); b.setTextColor(TEXT); b.setMinHeight(dp(44)); b.setMinWidth(dp(44)); b.setPadding(0, 0, 0, 0); b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.TRANSPARENT)); return b; }
    private MaterialButton smallButton(String text) { MaterialButton b = new MaterialButton(requireContext()); b.setText(text); b.setTextSize(12); b.setTextColor(TEXT); b.setMinHeight(dp(42)); b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(SURFACE_2)); return b; }
    private GradientDrawable round(int color, int radius) { GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d; }
    private void bubble(String text, boolean user) { TextView t = addAssistantBubble(text); t.setTextColor(TEXT); t.setBackground(round(user ? Color.rgb(31, 58, 47) : Color.TRANSPARENT, 16)); t.setPadding(dp(12), dp(8), dp(12), dp(8)); LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.gravity = user ? Gravity.END : Gravity.START; p.setMargins(user ? dp(42) : 0, dp(5), user ? 0 : dp(42), dp(5)); t.setLayoutParams(p); }
    private TextView addAssistantBubble(String text) { TextView t = label(requireContext(), text, 15, false); t.setTextIsSelectable(true); messages.addView(t); return t; }
    private void addAssistant(String text) { bubble(text, false); }
    private void showThinking(String text) { if (thinkingText != null) thinkingText.setText(text); thinkingPanel.setVisibility(View.VISIBLE); }
    private void status(String text, int color) { if (statusText != null) { statusText.setText(text); statusText.setTextColor(color); } }
    private void finishError(String text) { busy = false; thinkingPanel.setVisibility(View.GONE); status(text, Color.rgb(235, 130, 120)); addAssistant(text); }
    private String preview(String value) { return value.length() > 1600 ? value.substring(0, 1600) + "…" : value; }
    private void scrollBottom() { if (transcript != null) transcript.post(() -> transcript.fullScroll(View.FOCUS_DOWN)); }
    private void startHeartbeat() { stopHeartbeat(); heartbeat = () -> { profiles = store.getProfiles(); boolean on = profiles.stream().anyMatch(p -> p.enabled && !p.key.isEmpty()); status(on ? "Connected · auto" : "Offline · add an API profile", on ? MINT : MUTED); main.postDelayed(heartbeat, 30000); }; main.post(heartbeat); }
    private void stopHeartbeat() { if (heartbeat != null) main.removeCallbacks(heartbeat); heartbeat = null; }
    private int dp(int value) { return Math.round(value * requireContext().getResources().getDisplayMetrics().density); }

    private static final class Route { final AgentWorkspaceStore.Profile profile; final String model; Route(AgentWorkspaceStore.Profile profile, String model) { this.profile = profile; this.model = model; } }
}
