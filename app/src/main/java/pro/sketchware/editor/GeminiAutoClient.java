package pro.sketchware.editor;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.BufferedSource;

/**
 * Gemini REST client v3.8 - Dayven AI Ultra Autonomous Engine.
 *
 * Implements:
 * - Real-time discovery and dynamic latest-version prioritization.
 * - Multi-tier model classification (PRO, MEDIUM, BASIC, AUTO).
 * - Multi-key intelligent routing with real-time rate-limit & failover monitoring.
 * - Multi-turn conversation with native system instructions.
 * - Dynamic fallback chain prioritizing active working models (including gemini-3.6-flash, 3.x, 2.x, 1.5).
 */
public final class GeminiAutoClient {
    private static final String ROOT = "https://generativelanguage.googleapis.com/v1beta/";
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    public static final int MAX_OUTPUT_TOKENS = 8192;

    // Resilient fallback tiers: dynamically prioritizing modern active versions
    public static final List<String> FALLBACK_TIERS = Arrays.asList(
            "gemini-3.6-flash",
            "gemini-2.0-flash",
            "gemini-1.5-flash",
            "gemini-1.5-flash-latest",
            "gemini-2.0-flash-exp",
            "gemini-1.5-pro",
            "gemini-1.5-pro-latest",
            "gemini-pro"
    );

    // Tier definitions
    public static final String TIER_PRO = "PRO";
    public static final String TIER_MEDIUM = "MEDIUM";
    public static final String TIER_BASIC = "BASIC";
    public static final String TIER_AUTO = "AUTO";

    private final OkHttpClient http = new OkHttpClient.Builder()
            .callTimeout(Duration.ofSeconds(180))
            .readTimeout(Duration.ofSeconds(180))
            .build();

    public interface Listener {
        void onModels(List<ModelInfo> models, String remaining, String retryAfter);
        void onDelta(String text);
        void onComplete(String model, Usage usage, String remaining, String retryAfter);
        void onError(int status, String message, String remaining, String retryAfter);
    }

    /** Optional extension: notified when the model stopped only because it hit the output cap. */
    public interface StreamListener extends Listener {
        void onTruncated(String model, Usage usage, String remaining, String retryAfter);
    }

    public interface ValidateModelsCallback {
        void onValidated(List<String> validActiveModels);
    }

    /** One conversation turn. Role is either "user" or "model". */
    public static final class Turn {
        public final String role;
        public final String text;

        public Turn(String role, String text) {
            this.role = "model".equals(role) || "assistant".equals(role) ? "model" : "user";
            this.text = text == null ? "" : text;
        }

        public static Turn user(String text) { return new Turn("user", text); }
        public static Turn model(String text) { return new Turn("model", text); }
    }

    public static final class ModelInfo {
        public final String name;
        public final String displayName;
        public final List<String> methods;

        public ModelInfo(String name, String displayName, List<String> methods) {
            this.name = name;
            this.displayName = displayName;
            this.methods = methods;
        }

        public boolean text() {
            if (!methods.contains("generateContent")) return false;
            String lower = name.toLowerCase(Locale.US);
            if (lower.contains("tts") || lower.contains("image") || lower.contains("vision-preview")
                    || lower.contains("embedding") || lower.contains("aqa")) {
                return false;
            }
            return true;
        }

        public String id() {
            return name.replace("models/", "");
        }

        public String classifyTier() {
            return classifyModelTier(id());
        }
    }

    /**
     * Modul 5: Dynamic Version Parser & Recency Scoring.
     * Extracts version numbers (e.g. 3.6, 2.0, 1.5) to ensure latest models are prioritized first.
     */
    public static double extractModelVersion(String modelId) {
        if (modelId == null) return 0.0;
        String m = modelId.toLowerCase(Locale.US);
        Pattern p = Pattern.compile("(\\d+(?:\\.\\d+)?)");
        Matcher matcher = p.matcher(m);
        double highest = 0.0;
        while (matcher.find()) {
            try {
                double val = Double.parseDouble(matcher.group(1));
                if (val > highest && val < 100.0) { // filter out 8b or token sizes
                    highest = val;
                }
            } catch (Exception ignored) { }
        }
        if (m.contains("latest")) highest += 0.05;
        if (m.contains("exp")) highest += 0.02;
        return highest;
    }

