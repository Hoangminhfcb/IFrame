package nuts.deez.photoframeslideshow;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;

/** Foreground-only top-edge touch guard for the Android 5.1 photo frame. */
final class StatusBarGuard {
    private View edge;
    private WindowManager windows;
    static boolean supported() { return Build.VERSION.SDK_INT < 26; }
    static boolean permissionGranted(Context context) { return Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(context); }
    @SuppressWarnings("deprecation")
    void start(Activity activity) {
        stop();
        if (!supported() || !FramePreferences.get(activity).getBoolean("blockNotificationShade", true) || !permissionGranted(activity)) return;
        windows = (WindowManager) activity.getSystemService(Context.WINDOW_SERVICE);
        int resource = activity.getResources().getIdentifier("status_bar_height", "dimen", "android");
        int height = resource > 0 ? activity.getResources().getDimensionPixelSize(resource)
                : Math.round(24 * activity.getResources().getDisplayMetrics().density);
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT, height,
                WindowManager.LayoutParams.TYPE_SYSTEM_ERROR,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START; params.x = 0; params.y = 0;
        params.setTitle("Photo frame notification gesture guard");
        View view = new View(activity); view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        view.setOnTouchListener((v, event) -> true);
        try { windows.addView(view, params); edge = view; }
        catch (RuntimeException e) { Log.w("PhotoFrameSlideshow", "Notification guard needs overlay permission", e); windows = null; }
    }
    void stop() {
        if (edge != null && windows != null) try { windows.removeViewImmediate(edge); } catch (RuntimeException ignored) {}
        edge = null; windows = null;
    }
    static void openPermission(Activity activity) {
        Intent intent = new Intent(Build.VERSION.SDK_INT >= 23 ? Settings.ACTION_MANAGE_OVERLAY_PERMISSION : Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + activity.getPackageName()));
        if (intent.resolveActivity(activity.getPackageManager()) != null) activity.startActivity(intent);
    }
}
