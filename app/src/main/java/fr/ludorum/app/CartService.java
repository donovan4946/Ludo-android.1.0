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

    private static final long AMOUNT_CACHE_MS = 650L;

    private static volatile long lastAmountAt = 0L;
    private static volatile long lastAmount = 0L;
    private static volatile int lastMinorUnit = 2;
    private static volatile String lastCurrencyCode = "EUR";

    private static void invalidateAmountCache() {
        lastAmountAt = 0L;
    }

    interface Callback {
        void onSuccess(int itemsCount);
        void onError(String message);
    }

    interface AmountCallback {
        void onResult(
                long minorAmount,
                int minorUnit,
                String currencyCode
        );
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
                connection.setRequestProperty("User-Agent", "LudorumAndroid/1.0.5");

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
                    invalidateAmountCache();

                    // La réponse AJAX contient fragments/cart_hash :
                    // l'ajout WooCommerce est déjà confirmé.
                    // On ne bloque plus l'interface avec un second GET.
                    callback.onSuccess(-1);
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

    static void getProductsTtc(
            AmountCallback callback
    ) {
        long now =
                System.currentTimeMillis();

        if (now - lastAmountAt <= AMOUNT_CACHE_MS) {
            long amount = lastAmount;
            int minorUnit = lastMinorUnit;
            String currencyCode = lastCurrencyCode;

            MAIN.post(
                    () -> callback.onResult(
                            amount,
                            minorUnit,
                            currencyCode
                    )
            );
            return;
        }

        CookieManager cookieManager =
                CookieManager.getInstance();

        String cookies =
                cookieManager.getCookie(BASE);

        EXECUTOR.execute(() -> {
            HttpURLConnection connection = null;

            long amount = 0L;
            int minorUnit = 2;
            String currencyCode = "EUR";

            try {
                connection =
                        (HttpURLConnection)
                                new URL(CART_API)
                                        .openConnection();

                connection.setConnectTimeout(7000);
                connection.setReadTimeout(9000);
                connection.setRequestMethod("GET");
                connection.setUseCaches(true);
                connection.setRequestProperty(
                        "Accept",
                        "application/json"
                );
                connection.setRequestProperty(
                        "Connection",
                        "keep-alive"
                );
                connection.setRequestProperty(
                        "User-Agent",
                        "LudorumAndroid/1.0.5"
                );

                if (cookies != null &&
                        !cookies.trim().isEmpty()) {
                    connection.setRequestProperty(
                            "Cookie",
                            cookies
                    );
                }

                int status =
                        connection.getResponseCode();

                if (status >= 200 &&
                        status < 300) {
                    JSONObject cart =
                            new JSONObject(
                                    read(
                                            connection.getInputStream()
                                    )
                            );

                    JSONObject totals =
                            cart.optJSONObject("totals");

                    if (totals != null) {
                        long items =
                                parseMinor(
                                        totals.optString(
                                                "total_items",
                                                "0"
                                        )
                                );

                        long itemsTax =
                                parseMinor(
                                        totals.optString(
                                                "total_items_tax",
                                                "0"
                                        )
                                );

                        // Total TTC des PRODUITS uniquement.
                        // Les frais de livraison ne sont jamais ajoutés ici.
                        amount =
                                Math.max(
                                        0L,
                                        items + itemsTax
                                );

                        minorUnit =
                                totals.optInt(
                                        "currency_minor_unit",
                                        2
                                );

                        currencyCode =
                                totals.optString(
                                        "currency_code",
                                        "EUR"
                                );
                    }
                }

            } catch (Exception ignored) {
                amount = 0L;
                minorUnit = 2;
                currencyCode = "EUR";

            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }

            lastAmount = amount;
            lastMinorUnit = minorUnit;
            lastCurrencyCode = currencyCode;
            lastAmountAt = System.currentTimeMillis();

            final long finalAmount = amount;
            final int finalMinorUnit = minorUnit;
            final String finalCurrencyCode = currencyCode;

            MAIN.post(
                    () -> callback.onResult(
                            finalAmount,
                            finalMinorUnit,
                            finalCurrencyCode
                    )
            );
        });
    }

    private static long parseMinor(
            String raw
    ) {
        try {
            return Long.parseLong(
                    raw == null ||
                    raw.trim().isEmpty()
                            ? "0"
                            : raw.trim()
            );
        } catch (Exception ignored) {
            return 0L;
        }
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
            connection.setRequestProperty("User-Agent", "LudorumAndroid/1.0.5");

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
