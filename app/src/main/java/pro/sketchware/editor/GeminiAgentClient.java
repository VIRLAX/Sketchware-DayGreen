package pro.sketchware.editor;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/** Small REST client for Gemini Developer API calls used by the Agent tab. */
public final class GeminiAgentClient {
    private static final String API_ROOT = "https://generativelanguage.googleapis.com/v1beta/";
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private final OkHttpClient http = new OkHttpClient.Builder()
            .callTimeout(java.time.Duration.ofSeconds(45))
            .build();

    public interface Listener {
        void onModels(List<ModelInfo> models, String remaining, String retryAfter);
        void onAnswer(String answer, String remaining, String retryAfter);
        void onError(String message, String remaining, String retryAfter);
    }

    public static final class ModelInfo {
        public final String name;
        public final String displayName;

        public ModelInfo(String name, String displayName) {
            this.name = name;
            this.displayName = displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    public void listModels(String apiKey, Listener listener) {
        Request request = new Request.Builder()
                .url(API_ROOT + "models?key=" + encode(apiKey))
                .get()
                .build();
        http.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) {
                listener.onError(messageFor(e), "", "");
            }

            @Override public void onResponse(Call call, Response response) {
                String remaining = response.header("x-ratelimit-remaining", "");
                String retryAfter = response.header("retry-after", "");
                try (Response ignored = response) {
                    String body = ignored.body() == null ? "" : ignored.body().string();
                    if (!ignored.isSuccessful()) {
                        listener.onError(errorFrom(body, ignored.code()), remaining, retryAfter);
                        return;
                    }
                    JSONArray raw = new JSONObject(body).optJSONArray("models");
                    List<ModelInfo> models = new ArrayList<>();
                    if (raw != null) {
                        for (int i = 0; i < raw.length(); i++) {
                            JSONObject item = raw.optJSONObject(i);
                            if (item == null) continue;
                            JSONArray methods = item.optJSONArray("supportedGenerationMethods");
                            boolean supportsText = false;
                            if (methods != null) {
                                for (int j = 0; j < methods.length(); j++) {
                                    if ("generateContent".equals(methods.optString(j))) {
                                        supportsText = true;
                                        break;
                                    }
                                }
                            }
                            String name = item.optString("name", "");
                            if (supportsText && !name.isEmpty()) {
                                models.add(new ModelInfo(name, item.optString("displayName", name)));
                            }
                        }
                    }
                    listener.onModels(models, remaining, retryAfter);
                } catch (Exception e) {
                    listener.onError(messageFor(e), remaining, retryAfter);
                }
            }
        });
    }

    public void generate(String apiKey, String model, String prompt, Listener listener) {
        String modelPath = model.startsWith("models/") ? model : "models/" + model;
        JSONObject payload = new JSONObject();
        try {
            JSONArray parts = new JSONArray();
            parts.put(new JSONObject().put("text", prompt));
            JSONArray contents = new JSONArray();
            contents.put(new JSONObject().put("role", "user").put("parts", parts));
            payload.put("contents", contents);
            payload.put("generationConfig", new JSONObject()
                    .put("temperature", 0.2)
                    .put("maxOutputTokens", 4096));
        } catch (Exception e) {
            listener.onError(messageFor(e), "", "");
            return;
        }

        RequestBody body = RequestBody.create(JSON, payload.toString());
        Request request = new Request.Builder()
                .url(API_ROOT + modelPath + ":generateContent?key=" + encode(apiKey))
                .post(body)
                .build();
        http.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) {
                listener.onError(messageFor(e), "", "");
            }

            @Override public void onResponse(Call call, Response response) {
                String remaining = response.header("x-ratelimit-remaining", "");
                String retryAfter = response.header("retry-after", "");
                try (Response ignored = response) {
                    String raw = ignored.body() == null ? "" : ignored.body().string();
                    if (!ignored.isSuccessful()) {
                        listener.onError(errorFrom(raw, ignored.code()), remaining, retryAfter);
                        return;
                    }
                    JSONObject root = new JSONObject(raw);
                    JSONArray candidates = root.optJSONArray("candidates");
                    String answer = "";
                    if (candidates != null && candidates.length() > 0) {
                        JSONObject content = candidates.optJSONObject(0);
                        if (content != null) {
                            JSONObject textContent = content.optJSONObject("content");
                            JSONArray parts = textContent == null ? null : textContent.optJSONArray("parts");
                            if (parts != null) {
                                StringBuilder output = new StringBuilder();
                                for (int i = 0; i < parts.length(); i++) {
                                    String text = parts.optJSONObject(i) == null ? "" : parts.optJSONObject(i).optString("text", "");
                                    if (!text.isEmpty()) output.append(text);
                                }
                                answer = output.toString();
                            }
                        }
                    }
                    if (answer.isEmpty()) answer = "Gemini returned no text for this request.";
                    listener.onAnswer(answer, remaining, retryAfter);
                } catch (Exception e) {
                    listener.onError(messageFor(e), remaining, retryAfter);
                }
            }
        });
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private static String errorFrom(String body, int status) {
        try {
            JSONObject root = new JSONObject(body);
            JSONObject error = root.optJSONObject("error");
            if (error != null) {
                String message = error.optString("message", "");
                if (!message.isEmpty()) return "HTTP " + status + ": " + message;
            }
        } catch (Exception ignored) {
        }
        return "Gemini request failed (HTTP " + status + ").";
    }

    private static String messageFor(Exception e) {
        String message = e.getMessage();
        return message == null || message.trim().isEmpty() ? "Network request failed." : message;
    }
}
