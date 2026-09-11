package pro.sketchware.utility;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Build;
import java.util.Locale;

public final class LocaleManager {
    public static final String ACTION_LOCALE_CHANGED = "DAYGREEN_LOCALE_CHANGED";
    public static final String ENGLISH = "en";
    public static final String INDONESIAN = "in";
    public static final String CHINESE = "zh";
    public static final String ARABIC = "ar";
    public static final String SPANISH = "es";
    public static final String JAVANESE = "jv";
    private static final String PREFS = "daygreen_locale";
    private static final String KEY_LANGUAGE = "language";
    private LocaleManager() {}

    public static String getLanguage(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_LANGUAGE, ENGLISH);
    }

    public static void setLanguage(Context context, String language) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_LANGUAGE, language).apply();
        updateResources(context, language);
        context.sendBroadcast(new Intent(ACTION_LOCALE_CHANGED).setPackage(context.getPackageName()));
    }

    public static Context apply(Context context) { return updateResources(context, getLanguage(context)); }

    private static Context updateResources(Context context, String language) {
        Locale locale = switch (language) {
            case INDONESIAN -> new Locale("in", "ID");
            case CHINESE -> Locale.SIMPLIFIED_CHINESE;
            case ARABIC -> new Locale("ar");
            case SPANISH -> new Locale("es");
            case JAVANESE -> new Locale("jv", "ID");
            default -> Locale.ENGLISH;
        };
        Locale.setDefault(locale);
        Configuration configuration = new Configuration(context.getResources().getConfiguration());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) configuration.setLocales(new android.os.LocaleList(locale));
        else configuration.locale = locale;
        return context.createConfigurationContext(configuration);
    }
}
