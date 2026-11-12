package nuts.deez.photoframeslideshow;

import android.app.Instrumentation;
import android.app.AlertDialog;
import android.content.Intent;
import android.hardware.SensorEvent;
import android.hardware.SensorManager;
import android.os.SystemClock;
import android.view.View;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class FrameControlsTest {
    private Object field(Object obj, String name) throws Exception {
        Field field = obj.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(obj);
    }
    @Test public void darkSleepStopsPlaybackAndRestoresManualPause() throws Exception {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Intent intent = new Intent(instrumentation.getTargetContext(), MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        MainActivity a = (MainActivity) instrumentation.startActivitySync(intent);
        instrumentation.waitForIdleSync();
        final boolean originalAuto = a.getPreferences(0).getBoolean("autoDark", true);
        try {
            instrumentation.runOnMainSync(() -> {
                // Real sensor remains unregistered during deterministic light-event simulation.
                try {
                    ((SensorManager) field(a.controls, "sensors")).unregisterListener(a.controls);
                    Field enabled = FrameControls.class.getDeclaredField("autoDark");
                    enabled.setAccessible(true); enabled.set(a.controls, true);
                    Field threshold = FrameControls.class.getDeclaredField("darkLux");
                    threshold.setAccessible(true); threshold.set(a.controls, 2);
                    Field delay = FrameControls.class.getDeclaredField("lightDelay");
                    delay.setAccessible(true); delay.set(a.controls, 5);
                } catch (Exception e) { throw new AssertionError(e); }
                android.util.Log.i("FrameTest", "unregistered sensor, started=" + !a.controls.blocked());
                a.pause = true;
                a.controls.refreshPlayback();
            });
            Constructor<SensorEvent> constructor = SensorEvent.class.getDeclaredConstructor(int.class);
            constructor.setAccessible(true);
            SensorEvent event = constructor.newInstance(1);
            android.util.Log.i("FrameTest", "simulate dark");
            event.values[0] = 0;
            instrumentation.runOnMainSync(() -> a.controls.onSensorChanged(event));
            SystemClock.sleep(6200);
            instrumentation.runOnMainSync(() -> {
                assertEquals(View.VISIBLE, a.findViewById(R.id.darkCurtain).getVisibility());
                assertEquals(0f, a.getWindow().getAttributes().screenBrightness, 0f);
                assertFalse(a.keepAlive.isHeld());
                assertTrue(a.controls.blocked());
            });
            int index = a.imageIndex;
            SystemClock.sleep(3500);
            assertEquals(index, a.imageIndex);
            // A brief bright flash must not wake the frame.
            android.util.Log.i("FrameTest", "simulate bright");
            event.values[0] = 20;
            instrumentation.runOnMainSync(() -> a.controls.onSensorChanged(event));
            SystemClock.sleep(300);
            android.util.Log.i("FrameTest", "simulate dark");
            event.values[0] = 0;
            instrumentation.runOnMainSync(() -> a.controls.onSensorChanged(event));
            SystemClock.sleep(5100);
            assertTrue(a.controls.blocked());
            android.util.Log.i("FrameTest", "simulate bright");
            event.values[0] = 20;
            instrumentation.runOnMainSync(() -> a.controls.onSensorChanged(event));
            SystemClock.sleep(6200);
            instrumentation.runOnMainSync(() -> {
                assertEquals(View.GONE, a.findViewById(R.id.darkCurtain).getVisibility());
                assertFalse(a.controls.blocked());
                assertTrue(a.pause);
                assertTrue(a.keepAlive.isHeld());
            });
        } finally {
            instrumentation.runOnMainSync(() -> {
                a.getPreferences(0).edit().putBoolean("autoDark", originalAuto).apply();
                a.finish();
            });
        }
    }
}
