package nuts.deez.photoframeslideshow;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.util.AttributeSet;
import android.view.Surface;
import android.view.TextureView;

/** TextureView keeps clocks and the dark curtain above video on Android 5.1. */
public class FrameVideoView extends TextureView implements TextureView.SurfaceTextureListener {
    interface Listener { void prepared(int width, int height, int duration); void completed(); void failed(); }
    private MediaPlayer player;
    private final android.os.Handler handler = new android.os.Handler();
    private Surface surface;
    private String source;
    private Listener listener;
    private boolean ready, paused = true, muted = true;
    private int position, videoWidth, videoHeight;
    private final Runnable prepareTimeout = () -> {
        if (player != null && !ready) { release(); if (listener != null) listener.failed(); }
    };
    public FrameVideoView(Context context, AttributeSet attrs) { super(context, attrs); setSurfaceTextureListener(this); setOpaque(false); }
    void show(String source, boolean muted, boolean paused, Listener listener) {
        boolean same = source.equals(this.source);
        if (!same) { release(); position = 0; this.source = source; }
        this.listener = listener; this.muted = muted; this.paused = paused;
        if (player == null && isAvailable()) prepare();
        else if (ready) {
            player.setVolume(muted ? 0 : 1, muted ? 0 : 1); setPaused(paused);
            if (listener != null) listener.prepared(videoWidth, videoHeight, player.getDuration());
        }
    }
    private void prepare() {
        if (source == null || !isAvailable()) return;
        MediaPlayer media = new MediaPlayer(); player = media; ready = false;
        try {
            surface = new Surface(getSurfaceTexture()); media.setSurface(surface);
            media.setDataSource(getContext(), MediaLibrary.uri(source));
            media.setVolume(muted ? 0 : 1, muted ? 0 : 1);
            media.setOnPreparedListener(mp -> {
                if (player != mp) return;
                handler.removeCallbacks(prepareTimeout);
                ready = true; videoWidth = mp.getVideoWidth(); videoHeight = mp.getVideoHeight(); fit();
                if (position > 0) mp.seekTo(position);
                if (!paused) mp.start();
                if (listener != null) listener.prepared(videoWidth, videoHeight, mp.getDuration());
            });
            media.setOnCompletionListener(mp -> { if (player == mp && listener != null && !paused) listener.completed(); });
            media.setOnErrorListener((mp, what, extra) -> {
                if (player == mp) { release(); if (listener != null) listener.failed(); }
                return true;
            });
            media.prepareAsync(); handler.postDelayed(prepareTimeout, 20000);
        } catch (Exception e) { release(); if (listener != null) listener.failed(); }
    }
    void setPaused(boolean value) {
        paused = value;
        if (!ready || player == null) return;
        try { if (value) player.pause(); else player.start(); } catch (IllegalStateException ignored) {}
    }
    int duration() { try { return ready && player != null ? player.getDuration() : 0; } catch (IllegalStateException e) { return 0; } }
    int position() { try { return ready && player != null ? player.getCurrentPosition() : position; } catch (IllegalStateException e) { return position; } }
    void seekTo(int value) { position = Math.max(0, value); if (ready && player != null) player.seekTo(position); }
    void stop() { release(); source = null; listener = null; position = 0; }
    private void release() {
        handler.removeCallbacks(prepareTimeout);
        ready = false;
        if (player != null) { player.release(); player = null; }
        if (surface != null) { surface.release(); surface = null; }
    }
    private void fit() {
        if (videoWidth <= 0 || videoHeight <= 0 || getWidth() == 0 || getHeight() == 0) return;
        float scale = Math.min((float) getWidth() / videoWidth, (float) getHeight() / videoHeight);
        Matrix matrix = new Matrix();
        matrix.setScale(videoWidth * scale / getWidth(), videoHeight * scale / getHeight(), getWidth() / 2f, getHeight() / 2f);
        setTransform(matrix);
    }
    @Override public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) { prepare(); }
    @Override public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) { fit(); }
    @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) { position = position(); release(); return true; }
    @Override public void onSurfaceTextureUpdated(SurfaceTexture texture) {}
}
