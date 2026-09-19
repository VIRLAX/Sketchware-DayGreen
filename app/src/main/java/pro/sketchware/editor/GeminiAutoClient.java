package pro.sketchware.editor;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.BufferedSource;

/** Gemini REST client with model discovery, SSE streaming, usage telemetry, and fallback signals. */
public final class GeminiAutoClient {
    private static final String ROOT = "https://generativelanguage.googleapis.com/v1beta/";
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private final OkHttpClient http = new OkHttpClient.Builder().callTimeout(java.time.Duration.ofSeconds(90)).build();

    public interface Listener {
        void onModels(List<ModelInfo> models, String remaining, String retryAfter);
        void onDelta(String text);
        void onComplete(String model, Usage usage, String remaining, String retryAfter);
        void onError(int status, String message, String remaining, String retryAfter);
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
            if (lower.contains("tts") || lower.contains("image") || lower.contains("vision-preview") || lower.contains("embedding") || lower.contains("aqa")) {
                return false;
            }
            return true;
        }

        public String id() {
            return name.replace("models/", "");
        }
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
            return total <= 0 ? "" : total + " tokens";
        }
    }

    public void listModels(String apiKey, Listener listener) {
        Request r = new Request.Builder().url(ROOT + "models?key=" + enc(apiKey)).get().build();
        http.newCall(r).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) {
                listener.onError(0, message(e), "", "");
            }

            @Override public void onResponse(Call call, Response response) {
                String remaining = response.header("x-ratelimit-remaining", "");
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
                    listener.onModels(out, remaining, retry);
                } catch (Exception e) {
                    listener.onError(0, message(e), remaining, retry);
                }
            }
        });
    }

    public void stream(String apiKey, String model, String prompt, Listener listener) {
        JSONObject payload = new JSONObject();
        int outputTokens = prompt != null && prompt.length() > 6000 ? 4096 : 2048;
        try {
            payload.put("contents", new JSONArray().put(new JSONObject().put("role", "user").put("parts", new JSONArray().put(new JSONObject().put("text", prompt)))));
            payload.put("generationConfig", new JSONObject().put("temperature", 0.4).put("maxOutputTokens", outputTokens));
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
                String remaining = response.header("x-ratelimit-remaining", "");
                String retry = response.header("retry-after", "");
                if (!response.isSuccessful()) {
                    try (Response ignored = response) {
                        String body = ignored.body() == null ? "" : ignored.body().string();
                        listener.onError(ignored.code(), error(body, ignored.code()), remaining, retry);
                    } catch (IOException e) {
                        listener.onError(500, error(e.getMessage(), 500), remaining, retry);
                    }
                    return;
                }

                Usage[] usage = {new Usage(0, 0, 0)};
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
                            JSONObject content = candidates.optJSONObject(0);
                            JSONObject c = content == null ? null : content.optJSONObject("content");
                            JSONArray parts = c == null ? null : c.optJSONArray("parts");
                            if (parts != null) {
                                for (int i = 0; i < parts.length(); i++) {
                                    String delta = parts.optJSONObject(i) == null ? "" : parts.optJSONObject(i).optString("text", "");
                                    if (!delta.isEmpty()) listener.onDelta(delta);
                                }
                            }
                        }
                        JSONObject u = root.optJSONObject("usageMetadata");
                        if (u != null) {
                            usage[0] = new Usage(u.optInt("promptTokenCount"), u.optInt("candidatesTokenCount"), u.optInt("totalTokenCount"));
                        }
                    }
                    listener.onComplete(model, usage[0], remaining, retry);
                } catch (Exception e) {
                    listener.onError(0, message(e), remaining, retry);
                }
            }
        });
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

    private static String error(String body, int status) {
        try {
            JSONObject e = new JSONObject(body).optJSONObject("error");
            if (e != null && !e.optString("message").isEmpty()) return "HTTP " + status + ": " + e.optString("message");
        } catch (Exception ignored) { }
        return "Gemini request failed (HTTP " + status + ").";
    }
}
