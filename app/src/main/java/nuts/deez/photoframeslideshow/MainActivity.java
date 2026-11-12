package nuts.deez.photoframeslideshow;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.exifinterface.media.ExifInterface;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "PhotoFrameSlideshow";
    private static final int REQUEST_EXTERNAL_STORAGE = 1;
    PhotoFrameView image;
    FrameVideoView video;
    PhotoLoader photoLoader;
    FrameControls controls;
    private final StatusBarGuard statusBarGuard = new StatusBarGuard();
    PowerManager.WakeLock keepAlive;
    Handler updateImageHandler;
    private final Handler ntpHandler = new Handler();
    private ClockTask ntpTask;
    private final ExecutorService mediaScanner = Executors.newSingleThreadExecutor();
    private Future<?> scanJob;
    private boolean scanning;
    private int scanSerial;
    private long lastScanAt, configModified = -1, cacheRevision;
    private String sourceSettings = "", resumeSource;
    private final Set<String> unreadableMedia = new HashSet<>();
    List<String> allPictures = new ArrayList<>(), picturesRemaining = new ArrayList<>();
    ArrayList<Integer> randomPageStarts = new ArrayList<>(), randomPageCounts = new ArrayList<>();
    int imageIndex = -1, photosPerPage = 1, configuredPhotoCount = 1, pagePlanMode = -1;
    int frameWidth, frameHeight, photoRequest, transitionDirection = 1, transitionDuration = 600;
    int imageUpdateTime = 60, ntpUpdateTime = 3600, activeHourStart = 7, activeHourEnd = 23;
    boolean pause, active = true, randomPhotoCount, cropPhotos, blurPhotos = true, shuffleImages = true;
    boolean showingVideo, forceClockSync;
    int videoResumePosition;
    String transitionEffect = "fade", ntpServer = "pool.ntp.org";
    String imageFolder = Environment.getExternalStorageDirectory() + "/Pictures/";
    final Runnable updateImageRunnable = () -> {
        if (!pause && active && (controls == null || !controls.blocked())) updateImage();
        delayImageUpdate();
    };
    private final Runnable ntpRunnable = new Runnable() {
        @Override public void run() {
            if (controls == null || controls.blocked()) return;
            if (forceClockSync && !ntpServer.isEmpty() && (ntpTask == null || ntpTask.getStatus() == AsyncTask.Status.FINISHED)) {
                ntpTask = new ClockTask(); ntpTask.execute();
            }
            ntpHandler.postDelayed(this, ntpUpdateTime * 1000L);
        }
    };
    private static final class PlaybackState {
        List<String> all, remaining;
        ArrayList<Integer> starts, counts;
        int index, mode;
    }
    private String activeLanguage;
    @Override protected void attachBaseContext(android.content.Context base) {
        activeLanguage = AppLanguage.code(base);
        super.attachBaseContext(AppLanguage.wrap(base));
    }
    @SuppressLint({"ClickableViewAccessibility", "WakelockTimeout", "BatteryLife"})
    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        setContentView(R.layout.activity_main);
        image = findViewById(R.id.imageView); video = findViewById(R.id.videoView);
        updateImageHandler = new Handler(); photoLoader = PhotoLoader.shared(this);
        keepAlive = ((PowerManager) getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG + ":frame");
        frameWidth = getResources().getDisplayMetrics().widthPixels;
        frameHeight = getResources().getDisplayMetrics().heightPixels;
        Object retained = getLastCustomNonConfigurationInstance();
        if (retained instanceof PlaybackState) {
            PlaybackState state = (PlaybackState) retained;
            allPictures = state.all; picturesRemaining = state.remaining;
            randomPageStarts = state.starts; randomPageCounts = state.counts;
            imageIndex = state.index; pagePlanMode = state.mode;
        }
        if (saved != null) {
            pause = saved.getBoolean("pause"); resumeSource = saved.getString("resumeSource");
            videoResumePosition = saved.getInt("videoPosition");
        }
        findViewById(R.id.gestureLayer).setOnTouchListener(new OnSwipeTouchListener(this) {
            @Override public void onSwipeLeft() { goToNextImage(); }
            @Override public void onSwipeRight() { goToPreviousImage(); }
            @Override public void onSwipeUp() { rotatePicture(false); }
            @Override public void onSwipeDown() { rotatePicture(true); }
            @Override public void onScreenTap(float x) { if (x < image.getWidth() / 2f) goToPreviousImage(); else goToNextImage(); }
            @Override public void onLongPress() { pauseUnpauseImages(); }
            @Override public void onDoubleScreenTap() { controls.showSettings(); }
        });
        loadConfigFile(); controls = new FrameControls(this);
        if (saved != null) controls.restoreNight(saved.getBoolean("ambientDark", false));
        requestMediaPermissions(); updateFlagsAndStuff(); turnScreenOn();
        if (Build.VERSION.SDK_INT >= 23) {
            PowerManager manager = (PowerManager) getSystemService(POWER_SERVICE);
            if (!manager.isIgnoringBatteryOptimizations(getPackageName())) {
                Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + getPackageName()));
                if (intent.resolveActivity(getPackageManager()) != null) startActivity(intent);
            }
        }
    }
    @Override public Object onRetainCustomNonConfigurationInstance() {
        PlaybackState state = new PlaybackState();
        state.all = allPictures; state.remaining = picturesRemaining;
        state.starts = randomPageStarts; state.counts = randomPageCounts;
        state.index = imageIndex; state.mode = pagePlanMode; return state;
    }
    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out); out.putBoolean("pause", pause);
        out.putBoolean("ambientDark", controls != null && controls.ambientDark());
        if (imageIndex >= 0 && imageIndex < picturesRemaining.size()) out.putString("resumeSource", picturesRemaining.get(imageIndex));
        out.putInt("videoPosition", showingVideo ? video.position() : 0);
        // Large libraries stay in the retained state, never in a Binder-sized Bundle.
    }
    @Override protected void onResume() { super.onResume(); if (!AppLanguage.code(this).equals(activeLanguage)) { recreate(); return; } if (controls != null) controls.start(); statusBarGuard.start(this); }
    @Override protected void onPause() { statusBarGuard.stop(); if (controls != null) controls.stop(); super.onPause(); }
    @Override public void onWindowFocusChanged(boolean focus) { super.onWindowFocusChanged(focus); if (focus) updateFlagsAndStuff(); }
    @Override public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (photoLoader != null && level != TRIM_MEMORY_UI_HIDDEN && level >= TRIM_MEMORY_RUNNING_LOW) photoLoader.trim();
    }
    @Override protected void onDestroy() {
        statusBarGuard.stop();
        if (controls != null) controls.close();
        photoRequest++; scanSerial++; mediaScanner.shutdownNow();
        updateImageHandler.removeCallbacksAndMessages(null); ntpHandler.removeCallbacksAndMessages(null);
        if (ntpTask != null) ntpTask.cancel(true);
        video.stop(); image.clearPhoto(); image.setImageDrawable(null); photoLoader.suspend();
        if (keepAlive.isHeld()) keepAlive.release(); super.onDestroy();
    }
    private String[] mediaPermissions() {
        if (Build.VERSION.SDK_INT >= 33 && getApplicationInfo().targetSdkVersion >= 33)
            return new String[]{"android.permission.READ_MEDIA_IMAGES", "android.permission.READ_MEDIA_VIDEO"};
        if (Build.VERSION.SDK_INT <= 28) return new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE};
        return new String[]{Manifest.permission.READ_EXTERNAL_STORAGE};
    }
    private boolean hasMediaPermission() {
        if (Build.VERSION.SDK_INT < 23) return true;
        for (String permission : mediaPermissions())
            if (!Manifest.permission.WRITE_EXTERNAL_STORAGE.equals(permission)
                    && ActivityCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) return true;
        return false;
    }
    private void requestMediaPermissions() {
        if (Build.VERSION.SDK_INT >= 23 && !hasMediaPermission()) ActivityCompat.requestPermissions(this, mediaPermissions(), REQUEST_EXTERNAL_STORAGE);
    }
    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if (request != REQUEST_EXTERNAL_STORAGE) return;
        if (hasMediaPermission()) { loadConfigFile(); requestMediaScan(true); }
        else if (controls != null) controls.setPhoto(MainActivity.this.getString(R.string.text_photo_video_access_is_missing_grant_permission_in_app_settings));
    }
    void applySettings() {
        SharedPreferences prefs = FramePreferences.get(this);
        imageUpdateTime = Math.max(3, prefs.getInt("imageTime", imageUpdateTime));
        int mode = FramePreferences.photoCount(this);
        randomPhotoCount = mode == 0; configuredPhotoCount = randomPhotoCount ? 1 : mode;
        if (pagePlanMode != mode) { randomPageStarts.clear(); randomPageCounts.clear(); }
        ensureRandomPages();
        if (imageIndex >= 0 && !randomPageStarts.isEmpty()) imageIndex = randomPageStarts.get(randomPageIndex(imageIndex));
        photosPerPage = countAt(imageIndex);
        cropPhotos = prefs.getBoolean("crop", false); blurPhotos = prefs.getBoolean("blur", true);
        transitionEffect = prefs.getString("effect", "fade"); transitionDuration = prefs.getInt("effectDuration", 600);
        boolean shuffle = prefs.getBoolean("shuffle", shuffleImages);
        if (shuffle != shuffleImages) { shuffleImages = shuffle; refillRemainingPictures(); imageIndex = picturesRemaining.isEmpty() ? -1 : 0; }
        long revision = prefs.getLong("cacheRevision", 0);
        if (revision != cacheRevision) { photoLoader.clear(); cacheRevision = revision; }
        photoLoader.configure();
        String sources = prefs.getString("mediaSource", "all") + ':' + prefs.getBoolean("includeVideos", true) + ':' + prefs.getLong("mediaRevision", 0);
        if (!sources.equals(sourceSettings)) { sourceSettings = sources; unreadableMedia.clear(); requestMediaScan(true); }
    }
    void resumeFrame() {
        if (!keepAlive.isHeld()) keepAlive.acquire();
        loadConfigFile(); requestMediaScan(false);
        if (!hasMediaPermission()) { showNoPicturesIcon(); controls.setPhoto(MainActivity.this.getString(R.string.text_grant_photo_video_access_to_open_your_library)); return; }
        if (imageIndex >= 0 && imageIndex < picturesRemaining.size()) showPictureAsync(picturesRemaining.get(imageIndex));
        else updateImage();
        delayImageUpdate(); ntpHandler.removeCallbacks(ntpRunnable); ntpHandler.postDelayed(ntpRunnable, ntpUpdateTime * 1000L);
    }
    void suspendFrame() {
        updateImageHandler.removeCallbacksAndMessages(null); ntpHandler.removeCallbacksAndMessages(null);
        if (ntpTask != null) ntpTask.cancel(true);
        photoRequest++; photoLoader.suspend(); image.finishTransition(); video.setPaused(true);
        if (scanJob != null && scanning) { scanJob.cancel(true); scanSerial++; scanning = false; lastScanAt = 0; }
        if (keepAlive.isHeld()) keepAlive.release();
    }
    void showPauseIcon(boolean ignored) { findViewById(R.id.pauseIcon).setVisibility(View.GONE); }
    void pauseUnpauseImages() {
        if (controls.blocked()) return;
        pause = !pause; showPauseIcon(pause);
        if (showingVideo) video.setPaused(pause || !active);
        controls.refreshPlayback();
    }
    void delayImageUpdate() {
        updateImageHandler.removeCallbacks(updateImageRunnable);
        if (controls != null) controls.resetCountdown();
        if (!pause && active && !showingVideo && (controls == null || !controls.blocked())) updateImageHandler.postDelayed(updateImageRunnable, imageUpdateTime * 1000L);
    }
    void loadImageList() { requestMediaScan(false); }
    private void requestMediaScan(boolean force) {
        if (isDestroyed() || !hasMediaPermission() || mediaScanner.isShutdown()) return;
        long now = android.os.SystemClock.elapsedRealtime();
        if (!force && (scanning || lastScanAt > 0 && now - lastScanAt < 60000)) return;
        if (scanJob != null) scanJob.cancel(true);
        int serial = ++scanSerial; scanning = true; lastScanAt = now;
        SharedPreferences prefs = FramePreferences.get(this);
        boolean whole = "all".equals(prefs.getString("mediaSource", "all")), videos = prefs.getBoolean("includeVideos", true);
        scanJob = mediaScanner.submit(() -> {
            List<String> sources = MediaLibrary.scan(getApplicationContext(), whole, videos, imageFolder);
            if (Thread.currentThread().isInterrupted()) return;
            runOnUiThread(() -> {
                if (isDestroyed() || serial != scanSerial) return;
                scanning = false;
                if (allPictures.equals(sources) && !picturesRemaining.isEmpty()) return;
                String current = imageIndex >= 0 && imageIndex < picturesRemaining.size() ? picturesRemaining.get(imageIndex) : resumeSource;
                allPictures = sources; unreadableMedia.retainAll(sources); refillRemainingPictures();
                int found = current == null ? -1 : picturesRemaining.indexOf(current);
                imageIndex = found >= 0 ? randomPageStarts.get(randomPageIndex(found)) : picturesRemaining.isEmpty() ? -1 : 0;
                resumeSource = null; photosPerPage = countAt(imageIndex);
                if (controls != null && !controls.blocked()) {
                    if (imageIndex >= 0) showPictureAsync(picturesRemaining.get(imageIndex)); else showNoPicturesIcon();
                    delayImageUpdate();
                }
            });
        });
    }
    void refillRemainingPictures() {
        picturesRemaining = new ArrayList<>(allPictures); picturesRemaining.removeAll(unreadableMedia);
        if (shuffleImages) Collections.shuffle(picturesRemaining); else Collections.sort(picturesRemaining, String::compareToIgnoreCase);
        randomPageStarts.clear(); randomPageCounts.clear(); ensureRandomPages();
    }
    void updateImage() {
        if (controls != null && controls.blocked()) return;
        loadConfigFile(); updateFlagsAndStuff();
        imageIndex = imageIndex < 0 ? 0 : imageIndex + countAt(imageIndex);
        if (imageIndex >= picturesRemaining.size()) { requestMediaScan(false); refillRemainingPictures(); imageIndex = 0; }
        photosPerPage = countAt(imageIndex);
        if (imageIndex < picturesRemaining.size()) showPictureAsync(picturesRemaining.get(imageIndex)); else showNoPicturesIcon();
    }
    void goToNextImage() { transitionDirection = 1; updateImage(); if (!pause) delayImageUpdate(); }
    void goToPreviousImage() {
        if (controls.blocked()) return;
        if (picturesRemaining.isEmpty()) { goToNextImage(); return; }
        transitionDirection = -1; imageIndex = adjacentPage(-1); photosPerPage = countAt(imageIndex);
        showPictureAsync(picturesRemaining.get(imageIndex)); if (!pause) delayImageUpdate();
    }
    void ensureRandomPages() {
        int size = picturesRemaining.size(), mode = randomPhotoCount ? 0 : configuredPhotoCount;
        if (pagePlanMode == mode && !randomPageStarts.isEmpty()) {
            int last = randomPageStarts.size() - 1;
            if (randomPageStarts.get(last) + randomPageCounts.get(last) == size) return;
        }
        pagePlanMode = mode; randomPageStarts.clear(); randomPageCounts.clear();
        java.util.Random random = new java.util.Random();
        for (int start = 0; start < size;) {
            int wanted = randomPhotoCount ? (random.nextInt(10) < 6 ? 1 : 2 + random.nextInt(3)) : configuredPhotoCount;
            int count = 1;
            if (!MediaLibrary.isVideo(picturesRemaining.get(start)))
                while (count < wanted && start + count < size && !MediaLibrary.isVideo(picturesRemaining.get(start + count))) count++;
            randomPageStarts.add(start); randomPageCounts.add(count); start += count;
        }
    }
    int randomPageIndex(int start) {
        for (int i = randomPageStarts.size() - 1; i >= 0; i--) if (start >= randomPageStarts.get(i)) return i;
        return 0;
    }
    int countAt(int start) { ensureRandomPages(); return randomPageCounts.isEmpty() ? 1 : randomPageCounts.get(randomPageIndex(start)); }
    int adjacentPage(int offset) {
        ensureRandomPages(); int pages = randomPageStarts.size(); if (pages == 0) return 0;
        return randomPageStarts.get((randomPageIndex(imageIndex) + offset % pages + pages) % pages);
    }
    List<String> pagePaths(int start) {
        List<String> paths = new ArrayList<>(); if (start < 0) return paths;
        for (int i = 0; i < countAt(start) && start + i < picturesRemaining.size(); i++) paths.add(picturesRemaining.get(start + i));
        return paths;
    }
    private void stopVideo() { showingVideo = false; video.stop(); video.setVisibility(View.GONE); image.setVisibility(View.VISIBLE); }
    void showNoPicturesIcon() {
        photoRequest++; stopVideo(); image.clearPhoto(); image.setImageResource(R.drawable.no_images);
        if (controls != null) controls.setPhoto(scanning ? MainActivity.this.getString(R.string.text_searching_for_photos_and_videos) : MainActivity.this.getString(R.string.text_no_readable_media_found_grant_permission_or_rescan_the_library));
    }
    void showBrokenImageIcon() { stopVideo(); image.clearPhoto(); image.setImageResource(R.drawable.broken_image); }
    private void failedPage(List<String> sources, int request) {
        unreadableMedia.addAll(sources); showBrokenImageIcon();
        controls.setPhoto(MainActivity.this.getString(R.string.text_unsupported_file_skipping));
        updateImageHandler.removeCallbacks(updateImageRunnable);
        if (!pause && !controls.blocked()) updateImageHandler.postDelayed(() -> {
            if (photoRequest == request && !pause && !controls.blocked()) goToNextImage();
        }, 1000);
    }
    private void showVideo(String source, int request) {
        photoLoader.suspend(); showingVideo = true; image.clearPhoto(); image.setVisibility(View.INVISIBLE); video.setVisibility(View.VISIBLE);
        updateImageHandler.removeCallbacks(updateImageRunnable); controls.setPhoto("Video");
        video.show(source, !FramePreferences.get(this).getBoolean("videoSound", false), pause || !active || controls.blocked(), new FrameVideoView.Listener() {
            @Override public void prepared(int width, int height, int duration) {
                if (photoRequest == request && !isDestroyed()) controls.setPhoto(getString(R.string.video_info, width, height, duration / 1000));
            }
            @Override public void completed() { if (photoRequest == request && !pause && !controls.blocked()) goToNextImage(); }
            @Override public void failed() { if (photoRequest == request) failedPage(Collections.singletonList(source), request); }
        });
        if (videoResumePosition > 0) { video.seekTo(videoResumePosition); videoResumePosition = 0; }
    }
    void showPictureAsync(String source) {
        int request = ++photoRequest; photosPerPage = countAt(imageIndex);
        if (MediaLibrary.isVideo(source)) {
            if (FramePreferences.get(this).getBoolean("includeVideos", true)) showVideo(source, request);
            else failedPage(Collections.singletonList(source), request);
            return;
        }
        stopVideo();
        final int direction = transitionDirection; transitionDirection = 1;
        final List<String> paths = pagePaths(imageIndex);
        photoLoader.loadPage(paths, frameWidth, frameHeight, cropPhotos, blurPhotos, result -> {
            if (photoRequest != request || isFinishing() || isDestroyed() || controls.blocked()) return;
            if (result == null) { failedPage(paths, request); return; }
            image.show(result, transitionEffect, transitionDuration, direction, cropPhotos, blurPhotos);
            controls.setPhoto(result.info); delayImageUpdate();
            int pages = randomPageStarts.size();
            if (pages > 1) {
                prefetchPage(adjacentPage(-1));
                int ahead = Math.min(photoLoader.preloadPages(randomPhotoCount ? 4 : configuredPhotoCount, frameWidth, frameHeight), pages - 1);
                for (int i = 1; i <= ahead; i++) prefetchPage(adjacentPage(i));
            }
        });
    }
    private void prefetchPage(int index) {
        List<String> paths = pagePaths(index);
        if (!paths.isEmpty() && !MediaLibrary.isVideo(paths.get(0))) photoLoader.prefetchPage(paths, frameWidth, frameHeight, cropPhotos, blurPhotos);
    }
    void rotatePicture(boolean clockwise) {
        if (controls.blocked() || imageIndex < 0 || imageIndex >= picturesRemaining.size() || countAt(imageIndex) != 1) return;
        String source = picturesRemaining.get(imageIndex);
        if (MediaLibrary.isVideo(source) || source.startsWith("content:")) return;
        try {
            ExifInterface exif = new ExifInterface(source); exif.rotate(clockwise ? 90 : -90); exif.saveAttributes();
            photoLoader.clear(); showPictureAsync(source);
        } catch (Exception e) { Log.w(TAG, "Photo rotation unavailable", e); }
    }
    void refreshSchedule() { updateFlagsAndStuff(); }
    void updateFlagsAndStuff() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        SharedPreferences prefs = FramePreferences.get(this);
        int start = prefs.getInt("scheduleStart", activeHourStart), end = prefs.getInt("scheduleEnd", activeHourEnd);
        boolean next = !prefs.getBoolean("useSchedule", false) || start == end || (start < end
                ? hour >= start && hour < end : hour >= start || hour < end);
        if (next || controls != null && controls.nightMode()) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        boolean changed = active != next;
        if (next && !active && (controls == null || !controls.blocked())) turnScreenOn();
        active = next;
        if (changed && controls != null && !controls.blocked()) {
            if (showingVideo) video.setPaused(pause || !active);
            delayImageUpdate();
        }
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        if (getSupportActionBar() != null) getSupportActionBar().hide();
    }
    @SuppressWarnings("deprecation")
    @SuppressLint("WakelockTimeout")
    private void turnScreenOn() {
        PowerManager manager = (PowerManager) getSystemService(POWER_SERVICE);
        PowerManager.WakeLock screen = manager.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, TAG + ":screen");
        screen.acquire(1000); screen.release();
    }
    private void loadConfigFile() {
        if (!hasMediaPermission()) return;
        File file = new File(imageFolder, "config.json");
        try {
            if (!file.exists()) {
                if (Build.VERSION.SDK_INT >= 29) return;
                new File(imageFolder).mkdirs();
                JSONObject initial = new JSONObject();
                initial.put("imageTime", 60); initial.put("shuffle", true); initial.put("showFileNames", false);
                initial.put("activeHourStart", 7); initial.put("activeHourEnd", 23);
                initial.put("ntpServer", "pool.ntp.org"); initial.put("forceClockSync", false); initial.put("ntpSyncInterval", 3600);
                initial.put("fileTypes", new JSONArray(new String[]{".jpg", ".jpeg", ".png", ".webp", ".gif", ".bmp"}));
                initial.put("subdirectories", new JSONArray());
                try (FileWriter writer = new FileWriter(file)) { writer.write(initial.toString(4)); }
            }
            if (file.lastModified() == configModified) return;
            StringBuilder json = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) { String line; while ((line = reader.readLine()) != null) json.append(line); }
            JSONObject config = new JSONObject(json.toString());
            SharedPreferences prefs = FramePreferences.get(this);
            imageUpdateTime = Math.max(3, prefs.getInt("imageTime", config.optInt("imageTime", 60)));
            shuffleImages = prefs.getBoolean("shuffle", config.optBoolean("shuffle", true));
            activeHourStart = Math.max(0, Math.min(23, config.optInt("activeHourStart", 7)));
            activeHourEnd = Math.max(0, Math.min(23, config.optInt("activeHourEnd", 23)));
            ntpServer = config.optString("ntpServer", "pool.ntp.org"); forceClockSync = config.optBoolean("forceClockSync", false);
            ntpUpdateTime = Math.max(60, config.optInt("ntpSyncInterval", 3600)); configModified = file.lastModified();
        } catch (Exception e) { Log.w(TAG, "Legacy config is unavailable; using saved settings", e); }
    }
    @SuppressLint("StaticFieldLeak")
    private class ClockTask extends AsyncTask<Void, Void, Date> {
        @Override protected Date doInBackground(Void... unused) {
            SntpClient client = new SntpClient();
            return client.requestTime(ntpServer, 30000) && !isCancelled() ? new Date(client.getNtpTime()) : null;
        }
        @Override protected void onPostExecute(Date date) {
            if (date == null || isCancelled() || isDestroyed() || controls.blocked() || !forceClockSync) return;
            try {
                Process process = Runtime.getRuntime().exec("su");
                try (DataOutputStream stream = new DataOutputStream(process.getOutputStream())) {
                    stream.writeBytes("date " + new SimpleDateFormat("MMddHHmmyy.ss", Locale.US).format(date) + "\nexit\n"); stream.flush();
                }
            } catch (Exception e) { Log.w(TAG, "Root clock sync unavailable", e); }
        }
    }
}
