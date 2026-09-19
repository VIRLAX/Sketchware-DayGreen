package pro.sketchware.editor;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.method.PasswordTransformationMethod;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import pro.sketchware.activities.editor.view.ViewCodeEditorActivity;

/** Project-aware Gemini surface with explicit preview and approval before source writes. */
public final class AgentFragmentV2 extends Fragment {
    private static final Pattern MUTATING = Pattern.compile("(?i)\\b(create|make|build|generate|fix|repair|change|modify|update|buat|bikin|perbaiki|ubah|layout|xml|java|kotlin|c\\+\\+|block|code)\\b");
    private final Handler main = new Handler(Looper.getMainLooper());
    private final GeminiAgentClient client = new GeminiAgentClient();
    private AgentSettingsStore store;
    private LinearLayout messages;
    private ScrollView transcript;
    private LinearLayout settings;
    private EditText keyInput;
    private Spinner model;
    private TextView status;
    private TextView rate;
    private TextView dot;
    private TextView connection;
    private EditText composer;
    private String modelName;
    private String projectId;
    private String selectedFile;
    private boolean validated;
    private boolean busy;
    private Runnable heartbeat;

    public static AgentFragmentV2 newInstance(String projectId, String selectedFile) {
        AgentFragmentV2 fragment = new AgentFragmentV2();
        Bundle args = new Bundle();
        args.putString("project_id", projectId == null ? "" : projectId);
        args.putString("selected_file", selectedFile == null ? "" : selectedFile);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable @Override public View onCreateView(@NonNull android.view.LayoutInflater inflater, @Nullable ViewGroup parent, @Nullable Bundle state) {
        projectId = getArguments() == null ? "" : getArguments().getString("project_id", "");
        selectedFile = getArguments() == null ? "" : getArguments().getString("selected_file", "");
        store = new AgentSettingsStore(requireContext());
        modelName = store.getModel();
        return build(requireContext());
    }

    @Override public void onResume() { super.onResume(); startHeartbeat(); }
    @Override public void onPause() { stopHeartbeat(); super.onPause(); }
    @Override public void onDestroyView() { stopHeartbeat(); super.onDestroyView(); }

    private View build(Context c) {
        LinearLayout root = column(c);
        root.setPadding(dp(16), dp(12), dp(16), dp(12));
        LinearLayout head = row(c);
        TextView title = label(c, "✦  Gemini Agent", 20, true);
        head.addView(title, weight(1));
        MaterialButton settingsButton = button(c, "Settings");
        settingsButton.setIconResource(android.R.drawable.ic_menu_manage);
        settingsButton.setOnClickListener(v -> settings.setVisibility(settings.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE));
        head.addView(settingsButton);
        MaterialButton menu = button(c, "");
        menu.setIconResource(android.R.drawable.ic_menu_more);
        menu.setContentDescription("Agent menu");
        menu.setOnClickListener(this::showMenu);
        head.addView(menu, new LinearLayout.LayoutParams(dp(48), dp(48)));
        root.addView(head);

        LinearLayout heartbeatRow = row(c);
        dot = label(c, "●", 16, false);
        dot.setTextColor(Color.rgb(130, 130, 130));
        heartbeatRow.addView(dot, new LinearLayout.LayoutParams(dp(24), dp(48)));
        connection = label(c, "Offline, add a Gemini key", 13, false);
        heartbeatRow.addView(connection, weight(1));
        rate = label(c, "", 12, false);
        rate.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        heartbeatRow.addView(rate);
        root.addView(heartbeatRow);

        settings = buildSettings(c);
        settings.setVisibility(View.GONE);
        root.addView(settings);

        transcript = new ScrollView(c);
        transcript.setFillViewport(true);
        messages = column(c);
        messages.setPadding(0, dp(8), 0, dp(8));
        transcript.addView(messages, new ViewGroup.LayoutParams(-1, -2));
        root.addView(transcript, new LinearLayout.LayoutParams(-1, 0, 1));
        addAssistant(store.getApiKey().isEmpty()
                ? "Add your Gemini key in Settings, validate it, then ask me to inspect or change the current project. I will show the exact action before any write."
                : "Connected settings are saved for this device. Every source or layout write still waits for Accept.");

        LinearLayout bottom = row(c);
        composer = new EditText(c);
        composer.setHint("Ask the Agent to inspect, explain, or prepare a change...");
        composer.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        composer.setMinLines(1); composer.setMaxLines(4);
        bottom.addView(composer, weight(1));
        MaterialButton send = button(c, "Send");
        send.setOnClickListener(v -> send());
        bottom.addView(send);
        root.addView(bottom);
        return root;
    }

    private LinearLayout buildSettings(Context c) {
        LinearLayout box = column(c);
        box.setPadding(dp(12), dp(8), dp(12), dp(10));
        box.setBackgroundColor(Color.rgb(242, 241, 248));
        box.addView(label(c, "Gemini connection", 16, true));
        box.addView(label(c, "Saved once for all Sketchware projects on this device. The key is encrypted with Android Keystore.", 12, false));
        LinearLayout keyRow = row(c);
        keyInput = new EditText(c);
        keyInput.setHint("Paste Gemini API key");
        keyInput.setSingleLine(true);
        keyInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        keyInput.setTransformationMethod(PasswordTransformationMethod.getInstance());
        keyRow.addView(keyInput, weight(1));
        MaterialButton check = button(c, "Check");
        check.setOnClickListener(v -> validateKey());
        keyRow.addView(check);
        box.addView(keyRow);
        LinearLayout modelRow = row(c);
        modelRow.addView(label(c, "Model", 13, false), new LinearLayout.LayoutParams(dp(64), dp(52)));
        model = new Spinner(c);
        model.setAdapter(new ArrayAdapter<>(c, android.R.layout.simple_spinner_dropdown_item, new String[]{modelName}));
        model.setEnabled(false);
        model.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> p, View v, int pos, long id) { if (p.getItemAtPosition(pos) != null) modelName = String.valueOf(p.getItemAtPosition(pos)); }
            @Override public void onNothingSelected(android.widget.AdapterView<?> p) { }
        });
        modelRow.addView(model, weight(1));
        box.addView(modelRow);
        LinearLayout saveRow = row(c);
        status = label(c, "Not validated", 12, false);
        saveRow.addView(status, weight(1));
        MaterialButton save = button(c, "Save");
        save.setOnClickListener(v -> saveKey());
        saveRow.addView(save);
        box.addView(saveRow);
        return box;
    }

    private void validateKey() {
        String key = text(keyInput);
        if (key.isEmpty()) { state("Paste a key first", false); return; }
        state("Validating key and discovering models...", false); setConnection("Validating Gemini...", Color.rgb(190, 120, 20));
        client.listModels(key, new GeminiAgentClient.Listener() {
            @Override public void onModels(List<GeminiAgentClient.ModelInfo> list, String remaining, String retry) { main.post(() -> { updateRate(remaining, retry); if (list.isEmpty()) { validated = false; state("No compatible text model found", false); return; } validated = true; updateModels(list); state(list.size() + " compatible model(s) available", true); setConnection("Connected to Gemini", Color.rgb(40, 150, 90)); }); }
            @Override public void onAnswer(String answer, String remaining, String retry) { }
            @Override public void onError(String message, String remaining, String retry) { main.post(() -> { validated = false; updateRate(remaining, retry); state(message, false); setConnection("Gemini unavailable", Color.rgb(190, 55, 55)); }); }
        });
    }

    private void saveKey() {
        String key = text(keyInput);
        if (key.isEmpty() || !validated) { state("Validate a working key before saving", false); return; }
        store.saveApiKey(key); store.saveModel(modelName); keyInput.setText("");
        state("Saved for all projects on this device", true); setConnection("Connected to Gemini", Color.rgb(40, 150, 90));
        Toast.makeText(requireContext(), "Gemini Agent saved", Toast.LENGTH_SHORT).show();
    }

    private void updateModels(List<GeminiAgentClient.ModelInfo> list) {
        List<String> names = new ArrayList<>();
        for (GeminiAgentClient.ModelInfo item : list) names.add(item.name.replace("models/", ""));
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_dropdown_item, names);
        model.setAdapter(adapter); model.setEnabled(true);
        int selected = Math.max(0, names.indexOf(modelName.replace("models/", "")));
        model.setSelection(selected); modelName = names.get(selected);
    }

    private void send() {
        if (busy) return;
        String prompt = text(composer); if (prompt.isEmpty()) return;
        String key = store.getApiKey();
        if (key.isEmpty()) { addAssistant("I need a Gemini API key first. Open Settings, tap Check, then Save."); settings.setVisibility(View.VISIBLE); return; }
        composer.setText(""); store.appendHistory(prompt); addUser(prompt); busy = true;
        setConnection("Agent is working...", Color.rgb(190, 120, 20)); LinearProgressIndicator progress = progressCard();
        String context = AgentProjectContext.build(requireContext(), projectId, selectedFile);
        String request = "You are the embedded Sketchware-DayGreen project agent. Work only from the supplied project context.\n"
                + "Be precise and root-cause focused. Do not claim a change was applied. For explanation-only requests, answer normally. For any requested mutation, finish with exactly one machine-readable proposal: a line TARGET: relative/path/to/file, then one fenced code block containing the complete replacement content. Use XML for layouts, java for Java, kotlin for Kotlin, cpp for C/C++. For blocks, explain the exact block operations and do not invent a file format.\n\nPROJECT CONTEXT:\n" + context + "\n\nUSER REQUEST:\n" + prompt;
        client.generate(key, modelName, request, new GeminiAgentClient.Listener() {
            @Override public void onModels(List<GeminiAgentClient.ModelInfo> models, String remaining, String retryAfter) { }
            @Override public void onAnswer(String answer, String remaining, String retryAfter) { main.post(() -> { progress.setVisibility(View.GONE); busy = false; updateRate(remaining, retryAfter); setConnection("Connected to Gemini", Color.rgb(40, 150, 90)); addAssistant(answer); if (MUTATING.matcher(prompt).find()) proposal(answer); }); }
            @Override public void onError(String message, String remaining, String retryAfter) { main.post(() -> { progress.setVisibility(View.GONE); busy = false; updateRate(remaining, retryAfter); setConnection("Gemini request failed", Color.rgb(190, 55, 55)); addAssistant("Request failed: " + message); }); }
        });
    }

    private LinearProgressIndicator progressCard() {
        LinearProgressIndicator bar = new LinearProgressIndicator(requireContext()); bar.setIndeterminate(true);
        MaterialCardView card = card(); LinearLayout body = column(requireContext()); body.setPadding(dp(12), dp(10), dp(12), dp(10));
        body.addView(label(requireContext(), "Agent process", 13, true));
        body.addView(label(requireContext(), "✓ Read project files and compile log", 12, false));
        body.addView(label(requireContext(), "◌ Analyze root cause and prepare proposal", 12, false));
        body.addView(label(requireContext(), "◌ Wait for Accept before writing", 12, false)); body.addView(bar); card.addView(body); messages.addView(card); bottom(); return bar;
    }

    private void proposal(String answer) {
        AgentActionExecutor.Action action = AgentActionExecutor.parse(answer);
        MaterialCardView card = card(); LinearLayout body = column(requireContext()); body.setPadding(dp(12), dp(12), dp(12), dp(8));
        body.addView(label(requireContext(), "Proposed change, approval required", 15, true));
        if (action == null) body.addView(label(requireContext(), "No safe machine-readable target was found. Nothing can be applied automatically.", 12, false));
        else body.addView(label(requireContext(), "Target: " + action.target + "\nLanguage: " + action.language + "\n\nPreview:\n" + preview(action.content), 12, false));
        LinearLayout actions = row(requireContext()); MaterialButton cancel = button(requireContext(), "Cancel"); MaterialButton accept = button(requireContext(), "Accept");
        cancel.setOnClickListener(v -> { card.setVisibility(View.GONE); addAssistant("Cancelled. No project data was changed."); });
        accept.setOnClickListener(v -> { card.setVisibility(View.GONE); accept(action); });
        actions.addView(cancel, weight(1)); actions.addView(accept, weight(1)); body.addView(actions); card.addView(body); messages.addView(card); bottom();
    }

    private void accept(AgentActionExecutor.Action action) {
        if (action == null) { addAssistant("I did not apply anything because the proposal was missing a safe target and code block."); return; }
        if (action.isXml()) {
            Intent intent = new Intent(requireContext(), ViewCodeEditorActivity.class);
            intent.putExtra("sc_id", projectId); intent.putExtra("title", action.target); intent.putExtra("content", action.content);
            startActivity(intent); addAssistant("Accepted. XML is open in Direct XML. Its existing auto-sync keeps the Visual Editor model as source of truth."); return;
        }
        String result = AgentActionExecutor.apply(requireContext(), projectId, action);
        if (result.startsWith("APPLIED:")) addAssistant("Accepted and written: " + result.substring("APPLIED:".length()) + "\nRun the project build to verify it.");
        else addAssistant("I did not write the project: " + result);
    }

    private String preview(String value) { return value.length() > 1600 ? value.substring(0, 1600) + "\n…" : value; }

    private void showMenu(View anchor) {
        PopupMenu menu = new PopupMenu(requireContext(), anchor);
        menu.getMenu().add("Chat history").setOnMenuItemClickListener(i -> { history(); return true; });
        menu.getMenu().add("Live API rate limit").setOnMenuItemClickListener(i -> { new MaterialAlertDialogBuilder(requireContext()).setTitle("Gemini API rate limit").setMessage(rate.getText().length() == 0 ? "No rate-limit headers reported by Gemini yet." : rate.getText()).setPositiveButton("Close", null).show(); return true; });
        menu.getMenu().add("Open settings").setOnMenuItemClickListener(i -> { settings.setVisibility(View.VISIBLE); return true; }); menu.show();
    }

    private void history() {
        List<String> items = store.getHistory();
        if (items.isEmpty()) { new MaterialAlertDialogBuilder(requireContext()).setTitle("Chat history").setMessage("No prompts yet.").setPositiveButton("Close", null).show(); return; }
        new MaterialAlertDialogBuilder(requireContext()).setTitle("Chat history").setItems(items.toArray(new String[0]), null).setPositiveButton("Close", null).show();
    }

    private void startHeartbeat() {
        stopHeartbeat(); heartbeat = () -> { String key = store == null ? "" : store.getApiKey(); if (key.isEmpty()) setConnection("Offline, add a Gemini key", Color.rgb(130, 130, 130)); else if (!busy) client.listModels(key, new GeminiAgentClient.Listener() {
            @Override public void onModels(List<GeminiAgentClient.ModelInfo> list, String remaining, String retry) { main.post(() -> { updateRate(remaining, retry); setConnection(list.isEmpty() ? "Connected, no compatible model" : "Connected to Gemini", list.isEmpty() ? Color.rgb(190, 120, 20) : Color.rgb(40, 150, 90)); }); }
            @Override public void onAnswer(String answer, String remaining, String retry) { }
            @Override public void onError(String message, String remaining, String retry) { main.post(() -> { updateRate(remaining, retry); setConnection("Gemini unavailable", Color.rgb(190, 55, 55)); }); }
        }); main.postDelayed(heartbeat, 30000L); }; main.post(heartbeat);
    }
    private void stopHeartbeat() { if (heartbeat != null) main.removeCallbacks(heartbeat); heartbeat = null; }
    private void setConnection(String value, int color) { if (connection != null) connection.setText(value); if (dot != null) dot.setTextColor(color); }
    private void state(String value, boolean ok) { if (status != null) { status.setText(value); status.setTextColor(ok ? Color.rgb(40, 140, 80) : Color.rgb(150, 70, 60)); } }
    private void updateRate(String remaining, String retry) { if (rate == null) return; if (remaining != null && !remaining.isEmpty()) rate.setText("Remaining " + remaining); else if (retry != null && !retry.isEmpty()) rate.setText("Retry in " + retry + "s"); else rate.setText(""); }
    private void addUser(String value) { bubble(value, true); }
    private void addAssistant(String value) { bubble(value, false); }
    private void bubble(String value, boolean user) { MaterialCardView item = card(); LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.setMargins(user ? dp(40) : 0, dp(4), user ? 0 : dp(40), dp(4)); item.setLayoutParams(p); item.setCardBackgroundColor(user ? Color.rgb(225, 232, 250) : Color.rgb(244, 242, 247)); TextView t = label(requireContext(), value, 14, false); t.setTextIsSelectable(true); t.setPadding(dp(12), dp(10), dp(12), dp(10)); item.addView(t); messages.addView(item); bottom(); }
    private void bottom() { if (transcript != null) transcript.post(() -> transcript.fullScroll(View.FOCUS_DOWN)); }
    private static MaterialCardView card(Context c) { MaterialCardView v = new MaterialCardView(c); v.setRadius(18); v.setStrokeWidth(1); v.setStrokeColor(Color.rgb(220, 218, 228)); v.setCardElevation(0); return v; }
    private MaterialCardView card() { return card(requireContext()); }
    private static MaterialButton button(Context c, String s) { MaterialButton b = new MaterialButton(c); b.setText(s); b.setMinHeight(dp(c, 44)); return b; }
    private static TextView label(Context c, String s, int size, boolean bold) { TextView v = new TextView(c); v.setText(s); v.setTextSize(size); v.setTextColor(Color.rgb(35, 34, 42)); v.setTypeface(null, bold ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL); return v; }
    private static LinearLayout row(Context c) { LinearLayout v = new LinearLayout(c); v.setOrientation(LinearLayout.HORIZONTAL); return v; }
    private static LinearLayout column(Context c) { LinearLayout v = new LinearLayout(c); v.setOrientation(LinearLayout.VERTICAL); return v; }
    private static LinearLayout.LayoutParams weight(float w) { return new LinearLayout.LayoutParams(0, -2, w); }
    private static int dp(Context c, int x) { return Math.round(x * c.getResources().getDisplayMetrics().density); }
    private int dp(int x) { return dp(requireContext(), x); }
    private static String text(TextView v) { return v == null ? "" : v.getText().toString().trim(); }
}
