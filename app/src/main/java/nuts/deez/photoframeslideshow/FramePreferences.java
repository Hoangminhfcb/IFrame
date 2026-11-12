package nuts.deez.photoframeslideshow;

import android.content.Context;
import android.content.SharedPreferences;

final class FramePreferences {
    static SharedPreferences get(Context context) {
        // Preserve settings saved by earlier versions of MainActivity.getPreferences().
        return context.getSharedPreferences("MainActivity", Context.MODE_PRIVATE);
    }
    static int photoCount(Context c) { return Math.max(0, Math.min(4, get(c).getInt("photoCount", 1))); }
}
