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
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import pro.sketchware.R;
import pro.sketchware.activities.editor.view.ViewCodeEditorActivity;

/** Gemini Agent workspace tab. It is intentionally preview-first: no project write happens without Accept. */
public final class AgentFragment extends Fragment {
    private static final int XML_EDITOR_REQUEST = 4401;
    private static final Pattern MUTATING_REQUEST = Pattern.compile(
            "(?i)\\b(create|make|build|generate|fix|repair|change|modify|update|buat|bikin|perbaiki|ubah|layout|xml|java|kotlin|c\\+\\+|block|code)\\b");

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final GeminiAgentClient client = new GeminiAgentClient();
    private AgentSettingsStore store;
    private LinearLayout messageColumn;
    private ScrollView messageScroll;
    private EditText promptInput;
    private LinearLayout settingsPanel;
    private EditText apiKeyInput;
    private Spinner modelSpinner;
    private TextView settingsStatus;
    private TextView heartbeatLabel;
    private TextView heartbeatDot;
    private TextView rateLabel;
    private String selectedModel = "gemini-2.5-flash";
    private String lastRemaining = "";
    private String lastRetryAfter = "";
    private boolean keyValidated;
    private boolean waitingForReply;
    private Runnable heartbeatRunnable;

    public static AgentFragment newInstance(String projectId) {
        AgentFragment fragment = new AgentFragment();
        Bundle args = new Bundle();
        args.putString("project_id", projectId == null ? "" : projectId);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull android.view.LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        store = new AgentSettingsStore(requireContext());
        selectedModel = store.getModel();
        return buildView(requireContext());
    }

    @Override
    public void onResume() {
        super.onResume();
        startHeartbeat();
    }

    @Override
    public void onPause() {
        stopHeartbeat();
        super.onPause();
    }

    @Override
    public void onDestroyView() {
        stopHeartbeat();
        super.onDestroyView();
    }

    private View buildView(Context context) {
        LinearLayout root = column(context);
        root.setPadding(dp(16), dp(12), dp(16), dp(12));

        LinearLayout header = row(context);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView brand = text(context, "✦  Gemini Agent", 20, true);
        header.addView(brand, weightParams(1));

        MaterialButton settingsButton = button(context, "Settings");
        settingsButton.setIconResource(android.R.drawable.ic_menu_manage);
        settingsButton.setOnClickListener(v -> toggleSettings());
        header.addView(settingsButton);
        MaterialButton menuButton = button(context, "");
        menuButton.setIconResource(android.R.drawable.ic_menu_more);
        menuButton.setContentDescription("Agent menu");
        menuButton.setOnClickListener(this::showAgentMenu);
        header.addView(menuButton, new LinearLayout.LayoutParams(dp(48), dp(48)));
        root.addView(header);

        LinearLayout connection = row(context);
        connection.setGravity(Gravity.CENTER_VERTICAL);
        heartbeatDot = text(context, "●", 16, false);
        heartbeatDot.setTextColor(Color.rgb(130, 130, 130));
        connection.addView(heartbeatDot, new LinearLayout.LayoutParams(dp(24), dp(48)));
        heartbeatLabel = text(context, "Offline, add a Gemini key to connect", 13, false);
        connection.addView(heartbeatLabel, weightParams(1));
        rateLabel = text(context, "", 12, false);
        rateLabel.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        connection.addView(rateLabel);
        root.addView(connection);

        settingsPanel = buildSettingsPanel(context);
        settingsPanel.setVisibility(View.GONE);
        root.addView(settingsPanel);

        messageScroll = new ScrollView(context);
        messageScroll.setFillViewport(true);
        messageColumn = column(context);
        messageColumn.setPadding(0, dp(8), 0, dp(8));
        messageScroll.addView(messageColumn, new ViewGroup.LayoutParams(-1, -2));
        root.addView(messageScroll, new LinearLayout.LayoutParams(-1, 0, 1));

        if (store.getApiKey().isEmpty()) {
            addAssistant("I’m ready. Add your Gemini API key in Settings, validate it, then ask me to inspect or build something. I’ll show a plan before touching the project.");
        } else {
            addAssistant("Welcome back. I’ll keep every project change behind an explicit Accept step.");
        }

        LinearLayout composer = row(context);
        composer.setGravity(Gravity.BOTTOM);
        promptInput = new EditText(context);
        promptInput.setHint("Ask the Agent to inspect, explain, or prepare a change...");
        promptInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        promptInput.setMinLines(1);
        promptInput.setMaxLines(4);
        composer.addView(promptInput, weightParams(1));
        MaterialButton send = button(context, "Send");
        send.setOnClickListener(v -> sendPrompt());
        composer.addView(send);
        root.addView(composer);
        return root;
    }

