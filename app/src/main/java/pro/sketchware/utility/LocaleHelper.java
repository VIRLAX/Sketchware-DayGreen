package pro.sketchware.utility;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.os.LocaleList;

import java.util.Locale;

/**
 * Helper class for managing app locale/language.
 * Saves the user's language preference to SharedPreferences
 * and applies it to the app's Configuration so all UI updates instantly.
 */
public class LocaleHelper {

    private static final String PREFS_NAME = "daygreen_language_prefs";
    private static final String KEY_LANGUAGE = "selected_language";
    private static final String DEFAULT_LANGUAGE = "en";

    /**
     * Call this from attachBaseContext() in Application and every Activity.
     * Returns a Context with the saved locale applied.
     */
    public static Context applyLocale(Context context) {
        String language = getLanguage(context);
        return updateResources(context, language);
    }

    /**
     * Save the selected language code and return a new Context.
     */
    public static Context setLocale(Context context, String language) {
        saveLanguage(context, language);
        return updateResources(context, language);
    }

    /**
     * Get the currently saved language code.
     */
    public static String getLanguage(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_LANGUAGE, DEFAULT_LANGUAGE);
    }

    private static void saveLanguage(Context context, String language) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_LANGUAGE, language).apply();
    }

    private static Context updateResources(Context context, String language) {
        Locale locale = new Locale(language);
        Locale.setDefault(locale);

        Resources resources = context.getResources();
        Configuration config = new Configuration(resources.getConfiguration());

        config.setLocale(locale);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocales(new LocaleList(locale));
        }

        return context.createConfigurationContext(config);
    }
}