    /**
     * Modul 5: Klasifikasi Model Otomatis.
     * TIER PRO: "pro", "ultra", "exp", "thinking", "advanced"
     * TIER MEDIUM: "flash" (bukan flash-8b)
     * TIER BASIC: "nano", "lite", "mini", "flash-8b", "embedding"
     */
    public static String classifyModelTier(String modelId) {
        if (modelId == null) return TIER_MEDIUM;
        String m = modelId.toLowerCase(Locale.US);
        if (m.contains("nano") || m.contains("lite") || m.contains("mini") || m.contains("flash-8b")) {
            return TIER_BASIC;
        }
        if (m.contains("pro") || m.contains("ultra") || m.contains("exp") || m.contains("thinking") || m.contains("advanced")) {
            return TIER_PRO;
        }
        if (m.contains("flash")) {
            return TIER_MEDIUM;
        }
        return TIER_MEDIUM;
    }

    /**
     * Modul 5: Auto Routing Logic.
     */
    public static String computeMessageTier(String prompt) {
        if (prompt == null || prompt.trim().isEmpty()) return TIER_BASIC;
        int score = 0;
        String lower = prompt.toLowerCase(Locale.US);

        if (lower.matches("(?s).*\\b(algoritma|arsitektur|architecture|security|keamanan|optimize|optimasi|vulnerability|refactor|database|room|retrofit)\\b.*")) {
            score += 3;
        }
        if (prompt.length() > 200) {
            score += 3;
        }
        if (prompt.contains("```") || prompt.contains("{") || prompt.contains("class ") || prompt.contains("<LinearLayout")) {
            score += 2;
        }
        int questionMarks = countOccurrences(prompt, "?");
        if (questionMarks > 2) {
            score += 2;
        }
        if (lower.contains("kenapa") || lower.contains("bagaimana cara") || lower.contains("mengapa") || lower.contains("how to")) {
            score += 1;
        }
        if (lower.contains("project") || lower.contains("activity") || lower.contains("sketchware")) {
            score += 1;
        }

        if (score >= 7) return TIER_PRO;
        if (score >= 4) return TIER_MEDIUM;
        return TIER_BASIC;
    }

    private static int countOccurrences(String text, String sub) {
        int count = 0, idx = 0;
        while ((idx = text.indexOf(sub, idx)) != -1) {
            count++;
            idx += sub.length();
        }
        return count;
    }

    public static final class Usage {
        public final int input;
        public final int output;
        public final int total;

        public Usage(int input, int output, int total) {
            this.input = input;
            this.output = output;
            this.total = total;
        }

        public String label() {
            return total <= 0 ? "0 tokens" : total + " tokens";
        }
    }

    public void listModels(String apiKey, Listener listener) {
        Request r = new Request.Builder().url(ROOT + "models?key=" + enc(apiKey)).get().build();
        http.newCall(r).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) {
                listener.onError(0, message(e), "", "");
            }

