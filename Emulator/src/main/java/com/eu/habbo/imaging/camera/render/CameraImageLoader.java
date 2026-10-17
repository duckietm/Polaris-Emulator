package com.eu.habbo.imaging.camera.render;

import com.eu.habbo.imaging.camera.assets.CameraAssetSource;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class CameraImageLoader {
    private static final Logger LOGGER = LoggerFactory.getLogger(CameraImageLoader.class);

    private final CameraAssetSource sprites;
    private final CameraAssetSource effects;
    private final CameraRenderOptions options;
    private final Map<String, BufferedImage> spriteCache = new HashMap<>();
    private final Map<String, BufferedImage> frameCache = new HashMap<>();
    private final Map<String, BufferedImage> urlImageCache = new HashMap<>();

    private final FetchBudget fetchBudget;

    CameraImageLoader(CameraAssetSource sprites, CameraAssetSource effects, CameraRenderOptions options) {
        this.sprites = sprites;
        this.effects = effects;
        this.options = options;
        this.fetchBudget = new FetchBudget(options.fetchMaxPerRender(), options.fetchBudgetMs());
    }

    CameraRenderOptions options() {
        return this.options;
    }

    BufferedImage readSprite(String asset) {
        if (asset == null || asset.isEmpty() || this.sprites == null) {
            return null;
        }
        if (this.spriteCache.containsKey(asset)) {
            return this.spriteCache.get(asset);
        }
        this.options.checkDeadline();
        BufferedImage image = this.sprites.image(asset);
        // null is cached intentionally: a failed read should not be retried on the same render
        this.spriteCache.put(asset, image);
        return image;
    }

    int[] readPalette(String asset, String paletteSource) {
        return this.sprites == null ? null : this.sprites.palette(asset, paletteSource);
    }

    /** A camera effect or frame overlay ({@code frame_gold}, {@code hearts_hardlight_02}), shared: copy before changing it. */
    BufferedImage readFrame(String asset) {
        if (asset == null || asset.isEmpty() || this.effects == null) {
            return null;
        }
        if (this.frameCache.containsKey(asset)) {
            return this.frameCache.get(asset);
        }
        BufferedImage image = this.effects.image(asset);
        this.frameCache.put(asset, image);
        return image;
    }

    BufferedImage readUrlImage(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        if (this.urlImageCache.containsKey(name)) {
            return this.urlImageCache.get(name);
        }
        if (!this.options.fetchEnabled() || !this.fetchBudget.tryConsume()) {
            LOGGER.debug("Camera URL fetch budget exhausted, skipping: {}", name);
            return null;
        }

        this.options.checkDeadline();
        BufferedImage image = fetchUrlImage(name, this.options);
        this.urlImageCache.put(name, image);
        return image;
    }

    BufferedImage getSpriteImage(CameraSprite sprite) {
        if (sprite == null) {
            return null;
        }

        return sprite.isFromUrl() ? readUrlImage(sprite.getName()) : readSprite(sprite.getName());
    }

    private static BufferedImage fetchUrlImage(String name, CameraRenderOptions options) {
        String requestUrl = name.startsWith("//") ? "http:" + name : name;
        try {
            URL url = URI.create(requestUrl).toURL();
            if (!isAllowedSpriteUrlScheme(url.getProtocol())) {
                LOGGER.debug("Rejecting non-http(s) camera sprite URL: {}", requestUrl);
                return null;
            }
            if (!isAllowedSpriteUrlHost(url.getHost(), options.allowedHosts())) {
                LOGGER.debug("Rejecting private/loopback camera sprite URL: {}", requestUrl);
                return null;
            }
            URLConnection conn = url.openConnection();
            if (conn instanceof HttpURLConnection httpConn) {
                // Don't follow redirects â€” a 30x to an internal address would bypass the
                // host check above. Caller treats null as "couldn't fetch".
                httpConn.setInstanceFollowRedirects(false);
            }
            conn.setConnectTimeout(options.fetchConnectTimeoutMs());
            conn.setReadTimeout(options.fetchReadTimeoutMs());
            int maxBytes = options.fetchMaxBytes();
            int maxPixels = options.fetchMaxPixels();
            try (InputStream stream = conn.getInputStream()) {
                byte[] bytes = readBounded(stream, maxBytes);
                if (bytes == null) {
                    LOGGER.debug("Camera sprite URL exceeded max bytes ({}): {}", maxBytes, requestUrl);
                    return null;
                }
                return decodeBounded(bytes, maxPixels, requestUrl);
            }
        } catch (Exception e) {
            LOGGER.debug("Failed to fetch camera sprite from URL: {}", requestUrl, e);
            return null;
        }
    }

    private static BufferedImage decodeBounded(byte[] bytes, int maxPixels, String sourceUrl) throws Exception {
        try (ImageInputStream iis = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) {
                return null;
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(iis, false, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if ((long) width * height > maxPixels) {
                    LOGGER.debug(
                            "Camera sprite URL image dimensions {}x{} exceed max pixels ({}): {}",
                            width,
                            height,
                            maxPixels,
                            sourceUrl);
                    return null;
                }
                return reader.read(0);
            } finally {
                reader.dispose();
            }
        }
    }

    private static byte[] readBounded(InputStream stream, int maxBytes) throws java.io.IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(Math.min(maxBytes, 16384));
        byte[] chunk = new byte[8192];
        int total = 0;
        int read;
        while ((read = stream.read(chunk)) != -1) {
            if (total + read > maxBytes) {
                return null;
            }
            buffer.write(chunk, 0, read);
            total += read;
        }
        return buffer.toByteArray();
    }

    private static boolean isAllowedSpriteUrlScheme(String scheme) {
        return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
    }

    static boolean isAllowedSpriteUrlHost(String host, String allowedHosts) {
        if (host == null || host.isEmpty()) {
            return false;
        }
        if (isHostAllowlisted(host, allowedHosts)) {
            return true;
        }
        try {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            if (addresses.length == 0) {
                return false;
            }
            for (InetAddress address : addresses) {
                if (address.isAnyLocalAddress()
                        || address.isLoopbackAddress()
                        || address.isLinkLocalAddress()
                        || address.isSiteLocalAddress()
                        || address.isMulticastAddress()) {
                    return false;
                }
            }
            return true;
        } catch (UnknownHostException e) {
            return false;
        }
    }

    static boolean isHostAllowlisted(String host, String configured) {
        if (configured == null || configured.isEmpty()) {
            return false;
        }
        String target = host.toLowerCase(Locale.ROOT);
        for (String entry : configured.split(",")) {
            String trimmed = entry.trim().toLowerCase(Locale.ROOT);
            if (trimmed.isEmpty() || trimmed.equals("*")) {
                continue;
            }
            if (trimmed.startsWith("*.")) {
                String suffix = trimmed.substring(2);
                if (suffix.isEmpty()) {
                    continue;
                }
                if (target.equals(suffix) || target.endsWith("." + suffix)) {
                    return true;
                }
            } else if (trimmed.equals(target)) {
                return true;
            }
        }
        return false;
    }

    private static final class FetchBudget {
        private int remaining;
        private final long deadlineNanos;

        FetchBudget(int maxFetches, int budgetMs) {
            this.remaining = maxFetches;
            this.deadlineNanos = System.nanoTime() + budgetMs * 1_000_000L;
        }

        boolean tryConsume() {
            if (this.remaining <= 0 || System.nanoTime() > this.deadlineNanos) {
                return false;
            }
            this.remaining--;
            return true;
        }
    }
}
