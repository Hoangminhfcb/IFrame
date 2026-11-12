package nuts.deez.photoframeslideshow;

import android.content.Context;
import android.content.res.Configuration;
import java.util.Locale;

/** Persisted app language, independent of the device language. */
final class AppLanguage {
    static String code(Context context) {
        return "vi".equals(FramePreferences.get(context).getString("language", "en")) ? "vi" : "en";
    }
    static Locale locale(Context context) { return Locale.forLanguageTag(code(context)); }
    static Context wrap(Context context) {
        Configuration configuration = new Configuration(context.getResources().getConfiguration());
        configuration.setLocale(locale(context));
        return context.createConfigurationContext(configuration);
    }
    private AppLanguage() {}
}
