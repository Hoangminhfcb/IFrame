package nuts.deez.photoframeslideshow;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.Locale;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class AppLanguageTest {
    @Test public void languageDefaultsToEnglishAndPersistsVietnamese() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        SharedPreferences prefs = FramePreferences.get(context);
        String previous = prefs.getString("language", null);
        try {
            prefs.edit().remove("language").commit();
            Configuration device = new Configuration(context.getResources().getConfiguration());
            device.setLocale(new Locale("vi", "VN"));
            Context vietnameseDevice = context.createConfigurationContext(device);
            assertEquals("en", AppLanguage.code(vietnameseDevice));
            assertEquals("Language", AppLanguage.wrap(vietnameseDevice).getString(R.string.language_title));
            prefs.edit().putString("language", "vi").commit();
            assertEquals("vi", AppLanguage.code(context));
            assertEquals("Ngôn ngữ", AppLanguage.wrap(context).getString(R.string.language_title));
            assertEquals("IFrame", AppLanguage.wrap(context).getString(R.string.app_name));
            prefs.edit().putString("language", "en").commit();
            assertEquals("Language", AppLanguage.wrap(context).getString(R.string.language_title));
        } finally {
            if (previous == null) prefs.edit().remove("language").commit();
            else prefs.edit().putString("language", previous).commit();
        }
    }
}
