package nuts.deez.photoframeslideshow;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.content.Context;
import android.app.ActivityManager;
import java.util.List;
import java.util.ArrayList;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.LruCache;
import androidx.exifinterface.media.ExifInterface;
import java.io.File;
import java.io.InputStream;
import java.util.Locale;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Memory-bounded asynchronous photo decoding and soft background generation for API 22. */
final class PhotoLoader {
    static final class Photo {
        final Bitmap photo, background;
        final String info;
        Photo(Bitmap photo, Bitmap background, String info) {
            this.photo = photo; this.background = background; this.info = info;
        }
    }
    interface Callback { void ready(Photo photo); }
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ThreadPoolExecutor worker = new ThreadPoolExecutor(1, 1, 0,
            TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
    private final LruCache<String, Photo> cache;
    private static PhotoLoader shared;
    static synchronized PhotoLoader shared(Context context) {
        if (shared == null) shared = new PhotoLoader(context);
        return shared;
    }
    private final Context context;
    private volatile boolean closed;
    private volatile int generation;
    private static volatile long usedBytes, budgetBytes;
    private static volatile int hits, misses;
    PhotoLoader(Context context) {
        this.context = context.getApplicationContext();
        cache = new LruCache<String, Photo>(budget()) {
            @Override protected int sizeOf(String key, Photo p) {
                return p.photo.getByteCount() + (p.background == null ? 0 : p.background.getByteCount());
            }
            // Views may reference evicted bitmaps: GC releases them after the transition ends.
        };
        stats();
    }
    private int budget() {
        long heap = Runtime.getRuntime().maxMemory();
        int percent = FramePreferences.get(context).getInt("cachePercent", 70);
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo memory = new ActivityManager.MemoryInfo();
        manager.getMemoryInfo(memory);
        long target = heap * Math.max(30, Math.min(70, percent)) / 100;
        // Retain room for both displayed pages, GPU uploads, decoder scratch and settings.
        target = Math.min(target, Math.max(8L << 20, heap - (64L << 20)));
        target = Math.min(target, Math.max(8L << 20, memory.availMem / 2));
        if (memory.lowMemory) target = Math.min(target, heap / 4);
        return (int) Math.max(8L << 20, Math.min(Integer.MAX_VALUE, target));
    }
    void configure() { cache.resize(budget()); stats(); }
    static String memorySummary(android.content.Context context) {
        return String.format(Locale.getDefault(), AppLanguage.wrap(context).getString(R.string.cache_summary),
                usedBytes / 1048576f, budgetBytes / 1048576f,
                Runtime.getRuntime().maxMemory() / 1048576f, hits, misses);
    }
    private void stats() { usedBytes = cache.size(); budgetBytes = cache.maxSize(); }
    private String key(String path, int w, int h) {
        if (path.startsWith("content:")) return AppLanguage.code(context) + ':' + path + ':' + w + 'x' + h;
        File f = new File(path);
        return AppLanguage.code(context) + ':' + path + ':' + f.lastModified() + ':' + f.length() + ':' + w + 'x' + h;
    }
    private String pageKey(List<String> paths, int w, int h, boolean crop, boolean blur) {
        StringBuilder key = new StringBuilder("page:").append(w).append('x').append(h).append(crop).append(blur);
        for (String path : paths) key.append('|').append(key(path, w, h));
        return key.toString();
    }
    void loadPage(List<String> paths, int w, int h, boolean crop, boolean blur, Callback callback) {
        if (closed) return;
        worker.getQueue().clear();
        final int token = ++generation;
        Photo hit = cache.get(paths.size() == 1 ? key(paths.get(0), w, h) + blur : pageKey(paths, w, h, crop, blur));
        if (hit != null) { hits++; callback.ready(hit); return; }
        List<String> copy = new ArrayList<>(paths);
        worker.execute(() -> {
            Photo result = page(copy, w, h, crop, blur, token);
            if (!closed && token == generation) main.post(() -> {
                if (!closed && token == generation) callback.ready(result);
            });
        });
    }
    void prefetchPage(List<String> paths, int w, int h, boolean crop, boolean blur) {
        final int token = generation;
        if (!closed && cache.get(pageKey(paths, w, h, crop, blur)) == null) {
            List<String> copy = new ArrayList<>(paths);
            worker.execute(() -> { if (!closed && token == generation) page(copy, w, h, crop, blur, token); });
        }
    }
    int preloadPages(int count, int w, int h) {
        long estimate = Math.max(1, (long) w * h * 4 * 2);
        return Math.max(2, Math.min(24 / count, (int) (cache.maxSize() / estimate)));
    }
    private Photo page(List<String> paths, int w, int h, boolean crop, boolean blur, int token) {
        String key = pageKey(paths, w, h, crop, blur);
        Photo hit = cache.get(key);
        if (hit != null) { hits++; return hit; }
        if (paths.isEmpty()) return null;
        if (paths.size() == 1) {
            Photo photo = get(paths.get(0), w, h, blur, token);
            // Single pages share one cache entry, avoiding charging the same bitmap twice.
            return photo;
        }
        Bitmap composed = null;
        try {
            composed = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(composed);
            canvas.drawColor(android.graphics.Color.BLACK);
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
            RectF[] cells = PhotoRenderer.cells(paths.size(), w, h);
            StringBuilder info = new StringBuilder();
            int valid = 0;
            for (int i = 0; i < paths.size(); i++) {
                if (closed || token != generation) { composed.recycle(); return null; }
                Photo photo = get(paths.get(i), Math.round(cells[i].width()), Math.round(cells[i].height()), blur, token);
                if (photo != null) {
                    PhotoRenderer.draw(canvas, photo, cells[i], paint, 255, crop, blur);
                    if (info.length() > 0) info.append("\n");
                    info.append(i + 1).append(" · ").append(photo.info.replace('\n', ' '));
                    valid++;
                }
            }
            if (valid == 0 || closed || token != generation) { composed.recycle(); return null; }
            Photo result = new Photo(composed, null, info.toString());
            cache.put(key, result); stats(); return result;
        } catch (OutOfMemoryError error) {
            if (composed != null) composed.recycle();
            trim(); Log.w("PhotoFrameSlideshow", "Collage memory pressure", error); return null;
        }
    }
    private Photo get(String path, int w, int h, boolean blurred, int token) {
        String key = key(path, w, h) + blurred;
        Photo hit = cache.get(key);
        if (hit != null) { hits++; return hit; }
        misses++;
        try {
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            try (InputStream stream = MediaLibrary.open(context, path)) { BitmapFactory.decodeStream(stream, null, opts); }
            if (opts.outWidth <= 0 || opts.outHeight <= 0) return null;
            int originalW = opts.outWidth, originalH = opts.outHeight;
            int limit = Math.min(2048, Math.max(w, h));
            opts.inSampleSize = 1;
            while (Math.max(originalW, originalH) / opts.inSampleSize > limit * 1.5f) opts.inSampleSize *= 2;
            opts.inJustDecodeBounds = false;
            Bitmap bitmap;
            try (InputStream stream = MediaLibrary.open(context, path)) { bitmap = BitmapFactory.decodeStream(stream, null, opts); }
            if (bitmap == null) return null;
            if (closed || token != generation) { bitmap.recycle(); return null; }
            ExifInterface exif = null;
            // EXIF is optional: GIF/WebP/BMP and other decodable images must not fail without it.
            try (InputStream stream = MediaLibrary.open(context, path)) { exif = new ExifInterface(stream); }
            catch (Exception ignored) {}
            Matrix matrix = new Matrix();
            switch (exif == null ? 1 : exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)) {
                case 2: matrix.setScale(-1, 1); break;
                case 3: matrix.setRotate(180); break;
                case 4: matrix.setScale(1, -1); break;
                case 5: matrix.setRotate(90); matrix.postScale(-1, 1); break;
                case 6: matrix.setRotate(90); break;
                case 7: matrix.setRotate(-90); matrix.postScale(-1, 1); break;
                case 8: matrix.setRotate(-90); break;
                default: break;
            }
            if (!matrix.isIdentity()) {
                Bitmap rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
                if (rotated != bitmap) bitmap.recycle();
                bitmap = rotated;
            }
            String taken = exif == null ? null : exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL);
            if (taken == null && exif != null) taken = exif.getAttribute(ExifInterface.TAG_DATETIME);
            String date = taken == null ? AppLanguage.wrap(context).getString(R.string.text_no_capture_date) : taken.replaceFirst("^(\\d{4}):(\\d{2}):(\\d{2})", "$3/$2/$1");
            String info = date + "\n" + originalW + " × " + originalH + "  ·  "
                    + String.format(Locale.getDefault(), "%.1f MB", MediaLibrary.size(path) / 1048576f);
            String camera = exif == null ? null : exif.getAttribute(ExifInterface.TAG_MODEL);
            if (camera != null) info += "  ·  " + camera;
            Photo result = new Photo(bitmap, blurred ? blur(bitmap, token) : null, info);
            if (closed || token != generation) return null;
            cache.put(key, result); stats();
            return result;
        } catch (OutOfMemoryError e) {
            trim(); Log.w("PhotoFrameSlideshow", "Photo memory pressure", e); return null;
        } catch (Exception e) { Log.w("PhotoFrameSlideshow", "Cannot decode photo", e); return null; }
    }
    private Bitmap blur(Bitmap source, int token) {
        float scale = 160f / Math.max(source.getWidth(), source.getHeight());
        int w = Math.max(1, Math.round(source.getWidth() * scale));
        int h = Math.max(1, Math.round(source.getHeight() * scale));
        Bitmap small = Bitmap.createScaledBitmap(source, w, h, true);
        int[] pixels = new int[w * h], output = new int[w * h];
        small.getPixels(pixels, 0, w, 0, 0, w, h);
        for (int pass = 0; pass < 3; pass++) {
            if (closed || token != generation) break;
            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
                int r = 0, g = 0, b = 0;
                for (int k = -8; k <= 8; k++) {
                    int c = pixels[y * w + Math.max(0, Math.min(w - 1, x + k))];
                    r += (c >> 16) & 255; g += (c >> 8) & 255; b += c & 255;
                }
                output[y * w + x] = 0xff000000 | ((r / 17) << 16) | ((g / 17) << 8) | (b / 17);
            }
            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
                int r = 0, g = 0, b = 0;
                for (int k = -8; k <= 8; k++) {
                    int c = output[Math.max(0, Math.min(h - 1, y + k)) * w + x];
                    r += (c >> 16) & 255; g += (c >> 8) & 255; b += c & 255;
                }
                pixels[y * w + x] = 0xff000000 | ((r / 17) << 16) | ((g / 17) << 8) | (b / 17);
            }
        }
        Bitmap result = Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888);
        if (small != source) small.recycle();
        return result;
    }
    void suspend() { generation++; worker.getQueue().clear(); main.removeCallbacksAndMessages(null); }
    void clear() { cache.evictAll(); stats(); }
    void trim() { cache.resize(Math.max(8 << 20, cache.maxSize() / 2)); stats(); }
    void close() { closed = true; suspend(); worker.shutdownNow(); cache.evictAll(); }
}
