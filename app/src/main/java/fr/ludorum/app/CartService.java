package fr.ludorum.app;

import android.os.Handler;
import android.os.Looper;
import android.webkit.CookieManager;

import org.json.JSONObject;
import org.json.JSONArray;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class CartService {
    private static final String BASE =
            "https://ludorum.fr";

    private static final String CART_API =
            BASE +
            "/wp-json/wc/store/v1/cart";

    private static final String CART_ADD_API =
            CART_API +
            "/add-item";

    private static final ExecutorService EXECUTOR =
            Executors.newSingleThreadExecutor();

    private static final Handler MAIN =
            new Handler(Looper.getMainLooper());

    private static final long AMOUNT_CACHE_MS = 650L;

    private static volatile long lastAmountAt = 0L;
    private static volatile long lastAmount = 0L;
    private static volatile int lastMinorUnit = 2;
    private static volatile String lastCurrencyCode = "EUR";

    private static final Set<Integer> ADDING_PRODUCTS =
            Collections.synchronizedSet(
                    new HashSet<>()
            );

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
        if (productId <= 0) {
            MAIN.post(
                    () -> callback.onError(
                            "Produit invalide."
                    )
            );
            return;
        }

        // Protection supplémentaire contre deux vues du même produit
        // cliquées pendant que la première requête est encore en cours.
        if (!ADDING_PRODUCTS.add(productId)) {
            return;
        }

        final int requestedQuantity =
                Math.max(1, quantity);

        CookieManager cookieManager =
                CookieManager.getInstance();

        String cookies =
                cookieManager.getCookie(BASE);

        EXECUTOR.execute(() -> {
            try {
                CartSnapshot before =
                        getCartSnapshot(
                                cookies,
                                null
                        );

                String cartToken =
                        before.cartToken;

                if (cartToken == null ||
                        cartToken.trim().isEmpty()) {
                    throw new Exception(
                            "Session panier WooCommerce indisponible."
                    );
                }

                int beforeQuantity =
                        before.quantityForProduct(
                                productId
                        );

                int expectedQuantity =
                        beforeQuantity +
                        requestedQuantity;

                CartSnapshot after =
                        addStoreApiItem(
                                productId,
                                requestedQuantity,
                                cookies,
                                cartToken
                        );

                CartItemState state =
                        after.itemForProduct(
                                productId
                        );

                if (state == null) {
                    throw new Exception(
                            "Produit absent du panier après ajout."
                    );
                }

                // Garantie stricte :
                // même si un plugin / template Woo modifie la quantité,
                // on remet exactement quantité précédente + 1.
                if (state.quantity != expectedQuantity) {
                    after =
                            updateStoreApiItem(
                                    state.key,
                                    expectedQuantity,
                                    cookies,
                                    after.cartToken != null &&
                                    !after.cartToken.isEmpty()
                                            ? after.cartToken
                                            : cartToken
                            );

                    state =
                            after.itemForProduct(
                                    productId
                            );

                    if (state == null ||
                            state.quantity != expectedQuantity) {
                        throw new Exception(
                                "WooCommerce refuse la quantité exacte demandée."
                        );
                    }
                }

                String finalToken =
                        after.cartToken;

                List<String> setCookies =
                        after.setCookies;

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

                    callback.onSuccess(
                            after.itemsCount
                    );
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
                ADDING_PRODUCTS.remove(
                        productId
                );
            }
        });
    }

    private static final class CartItemState {
        final String key;
        final int productId;
        final int quantity;

        CartItemState(
                String key,
                int productId,
                int quantity
        ) {
            this.key = key;
            this.productId = productId;
            this.quantity = quantity;
        }
    }

    private static final class CartSnapshot {
        final List<CartItemState> items;
        final int itemsCount;
        final String cartToken;
        final List<String> setCookies;

        CartSnapshot(
                List<CartItemState> items,
                int itemsCount,
                String cartToken,
                List<String> setCookies
        ) {
            this.items = items;
            this.itemsCount = itemsCount;
            this.cartToken = cartToken;
            this.setCookies = setCookies;
        }

        CartItemState itemForProduct(
                int productId
        ) {
            for (CartItemState item : items) {
                if (item.productId == productId) {
                    return item;
                }
            }
            return null;
        }

        int quantityForProduct(
                int productId
        ) {
            CartItemState item =
                    itemForProduct(productId);

            return item == null
                    ? 0
                    : item.quantity;
        }
    }

    private static CartSnapshot getCartSnapshot(
            String cookies,
            String cartToken
    ) throws Exception {
        HttpURLConnection connection = null;

        try {
            connection =
                    (HttpURLConnection)
                            new URL(CART_API)
                                    .openConnection();

            connection.setConnectTimeout(7000);
            connection.setReadTimeout(9000);
            connection.setRequestMethod("GET");
            connection.setUseCaches(false);
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
                    "LudorumAndroid/1.0.12"
            );

            if (cookies != null &&
                    !cookies.trim().isEmpty()) {
                connection.setRequestProperty(
                        "Cookie",
                        cookies
                );
            }

            if (cartToken != null &&
                    !cartToken.trim().isEmpty()) {
                connection.setRequestProperty(
                        "Cart-Token",
                        cartToken
                );
            }

            int status =
                    connection.getResponseCode();

            if (status < 200 ||
                    status >= 300) {
                throw new Exception(
                        "Lecture panier HTTP " +
                        status
                );
            }

            JSONObject json =
                    new JSONObject(
                            read(
                                    connection.getInputStream()
                            )
                    );

            return snapshotFromResponse(
                    json,
                    connection
            );

        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static CartSnapshot addStoreApiItem(
            int productId,
            int quantity,
            String cookies,
            String cartToken
    ) throws Exception {
        String endpoint =
                CART_ADD_API +
                "?id=" +
                URLEncoder.encode(
                        String.valueOf(productId),
                        "UTF-8"
                ) +
                "&quantity=" +
                URLEncoder.encode(
                        String.valueOf(
                                Math.max(1, quantity)
                        ),
                        "UTF-8"
                );

        return writeStoreApi(
                endpoint,
                "POST",
                cookies,
                cartToken
        );
    }

    private static CartSnapshot updateStoreApiItem(
            String itemKey,
            int quantity,
            String cookies,
            String cartToken
    ) throws Exception {
        if (itemKey == null ||
                itemKey.trim().isEmpty()) {
            throw new Exception(
                    "Clé panier WooCommerce manquante."
            );
        }

        String endpoint =
                CART_API +
                "/items/" +
                URLEncoder.encode(
                        itemKey,
                        "UTF-8"
                ) +
                "?quantity=" +
                URLEncoder.encode(
                        String.valueOf(
                                Math.max(1, quantity)
                        ),
                        "UTF-8"
                );

        return writeStoreApi(
                endpoint,
                "PUT",
                cookies,
                cartToken
        );
    }

    private static CartSnapshot writeStoreApi(
            String endpoint,
            String method,
            String cookies,
            String cartToken
    ) throws Exception {
        HttpURLConnection connection = null;

        try {
            connection =
                    (HttpURLConnection)
                            new URL(endpoint)
                                    .openConnection();

            connection.setConnectTimeout(8000);
            connection.setReadTimeout(10000);
            connection.setRequestMethod(method);
            connection.setDoOutput(true);
            connection.setUseCaches(false);
            connection.setRequestProperty(
                    "Accept",
                    "application/json"
            );
            connection.setRequestProperty(
                    "Content-Type",
                    "application/json; charset=UTF-8"
            );
            connection.setRequestProperty(
                    "Connection",
                    "keep-alive"
            );
            connection.setRequestProperty(
                    "User-Agent",
                    "LudorumAndroid/1.0.12"
            );

            if (cookies != null &&
                    !cookies.trim().isEmpty()) {
                connection.setRequestProperty(
                        "Cookie",
                        cookies
                );
            }

            if (cartToken != null &&
                    !cartToken.trim().isEmpty()) {
                connection.setRequestProperty(
                        "Cart-Token",
                        cartToken
                );
            }

            // Force l'envoi immédiat de la requête POST/PUT sans payload.
            try (OutputStream output =
                         connection.getOutputStream()) {
                output.write(
                        new byte[0]
                );
            }

            int status =
                    connection.getResponseCode();

            InputStream stream =
                    status >= 200 &&
                    status < 400
                            ? connection.getInputStream()
                            : connection.getErrorStream();

            String response =
                    read(stream);

            if (status < 200 ||
                    status >= 300) {
                String message =
                        response;

                try {
                    JSONObject error =
                            new JSONObject(response);

                    String candidate =
                            error.optString(
                                    "message",
                                    ""
                            );

                    if (!candidate.trim().isEmpty()) {
                        message = candidate;
                    }
                } catch (Exception ignored) {}

                throw new Exception(
                        "WooCommerce HTTP " +
                        status +
                        (message == null ||
                         message.trim().isEmpty()
                                ? ""
                                : " — " + message)
                );
            }

            JSONObject json =
                    new JSONObject(response);

            return snapshotFromResponse(
                    json,
                    connection
            );

        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static CartSnapshot snapshotFromResponse(
            JSONObject json,
            HttpURLConnection connection
    ) {
        List<CartItemState> items =
                new ArrayList<>();

        JSONArray array =
                json.optJSONArray("items");

        if (array != null) {
            for (int i = 0; i < array.length(); i++) {
                JSONObject item =
                        array.optJSONObject(i);

                if (item == null) {
                    continue;
                }

                items.add(
                        new CartItemState(
                                item.optString(
                                        "key",
                                        ""
                                ),
                                item.optInt(
                                        "id",
                                        0
                                ),
                                item.optInt(
                                        "quantity",
                                        0
                                )
                        )
                );
            }
        }

        int itemsCount =
                json.optInt(
                        "items_count",
                        0
                );

        String cartToken =
                connection.getHeaderField(
                        "Cart-Token"
                );

        if (cartToken == null) {
            cartToken =
                    connection.getHeaderField(
                            "cart-token"
                    );
        }

        List<String> setCookies =
                collectSetCookies(
                        connection.getHeaderFields()
                );

        return new CartSnapshot(
                items,
                itemsCount,
                cartToken == null
                        ? ""
                        : cartToken,
                setCookies
        );
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
                        "LudorumAndroid/1.0.12"
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
            connection.setRequestProperty("User-Agent", "LudorumAndroid/1.0.12");

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
