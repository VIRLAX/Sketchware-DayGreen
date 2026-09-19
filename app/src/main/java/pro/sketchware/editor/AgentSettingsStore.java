package pro.sketchware.editor;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.List;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Workspace-scoped Gemini settings. The API key is encrypted with Android Keystore. */
public final class AgentSettingsStore {
    private static final String PREFS = "daygreen_gemini_agent";
    private static final String KEY_ALIAS = "daygreen_gemini_agent_key_v1";
    private static final String API_KEY = "api_key";
    private static final String MODEL = "model";
    private static final String HISTORY = "history";
    private static final int GCM_TAG_BITS = 128;

    private final SharedPreferences preferences;

    public AgentSettingsStore(Context context) {
        preferences = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public String getApiKey() {
        String encoded = preferences.getString(API_KEY, "");
        if (encoded.isEmpty()) return "";
        try {
            byte[] packed = Base64.decode(encoded, Base64.NO_WRAP);
            byte[] iv = new byte[12];
            System.arraycopy(packed, 0, iv, 0, iv.length);
            byte[] encrypted = new byte[packed.length - iv.length];
            System.arraycopy(packed, iv.length, encrypted, 0, encrypted.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, getKey(), new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            return "";
        }
    }

    public void saveApiKey(String apiKey) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, getKey());
            byte[] encrypted = cipher.doFinal(apiKey.trim().getBytes(StandardCharsets.UTF_8));
            byte[] packed = new byte[cipher.getIV().length + encrypted.length];
            System.arraycopy(cipher.getIV(), 0, packed, 0, cipher.getIV().length);
            System.arraycopy(encrypted, 0, packed, cipher.getIV().length, encrypted.length);
            preferences.edit().putString(API_KEY, Base64.encodeToString(packed, Base64.NO_WRAP)).apply();
        } catch (Exception ignored) {
            // Do not fall back to plaintext storage for an API credential.
        }
    }

    public String getModel() {
        return preferences.getString(MODEL, "gemini-2.5-flash");
    }

    public void saveModel(String model) {
        preferences.edit().putString(MODEL, model).apply();
    }

    public void appendHistory(String prompt) {
        String clean = prompt == null ? "" : prompt.trim().replace("|", " ");
        if (clean.isEmpty()) return;
        String current = preferences.getString(HISTORY, "");
        String next = clean + "|" + current;
        String[] entries = next.split("\\|");
        StringBuilder bounded = new StringBuilder();
        for (int i = 0; i < entries.length && i < 20; i++) {
            if (entries[i].trim().isEmpty()) continue;
            if (bounded.length() > 0) bounded.append('|');
            bounded.append(entries[i].trim());
        }
        preferences.edit().putString(HISTORY, bounded.toString()).apply();
    }

    public List<String> getHistory() {
        List<String> result = new ArrayList<>();
        String current = preferences.getString(HISTORY, "");
        if (!current.isEmpty()) {
            for (String item : current.split("\\|")) {
                if (!item.trim().isEmpty()) result.add(item.trim());
            }
        }
        return result;
    }

    private SecretKey getKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        if (keyStore.containsAlias(KEY_ALIAS)) {
            return ((KeyStore.SecretKeyEntry) keyStore.getEntry(KEY_ALIAS, null)).getSecretKey();
        }
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build());
        return generator.generateKey();
    }
}
