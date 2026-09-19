package pro.sketchware.editor;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Device-scoped encrypted profiles and chat sessions. */
public final class AgentWorkspaceStore {
    private static final String PREFS = "daygreen_agent_workspace_v2";
    private static final String PROFILES = "profiles";
    private static final String SESSIONS = "sessions";
    private static final String CURRENT = "current_session";
    private static final String KEY_ALIAS = "daygreen_agent_workspace_key_v2";
    private static final int TAG_BITS = 128;
    private final SharedPreferences prefs;
    private final AgentSettingsStore legacy;

    public AgentWorkspaceStore(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        legacy = new AgentSettingsStore(context);
    }

    public static final class Profile {
        public String id = UUID.randomUUID().toString();
        public String name = "Gemini 1";
        public String key = "";
        public boolean enabled = true;
        public List<String> models = new ArrayList<>();
        public long lastUsed;
        public int failures;
        public String lastError = "";
        public String rate = "";
    }

    public static final class Message {
        public String role;
        public String text;
        public long time;
        public Message(String role, String text, long time) { this.role = role; this.text = text; this.time = time; }
    }

    public static final class Session {
        public String id = UUID.randomUUID().toString();
        public String title = "New chat";
        public long created;
        public long updated;
        public List<Message> messages = new ArrayList<>();
    }

    public synchronized List<Profile> getProfiles() {
        List<Profile> list = new ArrayList<>();
        try {
            String raw = decrypt(prefs.getString(PROFILES, ""));
            if (!raw.isEmpty()) {
                JSONArray arr = new JSONArray(raw);
                for (int i = 0; i < arr.length(); i++) list.add(profileFrom(arr.getJSONObject(i)));
            }
        } catch (Exception ignored) { }
        if (list.isEmpty()) {
            String old = legacy.getApiKey();
            if (!old.isEmpty()) {
                Profile p = new Profile(); p.name = "Gemini 1"; p.key = old; list.add(p); saveProfiles(list);
            }
        }
        return list;
    }

    public synchronized void saveProfiles(List<Profile> profiles) {
        JSONArray arr = new JSONArray();
        try { for (Profile p : profiles) arr.put(profileTo(p)); } catch (Exception ignored) { }
        prefs.edit().putString(PROFILES, encrypt(arr.toString())).apply();
    }

    public synchronized void addProfile(String name) {
        List<Profile> list = getProfiles(); Profile p = new Profile(); p.name = name == null || name.trim().isEmpty() ? "Gemini " + (list.size() + 1) : name.trim(); list.add(p); saveProfiles(list);
    }

    public synchronized void removeProfile(String id) {
        List<Profile> list = getProfiles(); list.removeIf(p -> p.id.equals(id)); saveProfiles(list);
    }

    public synchronized Profile findProfile(String id) {
        for (Profile p : getProfiles()) if (p.id.equals(id)) return p;
        return null;
    }

    public synchronized List<Session> getSessions() {
        List<Session> list = new ArrayList<>();
        try {
            String raw = decrypt(prefs.getString(SESSIONS, ""));
            if (!raw.isEmpty()) {
                JSONArray arr = new JSONArray(raw);
                for (int i = 0; i < arr.length(); i++) list.add(sessionFrom(arr.getJSONObject(i)));
            }
        } catch (Exception ignored) { }
        list.sort((a, b) -> Long.compare(b.updated, a.updated));
        return list;
    }

    public synchronized Session getOrCreateCurrent() {
        String id = prefs.getString(CURRENT, "");
        List<Session> sessions = getSessions();
        for (Session s : sessions) if (s.id.equals(id)) return s;
        Session created = newSession("New chat");
        prefs.edit().putString(CURRENT, created.id).apply();
        return created;
    }

    public synchronized Session newSession(String title) {
        List<Session> sessions = getSessions(); Session s = new Session(); s.title = title == null || title.trim().isEmpty() ? "New chat" : title.trim(); s.created = s.updated = System.currentTimeMillis(); sessions.add(s); saveSessions(sessions); prefs.edit().putString(CURRENT, s.id).apply(); return s;
    }

    public synchronized void selectSession(String id) { prefs.edit().putString(CURRENT, id).apply(); }
    public synchronized String currentSessionId() { return prefs.getString(CURRENT, ""); }

    public synchronized Session getSession(String id) {
        for (Session s : getSessions()) if (s.id.equals(id)) return s;
        return null;
    }

