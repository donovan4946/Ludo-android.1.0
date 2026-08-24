package fr.ludorum.app;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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

    private static final ExecutorService EXECUTOR =
            Executors.newFixedThreadPool(4);

    private static final Handler MAIN =
            new Handler(Looper.getMainLooper());

    private static final long PRODUCTS_CACHE_MS = 30000L;
    private static final long PRODUCT_CACHE_MS = 30000L;
    private static final long CATEGORY_CACHE_MS = 300000L;

    private static final Map<String, CacheEntry> CACHE =
            new ConcurrentHashMap<>();

    private static final class CacheEntry {
        final long time;
        final Object value;

        CacheEntry(
                long time,
                Object value
        ) {
            this.time = time;
            this.value = value;
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T cached(
            String key,
            long ttl
    ) {
        CacheEntry entry =
                CACHE.get(key);

        if (entry == null) {
            return null;
        }

        if (System.currentTimeMillis() - entry.time > ttl) {
            CACHE.remove(key);
            return null;
        }

        try {
            return (T) entry.value;
        } catch (Exception ignored) {
            CACHE.remove(key);
            return null;
        }
    }

    private static void cache(
            String key,
            Object value
    ) {
        if (key == null ||
                value == null) {
            return;
        }

        CACHE.put(
                key,
                new CacheEntry(
                        System.currentTimeMillis(),
                        value
                )
        );
    }

    static void getProducts(
            String query,
            int page,
            int perPage,
            Callback<ProductPage> callback
    ) {
        String q =
                query == null
                        ? ""
                        : query;

        String url =
                STORE +
                "/products?per_page=" +
                perPage +
                "&page=" +
                page +
                q;

        String cacheKey =
                "products:" + url;

        ProductPage cached =
                cached(
                        cacheKey,
                        PRODUCTS_CACHE_MS
                );

        if (cached != null) {
            MAIN.post(
                    () -> callback.onSuccess(cached)
            );
            return;
        }

        EXECUTOR.execute(() -> {
            HttpURLConnection connection = null;
            try {
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

                ProductPage result =
                        new ProductPage(
                                products,
                                page,
                                pages
                        );

                cache(
                        cacheKey,
                        result
                );

                MAIN.post(
                        () -> callback.onSuccess(result)
                );
            } catch (Exception error) {
                MAIN.post(() -> callback.onError(error));
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    static void getProductBySlug(
            String slug,
            Callback<Product> callback
    ) {
        String safeSlug =
                slug == null
                        ? ""
                        : slug.trim();

        String cacheKey =
                "product:" + safeSlug;

        Product cached =
                cached(
                        cacheKey,
                        PRODUCT_CACHE_MS
                );

        if (cached != null) {
            MAIN.post(
                    () -> callback.onSuccess(cached)
            );
            return;
        }

        EXECUTOR.execute(() -> {
            HttpURLConnection connection = null;

            try {
                String encoded =
                        URLEncoder.encode(
                                safeSlug,
                                "UTF-8"
                        );

                connection = open(
                        STORE +
                        "/products?slug=" +
                        encoded +
                        "&per_page=1"
                );

                int status =
                        connection.getResponseCode();

                if (status < 200 ||
                        status >= 300) {
                    throw new Exception(
                            "HTTP " + status
                    );
                }

                JSONArray array =
                        new JSONArray(
                                read(
                                        connection.getInputStream()
                                )
                        );

                if (array.length() < 1 ||
                        array.optJSONObject(0) == null) {
                    throw new Exception(
                            "Produit introuvable"
                    );
                }

                Product product =
                        Product.fromJson(
                                array.optJSONObject(0)
                        );

                cache(
                        cacheKey,
                        product
                );

                MAIN.post(
                        () -> callback.onSuccess(product)
                );

            } catch (Exception error) {
                MAIN.post(
                        () -> callback.onError(error)
                );

            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    static void getCategoryBySlug(
            String slug,
            Callback<ProductCategory> callback
    ) {
        String safeSlug =
                slug == null
                        ? ""
                        : slug.trim();

        String cacheKey =
                "category:" + safeSlug;

        ProductCategory cached =
                cached(
                        cacheKey,
                        CATEGORY_CACHE_MS
                );

        if (cached != null) {
            MAIN.post(
                    () -> callback.onSuccess(cached)
            );
            return;
        }

        EXECUTOR.execute(() -> {
            HttpURLConnection connection = null;

            try {
                String encoded =
                        URLEncoder.encode(
                                safeSlug,
                                "UTF-8"
                        );

                connection = open(
                        STORE +
                        "/products/categories?slug=" +
                        encoded +
                        "&per_page=1"
                );

                int status =
                        connection.getResponseCode();

                if (status < 200 ||
                        status >= 300) {
                    throw new Exception(
                            "HTTP " + status
                    );
                }

                JSONArray array =
                        new JSONArray(
                                read(
                                        connection.getInputStream()
                                )
                        );

                if (array.length() < 1 ||
                        array.optJSONObject(0) == null) {
                    throw new Exception(
                            "Catégorie introuvable"
                    );
                }

                ProductCategory category =
                        ProductCategory.fromJson(
                                array.optJSONObject(0)
                        );

                cache(
                        cacheKey,
                        category
                );

                MAIN.post(
                        () -> callback.onSuccess(category)
                );

            } catch (Exception error) {
                MAIN.post(
                        () -> callback.onError(error)
                );

            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    static void getTopCategories(
            Callback<List<ProductCategory>> callback
    ) {
        final String cacheKey =
                "top_categories";

        List<ProductCategory> cached =
                cached(
                        cacheKey,
                        CATEGORY_CACHE_MS
                );

        if (cached != null) {
            MAIN.post(
                    () -> callback.onSuccess(cached)
            );
            return;
        }

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

                cache(
                        cacheKey,
                        categories
                );

                MAIN.post(
                        () -> callback.onSuccess(categories)
                );
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

        connection.setConnectTimeout(7000);
        connection.setReadTimeout(10000);
        connection.setRequestMethod("GET");
        connection.setUseCaches(true);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Connection", "keep-alive");
        connection.setRequestProperty("User-Agent", "LudorumAndroid/1.0.3");
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
