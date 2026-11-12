package nuts.deez.photoframeslideshow;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;

/** Shared geometry for the on-screen renderer and cached collage pages. */
final class PhotoRenderer {
    static RectF[] cells(int count, int w, int h) {
        float gap = Math.max(4, Math.min(w, h) * .006f), half = gap / 2;
        if (count <= 1) return new RectF[]{new RectF(0, 0, w, h)};
        if (count == 2) return w >= h
                ? new RectF[]{new RectF(0, 0, w / 2f - half, h), new RectF(w / 2f + half, 0, w, h)}
                : new RectF[]{new RectF(0, 0, w, h / 2f - half), new RectF(0, h / 2f + half, w, h)};
        if (count == 3) return w >= h
                ? new RectF[]{new RectF(0, 0, w * .64f - half, h), new RectF(w * .64f + half, 0, w, h / 2f - half), new RectF(w * .64f + half, h / 2f + half, w, h)}
                : new RectF[]{new RectF(0, 0, w, h * .64f - half), new RectF(0, h * .64f + half, w / 2f - half, h), new RectF(w / 2f + half, h * .64f + half, w, h)};
        return new RectF[]{new RectF(0, 0, w / 2f - half, h / 2f - half), new RectF(w / 2f + half, 0, w, h / 2f - half),
                new RectF(0, h / 2f + half, w / 2f - half, h), new RectF(w / 2f + half, h / 2f + half, w, h)};
    }
    static void draw(Canvas c, PhotoLoader.Photo photo, RectF bounds, Paint paint, int alpha, boolean crop, boolean blur) {
        c.save();
        c.clipRect(bounds);
        if (blur && photo.background != null) {
            bitmap(c, photo.background, bounds, paint, alpha, true);
            paint.setColor(Color.BLACK); paint.setAlpha(alpha * 85 / 255);
            c.drawRect(bounds, paint);
        }
        bitmap(c, photo.photo, bounds, paint, alpha, crop);
        c.restore();
    }
    private static void bitmap(Canvas c, android.graphics.Bitmap b, RectF bounds, Paint p, int alpha, boolean crop) {
        float sx = bounds.width() / b.getWidth(), sy = bounds.height() / b.getHeight();
        float scale = crop ? Math.max(sx, sy) : Math.min(sx, sy);
        float w = b.getWidth() * scale, h = b.getHeight() * scale;
        p.setColor(Color.WHITE); p.setAlpha(alpha);
        c.drawBitmap(b, null, new RectF(bounds.centerX() - w / 2, bounds.centerY() - h / 2,
                bounds.centerX() + w / 2, bounds.centerY() + h / 2), p);
    }
}
