package fr.ludorum.app;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class ImageLoader {
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(4);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static final LruCache<String, Bitmap> CACHE =
            new LruCache<String, Bitmap>(24 * 1024) {
                @Override
                protected int sizeOf(String key, Bitmap value) {
                    return value.getByteCount() / 1024;
                }
            };

    static void load(String url, ImageView view) {
        if (url == null || url.trim().isEmpty()) return;

        view.setTag(url);
        Bitmap cached = CACHE.get(url);

        if (cached != null) {
            view.setAlpha(1f);
            view.setImageBitmap(cached);
            return;
        }

        EXECUTOR.execute(() -> {
            HttpURLConnection connection = null;
            try {
                connection =
                        (HttpURLConnection) new URL(url).openConnection();

                connection.setConnectTimeout(10000);
                connection.setReadTimeout(12000);
                connection.setRequestProperty(
                        "User-Agent",
                        "LudorumAndroid/1.0.0"
                );

                try (InputStream input = connection.getInputStream()) {
                    Bitmap bitmap = BitmapFactory.decodeStream(input);
                    if (bitmap != null) CACHE.put(url, bitmap);

                    MAIN.post(() -> {
                        Object tag = view.getTag();
                        if (bitmap != null && url.equals(tag)) {
                            view.setAlpha(1f);
                            view.setImageBitmap(bitmap);
                        }
                    });
                }
            } catch (Exception ignored) {
                // Le placeholder Ludorum reste visible.
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }
}
