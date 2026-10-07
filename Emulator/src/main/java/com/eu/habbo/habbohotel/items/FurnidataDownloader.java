package com.eu.habbo.habbohotel.items;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class FurnidataDownloader {
    private static final Logger LOGGER = LoggerFactory.getLogger(FurnidataDownloader.class);

    static final long MAX_BYTES = 64L * 1024 * 1024;
    static final long REFRESH_MS = Duration.ofMinutes(10).toMillis();
    static final long RETRY_MS = Duration.ofMinutes(1).toMillis();

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);
    private static final ConcurrentHashMap<String, Long> LAST_ATTEMPT = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Boolean> REFRESHING = new ConcurrentHashMap<>();
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private FurnidataDownloader() {}

    static boolean isUrl(String value) {
        if (value == null) return false;
        String lower = value.trim().toLowerCase(Locale.ROOT);
        return lower.startsWith("http://") || lower.startsWith("https://");
    }

    static Path cachePathFor(String url) {
        return Paths.get(System.getProperty("java.io.tmpdir"), "polaris-furnidata", hash(url.trim()) + ".json");
    }

    static boolean isDownloadedCopy(Path path) {
        if (path == null) return false;
        Path folder = cachePathFor("").getParent().toAbsolutePath().normalize();
        return path.toAbsolutePath().normalize().startsWith(folder);
    }

    static FurnidataSourceResolver.Source resolve(String url) {
        String trimmed = url.trim();
        Path cache = cachePathFor(trimmed);
        long now = System.currentTimeMillis();

        if (!Files.exists(cache)) {
            Long last = LAST_ATTEMPT.get(trimmed);
            if (last == null || now - last >= RETRY_MS) {
                LAST_ATTEMPT.put(trimmed, now);
                download(trimmed, cache);
            }
        } else if (now - lastModified(cache) >= REFRESH_MS) {
            refreshInBackground(trimmed, cache);
        }

        if (Files.exists(cache)) {
            return new FurnidataSourceResolver.Source(
                    cache,
                    false,
                    FurnidataSourceResolver.Status.RESOLVED,
                    "items.furnidata.path download of " + trimmed);
        }
        return new FurnidataSourceResolver.Source(
                cache,
                false,
                FurnidataSourceResolver.Status.SOURCE_MISSING,
                "items.furnidata.path could not be downloaded from " + trimmed);
    }

    private static void refreshInBackground(String url, Path cache) {
        Long last = LAST_ATTEMPT.get(url);
        long now = System.currentTimeMillis();
        if ((last != null && now - last < RETRY_MS) || REFRESHING.putIfAbsent(url, Boolean.TRUE) != null) {
            return;
        }
        LAST_ATTEMPT.put(url, now);
        CompletableFuture.runAsync(() -> {
            try {
                download(url, cache);
            } finally {
                REFRESHING.remove(url);
            }
        });
    }

    static boolean download(String url, Path cache) {
        Path temp = null;
        try {
            Files.createDirectories(cache.getParent());
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<InputStream> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream body = response.body()) {
                if (response.statusCode() != 200) {
                    LOGGER.warn("Furnidata download from {} failed: HTTP {}", url, response.statusCode());
                    return false;
                }
                temp = Files.createTempFile(cache.getParent(), "download-", ".json");
                if (!copyLimited(body, temp)) {
                    LOGGER.warn("Furnidata download from {} is larger than {} bytes", url, MAX_BYTES);
                    return false;
                }
            }
            if (!looksLikeJsonObject(temp)) {
                LOGGER.warn("Furnidata download from {} is not a JSON furnidata file", url);
                return false;
            }
            try {
                Files.move(temp, cache, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, cache, StandardCopyOption.REPLACE_EXISTING);
            }
            temp = null;
            LOGGER.info("Furnidata downloaded from {} to {}", url, cache);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.warn("Furnidata download from {} was interrupted", url);
            return false;
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("Furnidata download from {} failed: {}", url, e.getMessage());
            return false;
        } finally {
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException ignored) {
                }
            }
        }
    }

    private static boolean copyLimited(InputStream in, Path target) throws IOException {
        byte[] buffer = new byte[64 * 1024];
        long total = 0;
        try (OutputStream out = Files.newOutputStream(target)) {
            int read;
            while ((read = in.read(buffer)) != -1) {
                total += read;
                if (total > MAX_BYTES) {
                    return false;
                }
                out.write(buffer, 0, read);
            }
        }
        return true;
    }

    static boolean looksLikeJsonObject(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            int c;
            int index = 0;
            while ((c = in.read()) != -1) {
                if (index++ < 3 && (c == 0xEF || c == 0xBB || c == 0xBF)) continue;
                if (Character.isWhitespace(c)) continue;
                return c == '{';
            }
            return false;
        }
    }

    private static long lastModified(Path file) {
        try {
            return Files.getLastModifiedTime(file).toMillis();
        } catch (IOException e) {
            return 0L;
        }
    }

    private static String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 12);
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(value.hashCode());
        }
    }
}