    public synchronized void append(String sessionId, String role, String text) {
        List<Session> sessions = getSessions();
        for (Session s : sessions) if (s.id.equals(sessionId)) {
            s.messages.add(new Message(role, text == null ? "" : text, System.currentTimeMillis()));
            if ("user".equals(role) && (s.title.equals("New chat") || s.title.isEmpty())) s.title = titleFor(text);
            s.updated = System.currentTimeMillis(); break;
        }
        saveSessions(sessions);
    }

    public synchronized void deleteSession(String id) {
        List<Session> sessions = getSessions(); sessions.removeIf(s -> s.id.equals(id)); saveSessions(sessions);
        if (id.equals(currentSessionId())) { if (sessions.isEmpty()) newSession("New chat"); else selectSession(sessions.get(0).id); }
    }

    private void saveSessions(List<Session> sessions) {
        JSONArray arr = new JSONArray();
        try { for (Session s : sessions) arr.put(sessionTo(s)); } catch (Exception ignored) { }
        prefs.edit().putString(SESSIONS, encrypt(arr.toString())).apply();
    }

    private static String titleFor(String text) {
        String value = text == null ? "New chat" : text.trim().replaceAll("\\s+", " ");
        if (value.isEmpty()) return "New chat";
        return value.length() > 42 ? value.substring(0, 42).trim() + "…" : value;
    }

    private static JSONObject profileTo(Profile p) throws Exception { JSONObject o = new JSONObject(); o.put("id", p.id); o.put("name", p.name); o.put("key", p.key); o.put("enabled", p.enabled); o.put("models", new JSONArray(p.models)); o.put("lastUsed", p.lastUsed); o.put("failures", p.failures); o.put("lastError", p.lastError); o.put("rate", p.rate); return o; }
    private static Profile profileFrom(JSONObject o) { Profile p = new Profile(); p.id = o.optString("id", p.id); p.name = o.optString("name", "Gemini"); p.key = o.optString("key", ""); p.enabled = o.optBoolean("enabled", true); JSONArray a = o.optJSONArray("models"); if (a != null) for (int i = 0; i < a.length(); i++) p.models.add(a.optString(i)); p.lastUsed = o.optLong("lastUsed"); p.failures = o.optInt("failures"); p.lastError = o.optString("lastError", ""); p.rate = o.optString("rate", ""); return p; }
    private static JSONObject sessionTo(Session s) throws Exception { JSONObject o = new JSONObject(); o.put("id", s.id); o.put("title", s.title); o.put("created", s.created); o.put("updated", s.updated); JSONArray a = new JSONArray(); for (Message m : s.messages) a.put(new JSONObject().put("role", m.role).put("text", m.text).put("time", m.time)); o.put("messages", a); return o; }
    private static Session sessionFrom(JSONObject o) { Session s = new Session(); s.id = o.optString("id", s.id); s.title = o.optString("title", "New chat"); s.created = o.optLong("created"); s.updated = o.optLong("updated"); JSONArray a = o.optJSONArray("messages"); if (a != null) for (int i = 0; i < a.length(); i++) { JSONObject m = a.optJSONObject(i); if (m != null) s.messages.add(new Message(m.optString("role", "assistant"), m.optString("text", ""), m.optLong("time"))); } return s; }

    private String encrypt(String value) { try { Cipher c = Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.ENCRYPT_MODE, key()); byte[] body = c.doFinal(value.getBytes(StandardCharsets.UTF_8)); byte[] all = new byte[c.getIV().length + body.length]; System.arraycopy(c.getIV(), 0, all, 0, c.getIV().length); System.arraycopy(body, 0, all, c.getIV().length, body.length); return Base64.encodeToString(all, Base64.NO_WRAP); } catch (Exception e) { return ""; } }
    private String decrypt(String value) { if (value == null || value.isEmpty()) return ""; try { byte[] all = Base64.decode(value, Base64.NO_WRAP); byte[] iv = new byte[12]; byte[] body = new byte[all.length - 12]; System.arraycopy(all, 0, iv, 0, 12); System.arraycopy(all, 12, body, 0, body.length); Cipher c = Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, iv)); return new String(c.doFinal(body), StandardCharsets.UTF_8); } catch (Exception e) { return ""; } }
    private SecretKey key() throws Exception { KeyStore ks = KeyStore.getInstance("AndroidKeyStore"); ks.load(null); if (ks.containsAlias(KEY_ALIAS)) return ((KeyStore.SecretKeyEntry) ks.getEntry(KEY_ALIAS, null)).getSecretKey(); KeyGenerator g = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore"); g.init(new KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setRandomizedEncryptionRequired(true).build()); return g.generateKey(); }
}
