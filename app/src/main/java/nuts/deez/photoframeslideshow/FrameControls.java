package nuts.deez.photoframeslideshow;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.SystemClock;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.ProgressBar;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Coordinates slideshow overlays, settings and ambient-light sleep without changing global brightness. */
final class FrameControls implements SensorEventListener {
    private final MainActivity a;
    private final Handler handler = new Handler();
    private final SensorManager sensors;
    private final Sensor light;
    private final TextView info, clock, date;
    private final ProgressBar progress;
    private final NightClockView curtain;
    private boolean dark, stopped = true, autoDark;
    private int darkLux = 2, lightDelay = 5;
    private boolean showProgress = true;
    private long started = SystemClock.elapsedRealtime(), candidateSince;
    private boolean candidateDark;
    private float previousBrightness;
    private long clockSecond = -1;
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("EEEE, dd/MM/yyyy", Locale.ENGLISH);
    private final Runnable nightTick = new Runnable() {
        @Override public void run() {
            if (stopped || !dark) return;
            curtain.updateMinute();
            handler.postDelayed(this, 60000 - System.currentTimeMillis() % 60000 + 50);
        }
    };
    private final Runnable commitLight = () -> {
        if (!stopped && autoDark && candidateDark != dark) setDark(candidateDark);
        candidateSince = 0;
    };
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (blocked()) return;
            long now = SystemClock.elapsedRealtime();
            if (!a.pause && a.active && !a.picturesRemaining.isEmpty()) {
                if (a.showingVideo) progress.setProgress(a.video.duration() > 0 ? (int) Math.min(1000, a.video.position() * 1000L / a.video.duration()) : 0);
                else progress.setProgress((int) Math.min(1000, (now - started) * 1000 / (a.imageUpdateTime * 1000L)));
            }
            progress.setVisibility(!showProgress || a.pause || !a.active || a.picturesRemaining.isEmpty() ? View.INVISIBLE : View.VISIBLE);
            long second = System.currentTimeMillis() / 1000;
            if (second != clockSecond) {
                clockSecond = second;
                a.refreshSchedule();
                Date current = new Date();
                clock.setText(timeFormat.format(current));
                date.setText(dateFormat.format(current));
            }
            handler.postDelayed(this, 100);
        }
    };
    FrameControls(MainActivity activity) {
        a = activity;
        dateFormat.setDateFormatSymbols(new java.text.DateFormatSymbols(AppLanguage.locale(a)));
        info = a.findViewById(R.id.photoInfo);
        clock = a.findViewById(R.id.frameClock);
        date = a.findViewById(R.id.frameDate);
        progress = a.findViewById(R.id.photoProgress);
        curtain = a.findViewById(R.id.darkCurtain);
        curtain.setOnTouchListener(new OnSwipeTouchListener(a) {
            @Override public void onDoubleScreenTap() { showSettings(); }
        });
        sensors = (SensorManager) a.getSystemService(Context.SENSOR_SERVICE);
        light = sensors == null ? null : sensors.getDefaultSensor(Sensor.TYPE_LIGHT);
        autoDark = a.getPreferences(Context.MODE_PRIVATE).getBoolean("autoDark", true);
        previousBrightness = a.getWindow().getAttributes().screenBrightness;
    }
    boolean blocked() { return dark || stopped; }
    boolean nightMode() { return dark && !stopped; }
    boolean ambientDark() { return dark; }
    void restoreNight(boolean value) {
        dark = value;
        curtain.setVisibility(value ? View.VISIBLE : View.GONE);
    }
    void resetCountdown() { started = SystemClock.elapsedRealtime(); progress.setProgress(0); }
    void setPhoto(String text) { info.setText(text); }
    void refreshPlayback() {
        a.updateImageHandler.removeCallbacks(a.updateImageRunnable);
        resetCountdown();
        if (!blocked()) { a.delayImageUpdate(); handler.removeCallbacks(tick); handler.post(tick); }
    }
    void start() {
        a.applySettings();
        android.content.SharedPreferences prefs = FramePreferences.get(a);
        autoDark = prefs.getBoolean("autoDark", true);
        darkLux = prefs.getInt("darkLux", 2);
        lightDelay = prefs.getInt("lightDelay", 5);
        showProgress = prefs.getBoolean("showProgress", true);
        info.setVisibility(prefs.getBoolean("showInfo", true) ? View.VISIBLE : View.GONE);
        clock.setVisibility(prefs.getBoolean("showClock", true) ? View.VISIBLE : View.GONE);
        date.setVisibility(prefs.getBoolean("showClock", true) ? View.VISIBLE : View.GONE);
        if (!autoDark) { dark = false; curtain.setVisibility(View.GONE); }
        stopped = false;
        candidateSince = 0;
        if (dark) enterNightDisplay();
        if (autoDark && light != null) sensors.registerListener(this, light, SensorManager.SENSOR_DELAY_NORMAL);
        if (!blocked()) { a.resumeFrame(); handler.removeCallbacks(tick); handler.post(tick); }
    }
    private void enterNightDisplay() {
        WindowManager.LayoutParams params = a.getWindow().getAttributes();
        params.screenBrightness = Math.max(1, Math.min(20, FramePreferences.get(a).getInt("nightBrightness", 3))) / 100f;
        a.getWindow().setAttributes(params);
        a.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        curtain.setVisibility(View.VISIBLE);
        handler.removeCallbacks(tick);
        a.suspendFrame();
        handler.removeCallbacks(nightTick); handler.post(nightTick);
    }
    void stop() {
        stopped = true;
        if (sensors != null) sensors.unregisterListener(this);
        handler.removeCallbacksAndMessages(null);
        a.suspendFrame();
        restoreBrightness();
    }
    private void restoreBrightness() {
        WindowManager.LayoutParams params = a.getWindow().getAttributes();
        params.screenBrightness = previousBrightness;
        a.getWindow().setAttributes(params);
    }
    private void setDark(boolean value) {
        if (dark == value) return;
        dark = value;
        android.util.Log.i("PhotoFrameSlideshow", "Night calendar: " + value);
        curtain.setVisibility(dark ? View.VISIBLE : View.GONE);
        if (dark) {
            previousBrightness = a.getWindow().getAttributes().screenBrightness;
            enterNightDisplay();
        } else {
            handler.removeCallbacks(nightTick);
            restoreBrightness();
            clockSecond = -1;
            a.refreshSchedule();
            if (!blocked()) { a.resumeFrame(); handler.post(tick); }
        }
    }
    @Override public void onSensorChanged(SensorEvent event) {
        if (!autoDark || stopped) return;
        float lux = event.values[0];
        boolean target = dark ? lux < Math.max(darkLux + 3, darkLux * 4) : lux <= darkLux;
        if (target == dark) { handler.removeCallbacks(commitLight); candidateSince = 0; return; }
        if (candidateSince == 0 || candidateDark != target) {
            candidateDark = target;
            candidateSince = SystemClock.elapsedRealtime();
            handler.removeCallbacks(commitLight);
            handler.postDelayed(commitLight, lightDelay * 1000L);
        }
    }
    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}
    void showSettings() {
        if (stopped) return;
        a.startActivity(new android.content.Intent(a, SettingsActivity.class)
                .putExtra("imageTime", a.imageUpdateTime).putExtra("shuffle", a.shuffleImages)
                .putExtra("scheduleStart", a.activeHourStart).putExtra("scheduleEnd", a.activeHourEnd));
    }
    void close() {
        stopped = true;
        if (sensors != null) sensors.unregisterListener(this);
        handler.removeCallbacksAndMessages(null);
        restoreBrightness();
    }
}
