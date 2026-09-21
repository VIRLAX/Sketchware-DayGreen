package pro.sketchware.editor;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/** Device-scoped persistent storage for Gemini API profiles and chat sessions.
 * Stored cleanly in SharedPreferences with fallback so keys persist permanently across app restarts.
 */
public final class AgentWorkspaceStore {
    private static final String PREFS = "daygreen_agent_workspace_v3";
    private static final String PROFILES = "profiles_json";
    private static final String SESSIONS = "sessions_json";
    private static final String CURRENT = "current_session";
    private static final String FALLBACK_API_KEY = "fallback_gemini_api_key";
    private final SharedPreferences prefs;

    public AgentWorkspaceStore(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
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

        public boolean isValid() {
            return key != null && !key.trim().isEmpty() && !models.isEmpty();
        }
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
            String raw = prefs.getString(PROFILES, "");
            if (raw != null && !raw.trim().isEmpty()) {
                JSONArray arr = new JSONArray(raw);
                for (int i = 0; i < arr.length(); i++) list.add(profileFrom(arr.getJSONObject(i)));
            }
        } catch (Exception ignored) { }

        // Fallback restoration: if list is empty or has no keys, check fallback string
        String fallbackKey = prefs.getString(FALLBACK_API_KEY, "").trim();
        if (!fallbackKey.isEmpty()) {
            boolean hasKey = false;
            for (Profile p : list) {
                if (p.key != null && !p.key.trim().isEmpty()) {
                    hasKey = true;
                    break;
                }
            }
            if (!hasKey) {
                if (list.isEmpty()) {
                    Profile p = new Profile();
                    p.name = "Gemini 1";
                    p.key = fallbackKey;
                    list.add(p);
                } else {
                    list.get(0).key = fallbackKey;
                }
            }
        }

        // If completely empty, seed exactly 1 initial Profile so the user always has a card ready
        if (list.isEmpty()) {
            Profile defaultProfile = new Profile();
            defaultProfile.name = "Gemini 1";
            list.add(defaultProfile);
            saveProfiles(list);
        }

