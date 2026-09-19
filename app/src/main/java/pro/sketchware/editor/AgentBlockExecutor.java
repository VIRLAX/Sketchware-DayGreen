package pro.sketchware.editor;

import com.besome.sketch.beans.BlockBean;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;
import java.util.Locale;

import a.a.a.eC;
import a.a.a.jC;

/** Applies an explicit BlockBean JSON proposal to one Sketchware event after Accept. */
public final class AgentBlockExecutor {
    private AgentBlockExecutor() { }

    public static String apply(String scId, String target, String json) {
        if (scId == null || scId.trim().isEmpty()) return "No project is selected.";
        if (target == null || !target.toLowerCase(Locale.US).startsWith("blocks/")) return "Invalid block target.";
        String spec = target.substring("blocks/".length());
        int separator = spec.indexOf('/');
        if (separator <= 0 || separator == spec.length() - 1) return "Block target must be blocks/<JavaFile>/<eventKey>.";
        String javaName = spec.substring(0, separator);
        String eventKey = spec.substring(separator + 1);
        if (javaName.contains("..") || eventKey.contains("..")) return "Unsafe block target.";
        try {
            ArrayList<BlockBean> blocks = new Gson().fromJson(json, new TypeToken<ArrayList<BlockBean>>() { }.getType());
            if (blocks == null) return "Block JSON was empty.";
            eC manager = jC.a(scId);
            manager.a(javaName, eventKey, blocks);
            manager.k();
            return "APPLIED_BLOCKS:" + javaName + "/" + eventKey + " (" + blocks.size() + " blocks)";
        } catch (Exception e) {
            return "Block import failed: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
    }
}