    private LinearLayout buildSettingsPanel(Context context) {
        LinearLayout panel = column(context);
        panel.setPadding(dp(12), dp(8), dp(12), dp(12));
        panel.setBackgroundColor(Color.rgb(242, 241, 248));

        TextView title = text(context, "Gemini connection", 16, true);
        panel.addView(title);
        TextView help = text(context, "Stored once for this Sketchware install, shared across projects. The key is encrypted with Android Keystore.", 12, false);
        help.setPadding(0, dp(4), 0, dp(8));
        panel.addView(help);

        LinearLayout keyRow = row(context);
        apiKeyInput = new EditText(context);
        apiKeyInput.setHint("Paste Gemini API key");
        apiKeyInput.setSingleLine(true);
        apiKeyInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        apiKeyInput.setTransformationMethod(PasswordTransformationMethod.getInstance());
        keyRow.addView(apiKeyInput, weightParams(1));
        MaterialButton validate = button(context, "Check");
        validate.setIconResource(android.R.drawable.ic_popup_sync);
        validate.setOnClickListener(v -> validateKey());
        keyRow.addView(validate);
        panel.addView(keyRow);

        LinearLayout modelRow = row(context);
        TextView modelLabel = text(context, "Model", 13, false);
        modelRow.addView(modelLabel, new LinearLayout.LayoutParams(dp(64), dp(52)));
        modelSpinner = new Spinner(context);
        ArrayAdapter<String> initial = new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item,
                new String[]{selectedModel});
        modelSpinner.setAdapter(initial);
        modelSpinner.setEnabled(false);
        modelSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                if (parent.getItemAtPosition(position) != null) selectedModel = parent.getItemAtPosition(position).toString();
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });
        modelRow.addView(modelSpinner, weightParams(1));
        panel.addView(modelRow);

        LinearLayout saveRow = row(context);
        settingsStatus = text(context, "Not validated", 12, false);
        saveRow.addView(settingsStatus, weightParams(1));
        MaterialButton save = button(context, "Save");
        save.setOnClickListener(v -> saveKey());
        saveRow.addView(save);
        panel.addView(saveRow);
        return panel;
    }

    private void toggleSettings() {
        if (settingsPanel == null) return;
        settingsPanel.setVisibility(settingsPanel.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
        if (settingsPanel.getVisibility() == View.VISIBLE && apiKeyInput != null) apiKeyInput.requestFocus();
    }

    private void validateKey() {
        String key = apiKeyInput == null ? "" : apiKeyInput.getText().toString().trim();
        if (key.isEmpty()) {
            setSettingsState("Paste a key first", false);
            return;
        }
        setSettingsState("Checking key and discovering models...", false);
        setHeartbeat("Validating Gemini...", Color.rgb(190, 120, 20));
        client.listModels(key, new GeminiAgentClient.Listener() {
            @Override public void onModels(List<GeminiAgentClient.ModelInfo> models, String remaining, String retryAfter) {
                mainHandler.post(() -> {
                    updateRate(remaining, retryAfter);
                    if (models.isEmpty()) {
                        setSettingsState("Key works, but no text model is available", false);
                        setHeartbeat("Connected, no compatible model", Color.rgb(190, 120, 20));
                        keyValidated = false;
                        return;
                    }
                    keyValidated = true;
                    updateModels(models);
                    setSettingsState(models.size() + " Gemini model(s) available", true);
                    setHeartbeat("Connected to Gemini", Color.rgb(40, 150, 90));
                });
            }
            @Override public void onAnswer(String answer, String remaining, String retryAfter) { }
            @Override public void onError(String message, String remaining, String retryAfter) {
                mainHandler.post(() -> {
                    updateRate(remaining, retryAfter);
                    keyValidated = false;
                    setSettingsState(message, false);
                    setHeartbeat("Gemini unavailable", Color.rgb(190, 55, 55));
                });
            }
        });
    }

    private void saveKey() {
        String key = apiKeyInput == null ? "" : apiKeyInput.getText().toString().trim();
        if (key.isEmpty() || !keyValidated) {
            setSettingsState("Validate a working key before saving", false);
            return;
        }
        store.saveApiKey(key);
        store.saveModel(selectedModel);
        apiKeyInput.setText("");
        setSettingsState("Saved for all projects on this device", true);
        setHeartbeat("Connected to Gemini", Color.rgb(40, 150, 90));
        Toast.makeText(requireContext(), "Gemini Agent settings saved", Toast.LENGTH_SHORT).show();
    }

    private void updateModels(List<GeminiAgentClient.ModelInfo> models) {
        List<String> names = new ArrayList<>();
        for (GeminiAgentClient.ModelInfo model : models) names.add(model.name.replace("models/", ""));
        if (modelSpinner == null) return;
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_dropdown_item, names);
        modelSpinner.setAdapter(adapter);
        modelSpinner.setEnabled(true);
        int selected = Math.max(0, names.indexOf(selectedModel.replace("models/", "")));
        modelSpinner.setSelection(selected);
        selectedModel = names.get(selected);
    }

    private void sendPrompt() {
        if (waitingForReply || promptInput == null) return;
        String prompt = promptInput.getText().toString().trim();
        if (prompt.isEmpty()) return;
        String key = store.getApiKey();
        if (key.isEmpty()) {
            addAssistant("I need a Gemini API key before I can run. Open Settings, paste it, tap Check, then Save.");
            settingsPanel.setVisibility(View.VISIBLE);
            return;
        }
        promptInput.setText("");
        store.appendHistory(prompt);
        addUser(prompt);
        waitingForReply = true;
        setHeartbeat("Agent is working...", Color.rgb(190, 120, 20));
        LinearProgressIndicator progress = addProgress();
        String projectId = getArguments() == null ? "" : getArguments().getString("project_id", "");
        String request = "You are the Sketchware-DayGreen project agent. Project id: " + projectId + ".\\n"
                + "Work in preview-first mode. Do not claim files were changed. Analyze the request, identify root causes when relevant, and propose exact files or model changes. "
                + "If code is needed, put it in a fenced code block with the correct language. If the request would modify the project, finish with a short ACTION PLAN listing each intended change.\\n\\nUser request:\\n" + prompt;
        client.generate(key, selectedModel, request, new GeminiAgentClient.Listener() {
            @Override public void onModels(List<GeminiAgentClient.ModelInfo> models, String remaining, String retryAfter) { }
            @Override public void onAnswer(String answer, String remaining, String retryAfter) {
                mainHandler.post(() -> {
                    progress.setVisibility(View.GONE);
                    waitingForReply = false;
                    updateRate(remaining, retryAfter);
                    setHeartbeat("Connected to Gemini", Color.rgb(40, 150, 90));
                    addAssistant(answer);
                    if (MUTATING_REQUEST.matcher(prompt).find()) addActionPreview(prompt, answer);
                });
            }
            @Override public void onError(String message, String remaining, String retryAfter) {
                mainHandler.post(() -> {
                    progress.setVisibility(View.GONE);
                    waitingForReply = false;
                    updateRate(remaining, retryAfter);
                    setHeartbeat("Gemini request failed", Color.rgb(190, 55, 55));
                    addAssistant("I couldn't complete that request: " + message);
                });
            }
        });
    }

    private LinearProgressIndicator addProgress() {
        LinearProgressIndicator progress = new LinearProgressIndicator(requireContext());
        progress.setIndeterminate(true);
        MaterialCardView card = card(requireContext());
        LinearLayout body = column(requireContext());
        body.setPadding(dp(12), dp(10), dp(12), dp(10));
        body.addView(text(requireContext(), "Agent process", 13, true));
        body.addView(text(requireContext(), "✓  Read prompt and project scope", 12, false));
        body.addView(text(requireContext(), "◌  Analyze request and prepare a safe response", 12, false));
        body.addView(text(requireContext(), "◌  Waiting for your approval before any write", 12, false));
        body.addView(progress);
        card.addView(body);
        messageColumn.addView(card);
        scrollToBottom();
        return progress;
    }

    private void addActionPreview(String prompt, String answer) {
        MaterialCardView card = card(requireContext());
        LinearLayout body = column(requireContext());
        body.setPadding(dp(12), dp(12), dp(12), dp(8));
        body.addView(text(requireContext(), "Proposed action, approval required", 15, true));
        body.addView(text(requireContext(), "Request: " + prompt, 12, false));
        body.addView(text(requireContext(), "Nothing has been written. Accept opens the generated XML in the existing Direct XML editor when an XML layout is present; Cancel discards this plan.", 12, false));
        LinearLayout actions = row(requireContext());
        MaterialButton cancel = button(requireContext(), "Cancel");
        MaterialButton accept = button(requireContext(), "Accept");
        cancel.setOnClickListener(v -> {
            card.setVisibility(View.GONE);
            addAssistant("Plan cancelled. No project data was changed.");
        });
        accept.setOnClickListener(v -> {
            card.setVisibility(View.GONE);
            String xml = extractCode(answer, "xml");
            if (!xml.isEmpty()) {
                Intent intent = new Intent(requireContext(), ViewCodeEditorActivity.class);
                intent.putExtra("title", "main.xml");
                intent.putExtra("content", xml);
                startActivityForResult(intent, XML_EDITOR_REQUEST);
                addAssistant("Accepted. I opened the XML in Direct XML so the existing Sketchware model remains the source of truth.");
            } else {
                addAssistant("Accepted as a review step. The response contains no XML handoff, so I left the project untouched instead of guessing a target file.");
            }
        });
        actions.addView(cancel, weightParams(1));
        actions.addView(accept, weightParams(1));
        body.addView(actions);
        card.addView(body);
        messageColumn.addView(card);
        scrollToBottom();
    }

    private String extractCode(String answer, String language) {
        String marker = "```" + language;
        int start = answer.toLowerCase(Locale.US).indexOf(marker.toLowerCase(Locale.US));
        if (start < 0) return "";
        start = answer.indexOf('\n', start);
        int end = answer.indexOf("```", start + 1);
        if (start < 0 || end < 0) return "";
        return answer.substring(start + 1, end).trim();
    }

    private void showAgentMenu(View anchor) {
        PopupMenu menu = new PopupMenu(requireContext(), anchor);
        menu.getMenu().add("Chat history").setOnMenuItemClickListener(item -> {
            showHistory();
            return true;
        });
        menu.getMenu().add("Live API rate limit").setOnMenuItemClickListener(item -> {
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Gemini API rate limit")
                    .setMessage(rateLabel == null || rateLabel.getText().length() == 0 ? "No rate-limit headers have been reported yet." : rateLabel.getText())
                    .setPositiveButton("Close", null)
                    .show();
            return true;
        });
        menu.getMenu().add("Open settings").setOnMenuItemClickListener(item -> {
            settingsPanel.setVisibility(View.VISIBLE);
            return true;
        });
        menu.show();
    }

    private void showHistory() {
        List<String> history = store.getHistory();
        if (history.isEmpty()) {
            new MaterialAlertDialogBuilder(requireContext()).setTitle("Chat history").setMessage("No prompts yet.").setPositiveButton("Close", null).show();
            return;
        }
        String[] entries = history.toArray(new String[0]);
        new MaterialAlertDialogBuilder(requireContext()).setTitle("Chat history").setItems(entries, null).setPositiveButton("Close", null).show();
    }

    private void startHeartbeat() {
        stopHeartbeat();
        heartbeatRunnable = () -> {
            String key = store == null ? "" : store.getApiKey();
            if (key.isEmpty()) {
                setHeartbeat("Offline, add a Gemini key to connect", Color.rgb(130, 130, 130));
            } else if (!waitingForReply) {
                client.listModels(key, new GeminiAgentClient.Listener() {
                    @Override public void onModels(List<GeminiAgentClient.ModelInfo> models, String remaining, String retryAfter) {
                        mainHandler.post(() -> {
                            updateRate(remaining, retryAfter);
                            setHeartbeat(models.isEmpty() ? "Connected, no compatible model" : "Connected to Gemini", models.isEmpty() ? Color.rgb(190, 120, 20) : Color.rgb(40, 150, 90));
                        });
                    }
                    @Override public void onAnswer(String answer, String remaining, String retryAfter) { }
                    @Override public void onError(String message, String remaining, String retryAfter) {
                        mainHandler.post(() -> {
                            updateRate(remaining, retryAfter);
                            setHeartbeat("Gemini unavailable", Color.rgb(190, 55, 55));
                        });
                    }
                });
            }
            mainHandler.postDelayed(heartbeatRunnable, 30000L);
        };
        mainHandler.post(heartbeatRunnable);
    }

    private void stopHeartbeat() {
        if (heartbeatRunnable != null) mainHandler.removeCallbacks(heartbeatRunnable);
        heartbeatRunnable = null;
    }

    private void setHeartbeat(String label, int color) {
        if (heartbeatLabel != null) heartbeatLabel.setText(label);
        if (heartbeatDot != null) heartbeatDot.setTextColor(color);
    }

    private void setSettingsState(String message, boolean success) {
        if (settingsStatus != null) {
            settingsStatus.setText(message);
            settingsStatus.setTextColor(success ? Color.rgb(40, 140, 80) : Color.rgb(150, 70, 60));
        }
    }

    private void updateRate(String remaining, String retryAfter) {
        lastRemaining = remaining == null ? "" : remaining;
        lastRetryAfter = retryAfter == null ? "" : retryAfter;
        if (rateLabel == null) return;
        if (!lastRemaining.isEmpty()) rateLabel.setText("Remaining " + lastRemaining);
        else if (!lastRetryAfter.isEmpty()) rateLabel.setText("Retry in " + lastRetryAfter + "s");
        else rateLabel.setText("");
    }

    private void addUser(String content) { addBubble(content, true); }
    private void addAssistant(String content) { addBubble(content, false); }

    private void addBubble(String content, boolean user) {
        MaterialCardView card = card(requireContext());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(user ? dp(40) : 0, dp(4), user ? 0 : dp(40), dp(4));
        card.setLayoutParams(params);
        card.setCardBackgroundColor(user ? Color.rgb(225, 232, 250) : Color.rgb(244, 242, 247));
        TextView body = text(requireContext(), content, 14, false);
        body.setTextIsSelectable(true);
        body.setPadding(dp(12), dp(10), dp(12), dp(10));
        card.addView(body);
        messageColumn.addView(card);
        scrollToBottom();
    }

    private void scrollToBottom() {
        if (messageScroll != null) messageScroll.post(() -> messageScroll.fullScroll(View.FOCUS_DOWN));
    }

    private static MaterialCardView card(Context context) {
        MaterialCardView view = new MaterialCardView(context);
        view.setRadius(18f);
        view.setStrokeWidth(1);
        view.setStrokeColor(Color.rgb(220, 218, 228));
        view.setCardElevation(0f);
        return view;
    }

    private static MaterialButton button(Context context, String label) {
        MaterialButton button = new MaterialButton(context);
        button.setText(label);
        button.setMinHeight(dp(context, 44));
        return button;
    }

    private static TextView text(Context context, String value, int sp, boolean bold) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(Color.rgb(35, 34, 42));
        view.setTypeface(null, bold ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        view.setTextIsSelectable(true);
        return view;
    }

    private static LinearLayout row(Context context) {
        LinearLayout view = new LinearLayout(context);
        view.setOrientation(LinearLayout.HORIZONTAL);
        view.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        return view;
    }

    private static LinearLayout column(Context context) {
        LinearLayout view = new LinearLayout(context);
        view.setOrientation(LinearLayout.VERTICAL);
        view.setLayoutParams(new LinearLayout.LayoutParams(-1, -1));
        return view;
    }

    private static LinearLayout.LayoutParams weightParams(float weight) {
        return new LinearLayout.LayoutParams(0, -2, weight);
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private int dp(int value) { return dp(requireContext(), value); }
}