        return list;
    }

    public synchronized void saveProfiles(List<Profile> profiles) {
        JSONArray arr = new JSONArray();
        String activeKey = "";
        try {
            for (Profile p : profiles) {
                arr.put(profileTo(p));
                if (activeKey.isEmpty() && p.key != null && !p.key.trim().isEmpty()) {
                    activeKey = p.key.trim();
                }
            }
        } catch (Exception ignored) { }

        SharedPreferences.Editor editor = prefs.edit().putString(PROFILES, arr.toString());
        if (!activeKey.isEmpty()) {
            editor.putString(FALLBACK_API_KEY, activeKey);
        }
        editor.commit(); // Use synchronous commit() to ensure instant disk persistence
    }

    public synchronized String getFallbackApiKey() {
        return prefs.getString(FALLBACK_API_KEY, "").trim();
    }

    public synchronized void setDirectApiKey(String key) {
        if (key == null) key = "";
        key = key.trim();
        prefs.edit().putString(FALLBACK_API_KEY, key).commit();
        List<Profile> list = getProfiles();
        if (list.isEmpty()) {
            Profile p = new Profile();
            p.name = "Gemini 1";
            p.key = key;
            list.add(p);
        } else {
            list.get(0).key = key;
        }
        saveProfiles(list);
    }

    public synchronized Profile addProfile(String name) {
        List<Profile> list = getProfiles();
        Profile p = new Profile();
        p.name = name;
        list.add(p);
        saveProfiles(list);
        return p;
    }

    public synchronized void removeProfile(String id) {
        List<Profile> list = getProfiles();
        list.removeIf(p -> p.id.equals(id));
        if (list.isEmpty()) {
            // Keep at least one default profile card ready
            Profile p = new Profile();
            p.name = "Gemini 1";
            list.add(p);
            prefs.edit().remove(FALLBACK_API_KEY).commit();
        }
        saveProfiles(list);
    }

    public synchronized List<Session> getSessions() {
        List<Session> list = new ArrayList<>();
        try {
            String raw = prefs.getString(SESSIONS, "");
            if (raw != null && !raw.trim().isEmpty()) {
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
        cleanEmptySessions(sessions, id);
        for (Session s : sessions) if (s.id.equals(id)) return s;
        if (!sessions.isEmpty()) {
            Session first = sessions.get(0);
            prefs.edit().putString(CURRENT, first.id).commit();
            return first;
        }
        return newSession("New chat");
    }

    private void cleanEmptySessions(List<Session> sessions, String currentId) {
        boolean changed = false;
        Iterator<Session> it = sessions.iterator();
        while (it.hasNext()) {
            Session s = it.next();
            if (s.messages.isEmpty() && !s.id.equals(currentId)) {
                it.remove();
                changed = true;
            }
        }
        if (changed) saveSessions(sessions);
    }

    public synchronized Session newSession(String title) {
        List<Session> sessions = getSessions();
        for (Session s : sessions) {
            if (s.messages.isEmpty()) {
                s.title = title;
                s.updated = System.currentTimeMillis();
                saveSessions(sessions);
                prefs.edit().putString(CURRENT, s.id).commit();
                return s;
            }
        }
        Session s = new Session();
        s.title = title;
        s.created = s.updated = System.currentTimeMillis();
        sessions.add(0, s);
        saveSessions(sessions);
        prefs.edit().putString(CURRENT, s.id).commit();
        return s;
    }

    public synchronized void selectSession(String id) { prefs.edit().putString(CURRENT, id).commit(); }
    public synchronized String currentSessionId() { return prefs.getString(CURRENT, ""); }

    public synchronized Session getSession(String id) {
        for (Session s : getSessions()) if (s.id.equals(id)) return s;
        return null;
    }

    public synchronized void append(String sessionId, String role, String text) {
        List<Session> sessions = getSessions();
        for (Session s : sessions) if (s.id.equals(sessionId)) {
            s.messages.add(new Message(role, text, System.currentTimeMillis()));
            s.updated = System.currentTimeMillis();
            if (("New chat".equalsIgnoreCase(s.title)) && "user".equals(role)) {
                s.title = text.length() > 30 ? text.substring(0, 30) + "..." : text;
            }
            break;
        }
        saveSessions(sessions);
    }

    public synchronized void deleteSession(String id) {
        List<Session> sessions = getSessions();
        sessions.removeIf(s -> s.id.equals(id));
        saveSessions(sessions);
    }

    private void saveSessions(List<Session> sessions) {
        JSONArray arr = new JSONArray();
        try { for (Session s : sessions) arr.put(sessionTo(s)); } catch (Exception ignored) { }
        prefs.edit().putString(SESSIONS, arr.toString()).commit();
    }

    private static JSONObject profileTo(Profile p) throws Exception {
        JSONObject o = new JSONObject();
        o.put("id", p.id);
        o.put("name", p.name);
        o.put("key", p.key);
        o.put("enabled", p.enabled);
        o.put("models", new JSONArray(p.models));
        o.put("lastUsed", p.lastUsed);
        o.put("failures", p.failures);
        o.put("lastError", p.lastError);
        o.put("rate", p.rate);
        return o;
    }

    private static Profile profileFrom(JSONObject o) {
        Profile p = new Profile();
        p.id = o.optString("id", p.id);
        p.name = o.optString("name", "Gemini");
        p.key = o.optString("key", "");
        p.enabled = o.optBoolean("enabled", true);
        JSONArray a = o.optJSONArray("models");
        if (a != null) for (int i = 0; i < a.length(); i++) p.models.add(a.optString(i));
        p.lastUsed = o.optLong("lastUsed");
        p.failures = o.optInt("failures");
        p.lastError = o.optString("lastError", "");
        p.rate = o.optString("rate", "");
        return p;
    }

    private static JSONObject sessionTo(Session s) throws Exception {
        JSONObject o = new JSONObject();
        o.put("id", s.id);
        o.put("title", s.title);
        o.put("created", s.created);
        o.put("updated", s.updated);
        JSONArray a = new JSONArray();
        for (Message m : s.messages) a.put(new JSONObject().put("role", m.role).put("text", m.text).put("time", m.time));
        o.put("messages", a);
        return o;
    }

    private static Session sessionFrom(JSONObject o) {
        Session s = new Session();
        s.id = o.optString("id", s.id);
        s.title = o.optString("title", "New chat");
        s.created = o.optLong("created");
        s.updated = o.optLong("updated");
        JSONArray a = o.optJSONArray("messages");
        if (a != null) {
            for (int i = 0; i < a.length(); i++) {
                JSONObject m = a.optJSONObject(i);
                if (m != null) s.messages.add(new Message(m.optString("role", "assistant"), m.optString("text", ""), m.optLong("time")));
            }
        }
        return s;
    }
}
