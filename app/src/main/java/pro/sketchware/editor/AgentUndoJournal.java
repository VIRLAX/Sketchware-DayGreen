package pro.sketchware.editor;

import android.content.Context;

import com.besome.sketch.beans.BlockBean;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Base64;

import a.a.a.eC;
import a.a.a.jC;
import pro.sketchware.utility.FilePathUtil;

/** Persists the last accepted change so the user can safely restore it after restart. */
public final class AgentUndoJournal {
    private AgentUndoJournal() { }

    private static File journal(String scId) {
        File data = new File(FilePathUtil.getLastCompileLogPath(scId)).getParentFile();
        return new File(data == null ? new File(".") : data, "agent_undo.json");
    }

    public static void saveFile(String scId, String path, boolean existed, String content) {
        try {
            JSONObject o = new JSONObject();
            o.put("kind", "file"); o.put("path", path); o.put("existed", existed);
            o.put("content", Base64.getEncoder().encodeToString((content == null ? "" : content).getBytes(StandardCharsets.UTF_8)));
            Files.write(journal(scId).toPath(), o.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) { }
    }

    public static void saveBlocks(String scId, String javaName, String eventKey, ArrayList<BlockBean> blocks) {
        try {
            JSONObject o = new JSONObject(); o.put("kind", "blocks"); o.put("java", javaName); o.put("event", eventKey);
            o.put("content", Base64.getEncoder().encodeToString(new Gson().toJson(blocks).getBytes(StandardCharsets.UTF_8)));
            Files.write(journal(scId).toPath(), o.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) { }
    }

    public static String revert(String scId) {
        try {
            File file = journal(scId); if (!file.isFile()) return "No accepted change to undo.";
            JSONObject o = new JSONObject(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
            String kind = o.optString("kind", "");
            if ("file".equals(kind)) {
                File target = new File(o.optString("path", ""));
                if (o.optBoolean("existed", false)) {
                    File parent = target.getParentFile(); if (parent != null) parent.mkdirs();
                    String content = new String(Base64.getDecoder().decode(o.optString("content", "")), StandardCharsets.UTF_8);
                    Files.write(target.toPath(), content.getBytes(StandardCharsets.UTF_8));
                } else if (target.isFile()) target.delete();
            } else if ("blocks".equals(kind)) {
                String raw = new String(Base64.getDecoder().decode(o.optString("content", "")), StandardCharsets.UTF_8);
                ArrayList<BlockBean> blocks = new Gson().fromJson(raw, new TypeToken<ArrayList<BlockBean>>() { }.getType());
                eC manager = jC.a(scId); manager.a(o.optString("java", ""), o.optString("event", ""), blocks); manager.k();
            } else return "Undo journal is invalid.";
            file.delete(); return "Last accepted change was restored.";
        } catch (Exception e) {
            return "Undo failed: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
    }
}
