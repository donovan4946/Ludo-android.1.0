package fr.ludorum.app;

import android.os.Handler;
import android.os.Looper;
import android.webkit.CookieManager;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class CartService {
    private static final String BASE = "https://ludorum.fr";
    private static final String AJAX_ADD = BASE + "/?wc-ajax=add_to_cart";
    private static final String CART_API = BASE + "/wp-json/wc/store/v1/cart";

    private static final ExecutorService EXECUTOR =
            Executors.newSingleThreadExecutor();

    private static final Handler MAIN =
            new Handler(Looper.getMainLooper());

    interface Callback {
        void onSuccess(int itemsCount);
        void onError(String message);
    }

    static void addSimpleProduct(
            int productId,
            int quantity,
            Callback callback
    ) {
        CookieManager cookieManager =
                CookieManager.getInstance();

        String existingCookies =
                cookieManager.getCookie(BASE);

        EXECUTOR.execute(() -> {
            HttpURLConnection connection = null;

            try {
                connection =
                        (HttpURLConnection)
                                new URL(AJAX_ADD)
                                        .openConnection();

                connection.setConnectTimeout(12000);
                connection.setReadTimeout(16000);
                connection.setRequestMethod("POST");
                connection.setDoOutput(true);
                connection.setInstanceFollowRedirects(false);
                connection.setRequestProperty(
                        "Content-Type",
                        "application/x-www-form-urlencoded; charset=UTF-8"
                );
                connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty("X-Requested-With", "XMLHttpRequest");
                connection.setRequestProperty("User-Agent", "LudorumAndroid/1.0.0");

                if (existingCookies != null &&
                        !existingCookies.trim().isEmpty()) {
                    connection.setRequestProperty(
                            "Cookie",
                            existingCookies
                    );
                }

                String body =
                        "product_id=" +
                        URLEncoder.encode(
                                String.valueOf(productId),
                                "UTF-8"
                        ) +
                        "&quantity=" +
                        URLEncoder.encode(
                                String.valueOf(Math.max(1, quantity)),
                                "UTF-8"
                        );

                try (OutputStream output =
                             connection.getOutputStream()) {
                    output.write(
                            body.getBytes(StandardCharsets.UTF_8)
                    );
                }

                int status =
                        connection.getResponseCode();

                InputStream stream =
                        status >= 200 && status < 400
                                ? connection.getInputStream()
                                : connection.getErrorStream();

                String response = read(stream);

                List<String> setCookies =
                        collectSetCookies(
                                connection.getHeaderFields()
                        );

                if (status < 200 || status >= 300) {
                    throw new Exception(
                            "WooCommerce HTTP " + status
                    );
                }

                JSONObject json =
                        new JSONObject(response);

                if (json.optBoolean("error", false)) {
                    throw new Exception(
                            "Ce produit n’a pas pu être ajouté au panier."
                    );
                }

                if (!json.has("fragments") &&
                        !json.has("cart_hash")) {
                    throw new Exception(
                            "Réponse panier WooCommerce inattendue."
                    );
                }

                String verificationCookies =
                        mergeCookies(
                                existingCookies,
                                setCookies
                        );

                int itemsCount =
                        verifyCartCount(
                                verificationCookies
                        );

                MAIN.post(() -> {
                    for (String cookie : setCookies) {
                        try {
                            cookieManager.setCookie(
                                    BASE,
                                    cookie
                            );
                        } catch (Exception ignored) {}
                    }

                    cookieManager.flush();
                    callback.onSuccess(itemsCount);
                });

            } catch (Exception error) {
                String message =
                        error.getMessage();

                if (message == null ||
                        message.trim().isEmpty()) {
                    message =
                            "Ajout au panier impossible. Réessaie.";
                }

                final String finalMessage =
                        message;

                MAIN.post(
                        () -> callback.onError(
                                finalMessage
                        )
                );

            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    private static int verifyCartCount(
            String cookies
    ) {
        HttpURLConnection connection = null;

        try {
            connection =
                    (HttpURLConnection)
                            new URL(CART_API)
                                    .openConnection();

            connection.setConnectTimeout(9000);
            connection.setReadTimeout(12000);
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "LudorumAndroid/1.0.0");

            if (cookies != null &&
                    !cookies.trim().isEmpty()) {
                connection.setRequestProperty(
                        "Cookie",
                        cookies
                );
            }

            int status =
                    connection.getResponseCode();

            if (status < 200 || status >= 300) {
                return -1;
            }

            JSONObject cart =
                    new JSONObject(
                            read(
                                    connection.getInputStream()
                            )
                    );

            return cart.optInt("items_count", -1);

        } catch (Exception ignored) {
            return -1;

        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static List<String> collectSetCookies(
            Map<String, List<String>> headers
    ) {
        List<String> result =
                new ArrayList<>();

        if (headers == null) {
            return result;
        }

        for (Map.Entry<String, List<String>> entry :
                headers.entrySet()) {

            String key =
                    entry.getKey();

            if (key == null ||
                    !"set-cookie".equalsIgnoreCase(key) ||
                    entry.getValue() == null) {
                continue;
            }

            result.addAll(
                    entry.getValue()
            );
        }

        return result;
    }

    private static String mergeCookies(
            String existing,
            List<String> setCookies
    ) {
        StringBuilder result =
                new StringBuilder();

        if (existing != null &&
                !existing.trim().isEmpty()) {
            result.append(
                    existing.trim()
            );
        }

        for (String raw : setCookies) {
            if (raw == null ||
                    raw.trim().isEmpty()) {
                continue;
            }

            String pair =
                    raw.split(";", 2)[0]
                            .trim();

            if (pair.isEmpty()) {
                continue;
            }

            if (result.length() > 0) {
                result.append("; ");
            }

            result.append(pair);
        }

        return result.toString();
    }

    private static String read(
            InputStream input
    ) throws Exception {
        if (input == null) {
            return "";
        }

        try (BufferedReader reader =
                     new BufferedReader(
                             new InputStreamReader(
                                     input,
                                     StandardCharsets.UTF_8
                             )
                     )) {

            StringBuilder result =
                    new StringBuilder();

            String line;

            while ((line =
                            reader.readLine()) != null) {
                result.append(line);
            }

            return result.toString();
        }
    }
}
