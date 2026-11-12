package nuts.deez.photoframeslideshow;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class SettingsActivityTest {
    @Test public void settingsPersistAndCollageResumesWithEffects() throws Exception {
        Instrumentation ins = InstrumentationRegistry.getInstrumentation();
        Context context = ins.getTargetContext();
        SharedPreferences prefs = FramePreferences.get(context);
        Map<String, ?> original = prefs.getAll();
        SettingsActivity settings = null;
        MainActivity frame = null;
        try {
            prefs.edit().putBoolean("autoDark", false).putBoolean("shuffle", false).apply();
            Intent intent = new Intent(context, SettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            settings = (SettingsActivity) ins.startActivitySync(intent);
            ins.waitForIdleSync();
            SettingsActivity screen = settings;
            ins.runOnMainSync(() -> {
                View root = screen.getWindow().getDecorView();
                ((Spinner) root.findViewWithTag("photoCount")).setSelection(3);
                ((Spinner) root.findViewWithTag("effect")).setSelection(2);
                ((Spinner) root.findViewWithTag("imageTime")).setSelection(5);
                ((Spinner) root.findViewWithTag("cachePercent")).setSelection(2);
            });
            ins.waitForIdleSync();
            assertEquals(4, prefs.getInt("photoCount", 0));
            assertEquals("slide", prefs.getString("effect", ""));
            assertEquals(30, prefs.getInt("imageTime", 0));
            assertEquals(70, prefs.getInt("cachePercent", 0));
            ins.runOnMainSync(screen::finish); ins.waitForIdleSync();
            frame = (MainActivity) ins.startActivitySync(new Intent(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            MainActivity a = frame;
            ins.waitForIdleSync();
            assertEquals(4, a.photosPerPage); assertEquals("slide", a.transitionEffect);
            if (a.picturesRemaining.size() >= 4) {
                AtomicBoolean loaded = new AtomicBoolean();
                for (int i = 0; i < 100 && !loaded.get(); i++) {
                    ins.runOnMainSync(() -> loaded.set(((TextView) a.findViewById(R.id.photoInfo)).getText().toString().contains("4 ·")));
                    if (!loaded.get()) android.os.SystemClock.sleep(100);
                }
                assertTrue("four-photo page loaded", loaded.get());
                ins.runOnMainSync(() -> {
                    a.image.finishTransition();
                    View root = a.getWindow().getDecorView();
                    Bitmap capture = Bitmap.createBitmap(root.getWidth(), root.getHeight(), Bitmap.Config.ARGB_8888);
                    root.draw(new Canvas(capture));
                    try (FileOutputStream out = new FileOutputStream(new File(a.getExternalFilesDir(null), "collage-preview.png"))) {
                        capture.compress(Bitmap.CompressFormat.PNG, 100, out);
                    } catch (Exception e) { throw new AssertionError(e); }
                    capture.recycle();
                    a.imageIndex = 0;
                    assertEquals((a.picturesRemaining.size() - 1) / 4 * 4, a.adjacentPage(-1));
                    a.goToNextImage(); a.goToPreviousImage();
                    assertEquals(0, a.imageIndex);
                });
            }
        } finally {
            SharedPreferences.Editor edit = prefs.edit().clear();
            for (Map.Entry<String, ?> entry : original.entrySet()) {
                Object value = entry.getValue(); String key = entry.getKey();
                if (value instanceof Integer) edit.putInt(key, (Integer) value);
                else if (value instanceof Long) edit.putLong(key, (Long) value);
                else if (value instanceof Boolean) edit.putBoolean(key, (Boolean) value);
                else if (value instanceof String) edit.putString(key, (String) value);
            }
            edit.commit();
            SettingsActivity s = settings; MainActivity a = frame;
            ins.runOnMainSync(() -> { if (s != null) s.finish(); if (a != null) a.finish(); });
        }
    }
}
