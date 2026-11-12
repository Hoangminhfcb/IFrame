package nuts.deez.photoframeslideshow;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.animation.DecelerateInterpolator;
import androidx.appcompat.widget.AppCompatImageView;
import java.util.Random;

/** Keeps only two frame references during an animation; no screenshot allocations per transition. */
public class PhotoFrameView extends AppCompatImageView {
    private PhotoLoader.Photo current, previous;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF bounds = new RectF();
    private ValueAnimator animator;
    private float fraction = 1;
    private String effect = "fade";
    private int direction = 1;
    private boolean crop, blur = true;
    public PhotoFrameView(Context c, AttributeSet attrs) { super(c, attrs); }
    void show(PhotoLoader.Photo next, String transition, int duration, int dir, boolean fill, boolean blurred) {
        finishTransition();
        previous = current;
        current = next; crop = fill; blur = blurred; direction = dir;
        effect = "random".equals(transition) ? new String[]{"fade", "slide", "zoom"}[new Random().nextInt(3)] : transition;
        if (previous == null || previous == current || "none".equals(effect)) {
            previous = null; fraction = 1; invalidate(); return;
        }
        animator = ValueAnimator.ofFloat(0, 1);
        animator.setDuration(duration);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(a -> { fraction = (float) a.getAnimatedValue(); invalidate(); });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator a) { previous = null; fraction = 1; invalidate(); }
        });
        animator.start();
    }
    void finishTransition() {
        if (animator != null) { animator.cancel(); animator = null; }
        previous = null; fraction = 1; invalidate();
    }
    void clearPhoto() { finishTransition(); current = null; invalidate(); }
    @Override protected void onDetachedFromWindow() { finishTransition(); super.onDetachedFromWindow(); }
    @Override protected void onDraw(Canvas canvas) {
        if (current == null) { super.onDraw(canvas); return; }
        canvas.drawColor(android.graphics.Color.BLACK);
        bounds.set(0, 0, getWidth(), getHeight());
        if (previous == null) { PhotoRenderer.draw(canvas, current, bounds, paint, 255, crop, blur); return; }
        if ("slide".equals(effect)) {
            canvas.save(); canvas.translate(-direction * fraction * getWidth(), 0);
            PhotoRenderer.draw(canvas, previous, bounds, paint, 255, crop, blur); canvas.restore();
            canvas.save(); canvas.translate(direction * (1 - fraction) * getWidth(), 0);
            PhotoRenderer.draw(canvas, current, bounds, paint, 255, crop, blur); canvas.restore();
        } else {
            PhotoRenderer.draw(canvas, previous, bounds, paint, 255, crop, blur);
            canvas.save();
            if ("zoom".equals(effect)) {
                float scale = .94f + .06f * fraction;
                canvas.scale(scale, scale, getWidth() / 2f, getHeight() / 2f);
            }
            PhotoRenderer.draw(canvas, current, bounds, paint, Math.round(255 * fraction), crop, blur);
            canvas.restore();
        }
    }
}
