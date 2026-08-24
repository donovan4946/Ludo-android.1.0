package fr.ludorum.app;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class ApiClient {
    static final String BASE = "https://ludorum.fr";
    static final String STORE = BASE + "/wp-json/wc/store/v1";

    interface Callback<T> {
        void onSuccess(T value);
        void onError(Exception error);
    }

    static final class ProductPage {
        final List<Product> products;
        final int page;
        final int totalPages;

        ProductPage(List<Product> products, int page, int totalPages) {
            this.products = products;
            this.page = page;
            this.totalPages = Math.max(1, totalPages);
        }
    }

    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(4);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    static void getProducts(
            String query,
            int page,
            int perPage,
            Callback<ProductPage> callback
    ) {
        EXECUTOR.execute(() -> {
            HttpURLConnection connection = null;
            try {
                String q = query == null ? "" : query;
                String url = STORE + "/products?per_page=" + perPage + "&page=" + page + q;
                connection = open(url);

                int status = connection.getResponseCode();
                if (status < 200 || status >= 300) {
                    throw new Exception("HTTP " + status);
                }

                JSONArray array = new JSONArray(read(connection.getInputStream()));
                List<Product> products = new ArrayList<>();
                for (int i = 0; i < array.length(); i++) {
                    if (array.optJSONObject(i) != null) {
                        products.add(Product.fromJson(array.optJSONObject(i)));
                    }
                }

                int pages = parseInt(connection.getHeaderField("X-WP-TotalPages"), 0);
                if (pages <= 0) {
                    pages = array.length() < perPage ? page : page + 1;
                }

                ProductPage result = new ProductPage(products, page, pages);
                MAIN.post(() -> callback.onSuccess(result));
            } catch (Exception error) {
                MAIN.post(() -> callback.onError(error));
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    static void getTopCategories(Callback<List<ProductCategory>> callback) {
        EXECUTOR.execute(() -> {
            HttpURLConnection connection = null;
            try {
                connection = open(
                        STORE +
                        "/products/categories?per_page=100&hide_empty=true&parent=0" +
                        "&orderby=count&order=desc"
                );

                int status = connection.getResponseCode();
                if (status < 200 || status >= 300) {
                    throw new Exception("HTTP " + status);
                }

                JSONArray array = new JSONArray(read(connection.getInputStream()));
                List<ProductCategory> categories = new ArrayList<>();

                for (int i = 0; i < array.length(); i++) {
                    if (array.optJSONObject(i) == null) continue;

                    ProductCategory category =
                            ProductCategory.fromJson(array.optJSONObject(i));

                    if (isHiddenCategory(category.name) ||
                            isHiddenCategory(category.slug)) {
                        continue;
                    }
                    categories.add(category);
                }

                categories.sort(
                        (a, b) -> Integer.compare(
                                categoryPriority(a.name),
                                categoryPriority(b.name)
                        )
                );

                MAIN.post(() -> callback.onSuccess(categories));
            } catch (Exception error) {
                MAIN.post(() -> callback.onError(error));
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    private static boolean isHiddenCategory(String value) {
        String normalized = normalize(value);
        return normalized.isEmpty()
                || normalized.contains("jeton")
                || normalized.contains("compteur")
                || normalized.equals("non classe")
                || normalized.equals("non classee")
                || normalized.equals("uncategorized");
    }

    private static int categoryPriority(String value) {
        String normalized = normalize(value);
        if (normalized.contains("jeux de societe")) return 0;
        if (normalized.contains("jeux de cartes")) return 1;
        if (normalized.contains("accessoire")) return 2;
        if (normalized.contains("pack")) return 3;
        return 10;
    }

    private static String normalize(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .trim();
    }

    private static HttpURLConnection open(String url) throws Exception {
        HttpURLConnection connection =
                (HttpURLConnection) new URL(url).openConnection();

        connection.setConnectTimeout(12000);
        connection.setReadTimeout(16000);
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", "LudorumAndroid/1.0.0");
        connection.setInstanceFollowRedirects(true);
        return connection;
    }

    private static String read(InputStream input) throws Exception {
        try (
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(input, StandardCharsets.UTF_8)
                )
        ) {
            StringBuilder result = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                result.append(line);
            }
            return result.toString();
        }
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (Exception ignored) {
            return fallback;
        }
    }
}