            @Override public void onResponse(Call call, Response response) {
                String remaining = extractRate(response);
                String retry = response.header("retry-after", "");
                try (Response ignored = response) {
                    String body = ignored.body() == null ? "" : ignored.body().string();
                    if (!ignored.isSuccessful()) {
                        listener.onError(ignored.code(), error(body, ignored.code()), remaining, retry);
                        return;
                    }
                    JSONArray raw = new JSONObject(body).optJSONArray("models");
                    List<ModelInfo> out = new ArrayList<>();
                    if (raw != null) {
                        for (int i = 0; i < raw.length(); i++) {
                            JSONObject o = raw.optJSONObject(i);
                            if (o == null) continue;
                            JSONArray m = o.optJSONArray("supportedGenerationMethods");
                            List<String> methods = new ArrayList<>();
                            if (m != null) {
                                for (int j = 0; j < m.length(); j++) methods.add(m.optString(j));
                            }
                            String n = o.optString("name", "");
                            if (!n.isEmpty()) {
                                ModelInfo info = new ModelInfo(n, o.optString("displayName", n), methods);
                                if (info.text()) out.add(info);
                            }
                        }
                    }

                    // Dynamically sort models so the latest versions (highest version score) are at the top
                    Collections.sort(out, new Comparator<ModelInfo>() {
                        @Override public int compare(ModelInfo o1, ModelInfo o2) {
                            double v1 = extractModelVersion(o1.id());
                            double v2 = extractModelVersion(o2.id());
                            return Double.compare(v2, v1); // Descending (latest first)
                        }
                    });

                    listener.onModels(out, remaining, retry);
                } catch (Exception e) {
                    listener.onError(0, message(e), remaining, retry);
                }
            }
        });
    }

    /**
     * Modul 5: Dynamic Concurrent Validation with Ping.
     * Evaluates candidate models, prioritizes the latest active models, and weeds out deprecated ones.
     */
    public void validateActiveModels(String apiKey, List<String> candidateModels, ValidateModelsCallback callback) {
        if (candidateModels == null || candidateModels.isEmpty()) {
            callback.onValidated(new ArrayList<>());
            return;
        }

        List<String> verified = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger remaining = new AtomicInteger(candidateModels.size());

        for (String candidate : candidateModels) {
            final String m = candidate;
            JSONObject pingPayload = new JSONObject();
            try {
                pingPayload.put("contents", new JSONArray().put(
                        new JSONObject().put("role", "user")
                                .put("parts", new JSONArray().put(new JSONObject().put("text", "Hi")))));
                pingPayload.put("generationConfig", new JSONObject().put("maxOutputTokens", 5));
            } catch (Exception ignored) { }

            Request req = new Request.Builder()
                    .url(ROOT + modelPath(m) + ":generateContent?key=" + enc(apiKey))
                    .post(RequestBody.create(JSON, pingPayload.toString()))
                    .build();

            http.newCall(req).enqueue(new Callback() {
                @Override public void onFailure(Call call, IOException e) {
                    if (remaining.decrementAndGet() == 0) {
                        finishValidation(verified, callback);
                    }
                }

                @Override public void onResponse(Call call, Response response) {
                    try (Response res = response) {
                        if (res.isSuccessful()) {
                            String body = res.body() == null ? "" : res.body().string();
                            JSONObject json = new JSONObject(body);
                            JSONArray candidates = json.optJSONArray("candidates");
                            if (candidates != null && candidates.length() > 0) {
                                verified.add(m);
                            }
                        }
                    } catch (Exception ignored) {
                    } finally {
                        if (remaining.decrementAndGet() == 0) {
                            finishValidation(verified, callback);
                        }
                    }
                }
            });
        }
    }

    private void finishValidation(List<String> verified, ValidateModelsCallback callback) {
        List<String> sorted = new ArrayList<>(verified);
        // Sort verified models descending by version number so the newest model is selected first
        Collections.sort(sorted, new Comparator<String>() {
            @Override public int compare(String m1, String m2) {
                double v1 = extractModelVersion(m1);
                double v2 = extractModelVersion(m2);
                return Double.compare(v2, v1);
            }
        });
        callback.onValidated(sorted);
    }

    public void stream(String apiKey, String model, String prompt, Listener listener) {
        List<Turn> turns = new ArrayList<>();
        turns.add(Turn.user(prompt));
        streamConversation(apiKey, model, "", turns, MAX_OUTPUT_TOKENS, listener);
    }

    public void streamConversation(String apiKey, String model, String systemInstruction, List<Turn> turns, int maxTokens, Listener listener) {
        boolean useSystem = systemInstruction != null && !systemInstruction.trim().isEmpty();
        dispatch(apiKey, model, systemInstruction, turns, maxTokens, useSystem, listener);
    }

    private void dispatch(String apiKey, String model, String systemInstruction, List<Turn> turns,
                          int maxTokens, boolean nativeSystem, Listener listener) {
        List<Turn> conversation = turns == null ? new ArrayList<>() : new ArrayList<>(turns);
        if (conversation.isEmpty()) {
            listener.onError(0, "Tidak ada instruksi untuk dikirim.", "", "");
            return;
        }

        boolean hasSystem = systemInstruction != null && !systemInstruction.trim().isEmpty();
        JSONObject payload = new JSONObject();
        try {
            JSONArray contents = new JSONArray();
            for (int i = 0; i < conversation.size(); i++) {
                Turn t = conversation.get(i);
                String text = t.text;
                if (hasSystem && !nativeSystem && i == 0) {
                    text = systemInstruction.trim() + "\n\n---\n\n" + text;
                }
                contents.put(new JSONObject()
                        .put("role", t.role)
                        .put("parts", new JSONArray().put(new JSONObject().put("text", text))));
            }
            payload.put("contents", contents);
            if (hasSystem && nativeSystem) {
                payload.put("systemInstruction", new JSONObject()
                        .put("parts", new JSONArray().put(new JSONObject().put("text", systemInstruction.trim()))));
            }
            payload.put("generationConfig", new JSONObject()
                    .put("temperature", 0.35)
                    .put("topP", 0.95)
                    .put("maxOutputTokens", maxTokens <= 0 ? MAX_OUTPUT_TOKENS : maxTokens));
        } catch (Exception e) {
            listener.onError(0, message(e), "", "");
            return;
        }

        Request r = new Request.Builder()
                .url(ROOT + modelPath(model) + ":streamGenerateContent?alt=sse&key=" + enc(apiKey))
                .post(RequestBody.create(JSON, payload.toString()))
                .build();

        http.newCall(r).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) {
                listener.onError(0, message(e), "", "");
            }

            @Override public void onResponse(Call call, Response response) {
                String remaining = extractRate(response);
                String retry = response.header("retry-after", "");
                if (!response.isSuccessful()) {
                    int code = response.code();
                    String body = "";
                    try (Response ignored = response) {
                        body = ignored.body() == null ? "" : ignored.body().string();
                    } catch (IOException ignored) { }
                    if (code == 400 && hasSystem && nativeSystem) {
                        dispatch(apiKey, model, systemInstruction, conversation, maxTokens, false, listener);
                        return;
                    }
                    listener.onError(code, error(body, code), remaining, retry);
                    return;
                }

                Usage usage = new Usage(0, 0, 0);
                String finishReason = "";
                boolean hasEmitted = false;
                try (Response ignored = response) {
                    BufferedSource source = ignored.body().source();
                    while (!source.exhausted()) {
                        String line = source.readUtf8Line();
                        if (line == null || !line.startsWith("data:")) continue;
                        String json = line.substring(5).trim();
                        if (json.isEmpty()) continue;
                        JSONObject root = new JSONObject(json);
                        JSONArray candidates = root.optJSONArray("candidates");
                        if (candidates != null && candidates.length() > 0) {
                            JSONObject first = candidates.optJSONObject(0);
                            if (first != null) {
                                String reason = first.optString("finishReason", "");
                                if (!reason.isEmpty()) finishReason = reason;
                                JSONObject c = first.optJSONObject("content");
                                JSONArray parts = c == null ? null : c.optJSONArray("parts");
                                if (parts != null) {
                                    for (int i = 0; i < parts.length(); i++) {
                                        JSONObject part = parts.optJSONObject(i);
                                        String delta = part == null ? "" : part.optString("text", "");
                                        if (!delta.isEmpty()) {
                                            hasEmitted = true;
                                            listener.onDelta(delta);
                                        }
                                    }
                                }
                            }
                        }
                        JSONObject u = root.optJSONObject("usageMetadata");
                        if (u != null) {
                            usage = new Usage(u.optInt("promptTokenCount"), u.optInt("candidatesTokenCount"), u.optInt("totalTokenCount"));
                        }
                    }

                    if (!hasEmitted) {
                        listener.onError(204, "Tidak ada respon teks dari model " + model, remaining, retry);
                        return;
                    }
                    if ("MAX_TOKENS".equalsIgnoreCase(finishReason) && listener instanceof StreamListener) {
                        ((StreamListener) listener).onTruncated(model, usage, remaining, retry);
                    } else {
                        listener.onComplete(model, usage, remaining, retry);
                    }
                } catch (Exception e) {
                    if (hasEmitted) {
                        listener.onComplete(model, usage, remaining, retry);
                    } else {
                        listener.onError(0, message(e), remaining, retry);
                    }
                }
            }
        });
    }

    public static boolean retryable(int status) {
        return status == 0 || status == 204 || status == 400 || status == 403 || status == 404
                || status == 429 || status == 500 || status == 503 || status == 504;
    }

    public static boolean isHighDemandOrQuotaError(String errorMsg) {
        if (errorMsg == null) return false;
        String lower = errorMsg.toLowerCase(Locale.US);
        return lower.contains("high demand") || lower.contains("resource_exhausted")
                || lower.contains("quota") || lower.contains("429") || lower.contains("503")
                || lower.contains("rate limit") || lower.contains("overloaded")
                || lower.contains("no longer available") || lower.contains("404");
    }

    private static String extractRate(Response response) {
        String r = response.header("x-ratelimit-remaining", "");
        if (r == null || r.isEmpty()) r = response.header("x-ratelimit-remaining-requests", "");
        return (r == null || r.isEmpty()) ? "" : r + " left";
    }

    private static String modelPath(String model) {
        return model.startsWith("models/") ? model : "models/" + model;
    }

    private static String enc(String v) {
        return URLEncoder.encode(v == null ? "" : v, StandardCharsets.UTF_8);
    }

    private static String message(Exception e) {
        return e.getMessage() == null || e.getMessage().isEmpty() ? "Network request failed." : e.getMessage();
    }

    public static String error(String body, int status) {
        try {
            JSONObject root = new JSONObject(body);
            JSONObject e = root.optJSONObject("error");
            if (e != null) {
                String msg = e.optString("message", "");
                String stat = e.optString("status", "");
                if (!msg.isEmpty()) {
                    if (status == 429 || "RESOURCE_EXHAUSTED".equalsIgnoreCase(stat)) {
                        return "Quota/Rate Limit Google AI Studio tercapai: " + msg;
                    }
                    if (status == 400 || "INVALID_ARGUMENT".equalsIgnoreCase(stat)) {
                        return "Parameter atau model tidak valid (HTTP 400): " + msg;
                    }
                    if (status == 403 || "PERMISSION_DENIED".equalsIgnoreCase(stat)) {
                        return "Akses API ditolak (HTTP 403): " + msg;
                    }
                    if (status == 404 || "NOT_FOUND".equalsIgnoreCase(stat)) {
                        return "Model tidak ditemukan (HTTP 404): " + msg;
                    }
                    if (status == 503 || "UNAVAILABLE".equalsIgnoreCase(stat)) {
                        return "Server model sedang high demand / penuh (HTTP 503): " + msg;
                    }
                    return msg + " (HTTP " + status + ")";
                }
            }
        } catch (Exception ignored) { }
        return "Gemini API error (HTTP " + status + ").";
    }
}
