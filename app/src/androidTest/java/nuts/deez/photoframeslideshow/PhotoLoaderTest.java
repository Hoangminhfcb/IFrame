package nuts.deez.photoframeslideshow;

import android.app.Instrumentation;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.RectF;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class PhotoLoaderTest {
    @Test public void collageGeometryCacheAndLatestRequest() throws Exception {
        Instrumentation ins = InstrumentationRegistry.getInstrumentation();
        Context context = ins.getTargetContext();
        File dir = new File(context.getCacheDir(), "collage-test");
        assertTrue(dir.isDirectory() || dir.mkdirs());
        List<String> paths = new ArrayList<>();
        int[] colors = {Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW};
        PhotoLoader loader = new PhotoLoader(context);
        try {
            for (int i = 0; i < colors.length; i++) {
                File file = new File(dir, "tile-" + i + ".png");
                Bitmap bitmap = Bitmap.createBitmap(120, 160, Bitmap.Config.ARGB_8888);
                bitmap.eraseColor(colors[i]);
                try (FileOutputStream out = new FileOutputStream(file)) { bitmap.compress(Bitmap.CompressFormat.PNG, 100, out); }
                bitmap.recycle(); paths.add(file.getAbsolutePath());
            }
            for (int count = 1; count <= 4; count++) {
                final List<String> group = new ArrayList<>(paths.subList(0, count));
                for (int orientation = 0; orientation < 2; orientation++) {
                    int w = orientation == 0 ? 600 : 400, h = orientation == 0 ? 400 : 600;
                    AtomicReference<PhotoLoader.Photo> result = new AtomicReference<>();
                    CountDownLatch ready = new CountDownLatch(1);
                    ins.runOnMainSync(() -> loader.loadPage(group, w, h, true, false, photo -> { result.set(photo); ready.countDown(); }));
                    assertTrue("decode completed", ready.await(10, TimeUnit.SECONDS));
                    PhotoLoader.Photo photo = result.get(); assertNotNull(photo);
                    if (count > 1) {
                        assertEquals(w, photo.photo.getWidth()); assertEquals(h, photo.photo.getHeight());
                        RectF[] cells = PhotoRenderer.cells(count, w, h);
                        for (int i = 0; i < count; i++) assertEquals("tile " + i, colors[i], photo.photo.getPixel((int) cells[i].centerX(), (int) cells[i].centerY()));
                    } else assertEquals(colors[0], photo.photo.getPixel(40, 40));
                    CountDownLatch cached = new CountDownLatch(1);
                    ins.runOnMainSync(() -> loader.loadPage(group, w, h, true, false, hit -> { assertSame(photo, hit); cached.countDown(); }));
                    assertTrue(cached.await(5, TimeUnit.SECONDS));
                }
            }
            loader.clear();
            AtomicInteger stale = new AtomicInteger();
            CountDownLatch latest = new CountDownLatch(1);
            ins.runOnMainSync(() -> {
                loader.loadPage(paths, 700, 500, true, true, photo -> stale.incrementAndGet());
                loader.loadPage(paths.subList(0, 2), 700, 500, true, false, photo -> { assertNotNull(photo); latest.countDown(); });
            });
            assertTrue(latest.await(10, TimeUnit.SECONDS)); ins.waitForIdleSync();
            assertEquals("stale decode cannot replace latest swipe", 0, stale.get());
        } finally {
            ins.runOnMainSync(loader::close);
            for (String path : paths) new File(path).delete();
            dir.delete();
        }
    }
}
