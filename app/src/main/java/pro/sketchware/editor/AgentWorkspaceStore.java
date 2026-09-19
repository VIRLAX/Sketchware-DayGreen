package pro.sketchware.editor;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Device-scoped persistent storage for Gemini API profiles and chat sessions.
 * Stored cleanly in SharedPreferences with fallback so keys persist permanently across app restarts.
 */
public final class AgentWorkspaceStore {
    private static final String PREFS = "daygreen_agent_workspace_v2";
    private static final String PROFILES = "profiles_json";
    private static final String SESSIONS = "sessions_json";
    private static final String CURRENT = "current_session";
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
            String raw = prefs.getString(PROFILES, "");
            if (raw != null && !raw.trim().isEmpty()) {
                JSONArray arr = new JSONArray(raw);
                for (int i = 0; i < arr.length(); i++) list.add(profileFrom(arr.getJSONObject(i)));
            }
        } catch (Exception ignored) { }
        if (list.isEmpty()) {
            String old = legacy.getApiKey();
            Profile p = new Profile();
            p.name = "Gemini 1";
            p.key = old == null ? "" : old.trim();
            p.enabled = !p.key.isEmpty();
            list.add(p);
            saveProfiles(list);
        }
        return list;
    }

    public synchronized void saveProfiles(List<Profile> profiles) {
        JSONArray arr = new JSONArray();
        try { for (Profile p : profiles) arr.put(profileTo(p)); } catch (Exception ignored) { }
        prefs.edit().putString(PROFILES, arr.toString()).apply();
    }

    public synchronized Profile addProfile(String name) {
        List<Profile> list = getProfiles();
        Profile p = new Profile();
        int idx = list.size() + 1;
        p.name = name == null || name.trim().isEmpty() ? "Gemini " + idx : name.trim();
        p.enabled = true;
        list.add(p);
        saveProfiles(list);
        return p;
    }

    public synchronized void removeProfile(String id) {
        List<Profile> list = getProfiles();
        list.removeIf(p -> p.id.equals(id));
        if (list.isEmpty()) {
            Profile p = new Profile();
            p.name = "Gemini 1";
            list.add(p);
        }
        saveProfiles(list);
    }

    public synchronized Profile findProfile(String id) {
        for (Profile p : getProfiles()) if (p.id.equals(id)) return p;
        return null;
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
        normalizeEmptySessions(list);
        return list;
    }

    public synchronized Session getOrCreateCurrent() {
        String id = prefs.getString(CURRENT, "");
        List<Session> sessions = getSessions();
        for (Session s : sessions) if (s.id.equals(id)) return s;
        if (!sessions.isEmpty() && isBlank(sessions.get(0))) {
            prefs.edit().putString(CURRENT, sessions.get(0).id).apply();
            return sessions.get(0);
        }
        Session created = newSession("New chat");
        prefs.edit().putString(CURRENT, created.id).apply();
        return created;
    }

    public synchronized Session newSession(String title) {
        String requested = title == null || title.trim().isEmpty() ? "New chat" : title.trim();
        List<Session> sessions = getSessions();
        String currentId = prefs.getString(CURRENT, "");
        if ("New chat".equals(requested)) {
            for (Session s : sessions) if (s.id.equals(currentId) && isBlank(s)) return s;
            for (Session s : sessions) if (isBlank(s)) {
                prefs.edit().putString(CURRENT, s.id).apply();
                return s;
            }
        }
        Session s = new Session();
        s.title = requested;
        s.created = s.updated = System.currentTimeMillis();
        sessions.add(s);
        saveSessions(sessions);
        prefs.edit().putString(CURRENT, s.id).apply();
        return s;
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
            s.updated = System.currentTimeMillis();
            break;
        }
        saveSessions(sessions);
    }

    public synchronized void deleteSession(String id) {
        List<Session> sessions = getSessions();
        sessions.removeIf(s -> s.id.equals(id));
        saveSessions(sessions);
        if (id.equals(currentSessionId())) {
            if (sessions.isEmpty()) newSession("New chat");
            else selectSession(sessions.get(0).id);
        }
    }

    private void normalizeEmptySessions(List<Session> sessions) {
        Session keep = null;
        boolean changed = false;
        for (int i = sessions.size() - 1; i >= 0; i--) {
            Session s = sessions.get(i);
            if (!isBlank(s)) continue;
            if (keep == null) keep = s;
            else { sessions.remove(i); changed = true; }
        }
        if (keep != null && changed) {
            String current = prefs.getString(CURRENT, "");
            boolean currentRemoved = true;
            for (Session s : sessions) if (s.id.equals(current)) { currentRemoved = false; break; }
            if (currentRemoved) prefs.edit().putString(CURRENT, keep.id).apply();
            saveSessions(sessions);
        }
    }

    private static boolean isBlank(Session s) { return s != null && s.messages.isEmpty() && (s.title == null || s.title.trim().isEmpty() || "New chat".equals(s.title)); }

    private void saveSessions(List<Session> sessions) {
        JSONArray arr = new JSONArray();
        try { for (Session s : sessions) arr.put(sessionTo(s)); } catch (Exception ignored) { }
        prefs.edit().putString(SESSIONS, arr.toString()).apply();
    }

    private static String titleFor(String text) {
        String value = text == null ? "New chat" : text.trim().replaceAll("\\s+", " ");
        if (value.isEmpty()) return "New chat";
        return value.length() > 36 ? value.substring(0, 36).trim() + "…" : value;
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
