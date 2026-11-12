package nuts.deez.photoframeslideshow;

import android.content.Context;
import android.content.ContentUris;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Public media only: MediaStore plus readable legacy storage, scanned off the UI thread. */
final class MediaLibrary {
    static boolean isVideo(String source) {
        if (source.startsWith("content:")) return Uri.parse(source).getPath().contains("/video/");
        return extension(source, ".mp4", ".m4v", ".3gp", ".3gpp", ".webm", ".mkv", ".mov", ".avi", ".mpg", ".mpeg", ".ts", ".m2ts");
    }
    private static boolean extension(String source, String... types) {
        String name = source.toLowerCase(Locale.ROOT);
        for (String type : types) if (name.endsWith(type)) return true;
        return false;
    }
    static boolean isImage(String path) {
        return extension(path, ".jpg", ".jpeg", ".jpe", ".jfif", ".png", ".webp", ".gif", ".bmp", ".wbmp",
                ".heic", ".heif", ".avif", ".tif", ".tiff", ".dng", ".arw", ".cr2", ".cr3", ".nef", ".orf", ".rw2", ".raf", ".pef", ".ico");
    }
    static Uri uri(String source) {
        return source.startsWith("content:") ? Uri.parse(source).buildUpon().fragment(null).build() : Uri.fromFile(new File(source));
    }
    static InputStream open(Context context, String source) throws IOException {
        InputStream stream = source.startsWith("content:") ? context.getContentResolver().openInputStream(uri(source)) : new FileInputStream(source);
        if (stream == null) throw new IOException("Media is no longer available");
        return stream;
    }
    static long size(String source) {
        if (!source.startsWith("content:")) return new File(source).length();
        String fragment = Uri.parse(source).getFragment();
        if (fragment != null) try { return Long.parseLong(fragment.substring(fragment.indexOf('_') + 1)); } catch (RuntimeException ignored) {}
        return 0;
    }
    static List<String> scan(Context context, boolean wholeDevice, boolean videos, String picturesFolder) {
        LinkedHashSet<String> sources = new LinkedHashSet<>();
        if (wholeDevice) {
            query(context, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, sources);
            if (videos) query(context, MediaStore.Video.Media.EXTERNAL_CONTENT_URI, sources);
        }
        // Direct reads cover unindexed photos and removable cards on older photo frames.
        // Newer Android versions use MediaStore content URIs under scoped storage.
        if (Build.VERSION.SDK_INT <= 28 || !wholeDevice) {
            Set<String> visited = new HashSet<>();
            if (!wholeDevice) walk(new File(picturesFolder), videos, sources, visited, 0);
            else {
                walk(Environment.getExternalStorageDirectory(), videos, sources, visited, 0);
                File[] volumes = new File("/storage").listFiles();
                if (volumes != null) for (File volume : volumes)
                    if (!volume.getName().equals("self") && !volume.getName().equals("emulated")) walk(volume, videos, sources, visited, 0);
            }
        }
        List<String> result = new ArrayList<>(sources);
        java.util.Collections.sort(result, String::compareToIgnoreCase);
        return result;
    }
    private static void query(Context context, Uri collection, Set<String> result) {
        String[] projection = {"_id", "_data", "date_modified", "_size"};
        try (Cursor cursor = context.getContentResolver().query(collection, projection, null, null, "_id ASC")) {
            if (cursor == null) return;
            while (cursor.moveToNext() && !Thread.currentThread().isInterrupted()) {
                String path = cursor.getString(1);
                if (Build.VERSION.SDK_INT <= 28 && path != null && new File(path).canRead()
                        && (!collection.getPath().contains("/video/") || isVideo(path))) {
                    result.add(new File(path).getCanonicalPath());
                } else {
                    Uri uri = ContentUris.withAppendedId(collection, cursor.getLong(0));
                    result.add(uri.buildUpon().fragment(cursor.getLong(2) + "_" + cursor.getLong(3)).build().toString());
                }
            }
        } catch (Exception e) { Log.w("PhotoFrameSlideshow", "Media collection unavailable", e); }
    }
    private static void walk(File entry, boolean videos, Set<String> result, Set<String> visited, int depth) {
        if (Thread.currentThread().isInterrupted() || depth > 30) return;
        try {
            String path = entry.getCanonicalPath();
            if (!visited.add(path)) return;
            if (entry.isFile()) {
                if (entry.canRead() && (isImage(path) || videos && isVideo(path))) result.add(path);
                return;
            }
            if (path.endsWith("/Android/data") || path.endsWith("/Android/obb")) return;
            File[] children = entry.listFiles();
            if (children != null) for (File child : children) walk(child, videos, result, visited, depth + 1);
        } catch (IOException | SecurityException ignored) {}
    }
}
