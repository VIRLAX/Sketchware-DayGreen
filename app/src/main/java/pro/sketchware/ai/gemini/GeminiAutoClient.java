package pro.sketchware.ai.gemini;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class GeminiAutoClient {

    private static final String API_BASE = "https://generativelanguage.googleapis.com/v1beta";
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    public interface Callback {
        void onSuccess(String responseText, String resolvedModel);
        void onError(String error);
    }

    public static void discoverAndGenerate(String apiKey, String userPrompt, Callback callback) {
        new Thread(() -> {
            try {
                List<String> sortedModels = fetchAvailableModels(apiKey);
                if (sortedModels.isEmpty()) {
                    sortedModels = getHardcodedFallbackTier();
                }

                executeWithFailover(apiKey, userPrompt, sortedModels, 0, callback);
            } catch (Exception e) {
                MAIN_HANDLER.post(() -> callback.onError("Dynamic discovery failed: " + e.getMessage()));
            }
        }).start();
    }

    private static void executeWithFailover(String apiKey, String userPrompt, List<String> models, int index, Callback callback) {
        if (index >= models.size()) {
            MAIN_HANDLER.post(() -> callback.onError("All viable Gemini models failed (rate limit, high demand 503, or quota exceeded)."));
            return;
        }

        String targetModel = models.get(index);
        new Thread(() -> {
            try {
                String result = callGenerateContent(apiKey, targetModel, userPrompt);
                MAIN_HANDLER.post(() -> callback.onSuccess(result, targetModel));
            } catch (Exception e) {
                // Failover on 503, 429, 404, or model busy
                executeWithFailover(apiKey, userPrompt, models, index + 1, callback);
            }
        }).start();
    }

    private static List<String> fetchAvailableModels(String apiKey) {
        List<String> viable = new ArrayList<>();
        try {
            URL url = new URL(API_BASE + "/models?key=" + apiKey);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);

            if (conn.getResponseCode() == 200) {
                String jsonStr = readStream(conn.getInputStream());
                JSONObject root = new JSONObject(jsonStr);
                JSONArray models = root.optJSONArray("models");
                if (models != null) {
                    for (int i = 0; i < models.length(); i++) {
                        JSONObject m = models.getJSONObject(i);
                        String name = m.optString("name", "");
                        JSONArray methods = m.optJSONArray("supportedGenerationMethods");
                        boolean supportsGen = false;
                        if (methods != null) {
                            for (int j = 0; j < methods.length(); j++) {
                                if ("generateContent".equals(methods.getString(j))) {
                                    supportsGen = true;
                                    break;
                                }
                            }
                        }
                        if (supportsGen && name.startsWith("models/gemini")) {
                            String modelId = name.replace("models/", "");
                            if (isModelPermitted(modelId)) {
                                viable.add(modelId);
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }

        viable.sort((a, b) -> Integer.compare(scoreModelRecency(b), scoreModelRecency(a)));
        return viable;
    }

    private static boolean isModelPermitted(String id) {
        String lower = id.toLowerCase();
        if (lower.contains("embedding") || lower.contains("aqa") || lower.contains("imagen") || lower.contains("thinking")) {
            return false;
        }
        return true;
    }

    private static int scoreModelRecency(String modelId) {
        String lower = modelId.toLowerCase();
        int score = 0;
        if (lower.contains("3.6") || lower.contains("3.5") || lower.contains("3.0")) score += 5000;
        else if (lower.contains("2.5") || lower.contains("2.0")) score += 3000;
        else if (lower.contains("1.5")) score += 1000;
        else if (lower.contains("1.0")) score += 500;

        if (lower.contains("flash")) score += 200;
        if (lower.contains("pro")) score += 100;
        if (lower.contains("latest")) score += 50;
        if (lower.contains("preview")) score += 20;
        if (lower.contains("002")) score += 10;
        if (lower.contains("001")) score += 5;

        return score;
    }

    private static List<String> getHardcodedFallbackTier() {
        List<String> tier = new ArrayList<>();
        tier.add("gemini-2.5-flash");
        tier.add("gemini-2.0-flash");
        tier.add("gemini-1.5-flash");
        tier.add("gemini-1.5-pro");
        tier.add("gemini-2.5-pro");
        return tier;
    }

    private static String callGenerateContent(String apiKey, String modelId, String userPrompt) throws Exception {
        URL url = new URL(API_BASE + "/models/" + modelId + ":generateContent?key=" + apiKey);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(30000);
        conn.setDoOutput(true);

        JSONObject root = new JSONObject();
        JSONArray contents = new JSONArray();
        JSONObject content = new JSONObject();
        JSONArray parts = new JSONArray();
        JSONObject part = new JSONObject();
        part.put("text", userPrompt);
        parts.put(part);
        content.put("parts", parts);
        contents.put(content);
        root.put("contents", contents);

        byte[] payload = root.toString().getBytes(StandardCharsets.UTF_8);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(payload);
            os.flush();
        }

        int code = conn.getResponseCode();
        if (code >= 200 && code < 300) {
            String respStr = readStream(conn.getInputStream());
            JSONObject respObj = new JSONObject(respStr);
            JSONArray candidates = respObj.optJSONArray("candidates");
            if (candidates != null && candidates.length() > 0) {
                JSONObject c0 = candidates.getJSONObject(0);
                JSONObject cContent = c0.optJSONObject("content");
                if (cContent != null) {
                    JSONArray cParts = cContent.optJSONArray("parts");
                    if (cParts != null && cParts.length() > 0) {
                        return cParts.getJSONObject(0).optString("text", "");
                    }
                }
            }
            return "";
        } else {
            String errStr = readStream(conn.getErrorStream());
            throw new Exception("HTTP " + code + ": " + errStr);
        }
    }

    private static String readStream(InputStream is) {
        if (is == null) return "";
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append("\n");
            }
            return sb.toString().trim();
        } catch (Exception e) {
            return "";
        }
    }
}
