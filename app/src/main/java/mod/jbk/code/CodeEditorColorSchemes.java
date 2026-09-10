package mod.jbk.code;

import android.content.res.AssetManager;
import org.eclipse.tm4e.core.registry.IThemeSource;
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme;
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry;
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry;
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel;
import io.github.rosemoe.sora.langs.textmate.registry.provider.AssetsFileResolver;
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme;
import mod.jbk.util.LogUtil;
import pro.sketchware.SketchApplication;

public class CodeEditorColorSchemes {
    public static final String THEME_GITHUB_LIGHT = "github_light.json";
    public static final String THEME_GITHUB_DARK = "github_dark.json";
    public static final String THEME_RAINBOW = "rainbow.json";
    public static final String THEME_ONE_DARK_PRO = "one_dark_pro.json";
    public static final String THEME_SYNTHWAVE = "synthwave_84.json";
    public static final String THEME_TOKYO_NIGHT = "tokyo_night.json";
    public static final String[] THEMES = {THEME_GITHUB_LIGHT, THEME_GITHUB_DARK, THEME_RAINBOW, THEME_ONE_DARK_PRO, THEME_SYNTHWAVE, THEME_TOKYO_NIGHT};
    private static final String TAG = "CodeEditorColorSchemes";

    static {
        AssetManager assets = SketchApplication.getContext().getAssets();
        FileProviderRegistry.getInstance().addFileProvider(new AssetsFileResolver(assets));
        ThemeRegistry registry = ThemeRegistry.getInstance();
        for (String theme : THEMES) {
            String path = "textmate/themes/" + theme;
            try { registry.loadTheme(new ThemeModel(IThemeSource.fromInputStream(FileProviderRegistry.getInstance().tryGetInputStream(path), path, null), theme)); }
            catch (Exception e) { LogUtil.e(TAG, "Failed to load theme '" + theme + "'", e); }
        }
    }

    public static EditorColorScheme loadTextMateColorScheme(String theme) {
        try { ThemeRegistry.getInstance().setTheme(theme); return TextMateColorScheme.create(ThemeRegistry.getInstance()); }
        catch (Exception e) { LogUtil.e(TAG, "Failed to load theme '" + theme + "', using defaults", e); return new EditorColorScheme(); }
    }
}
