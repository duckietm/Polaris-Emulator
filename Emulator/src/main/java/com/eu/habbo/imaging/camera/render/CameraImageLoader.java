package com.eu.habbo.imaging.camera.render;

import com.eu.habbo.imaging.camera.assets.CameraAssetSource;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
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
    private final CameraUrlFetcher urlFetcher;

    CameraImageLoader(CameraAssetSource sprites, CameraAssetSource effects, CameraRenderOptions options) {
        this(sprites, effects, options, CameraUrlFetcher.system());
    }

    CameraImageLoader(
            CameraAssetSource sprites,
            CameraAssetSource effects,
            CameraRenderOptions options,
            CameraUrlFetcher urlFetcher) {
        this.urlFetcher = urlFetcher;
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
        BufferedImage image = fetchUrlImage(name);
        this.urlImageCache.put(name, image);
        return image;
    }

    BufferedImage getSpriteImage(CameraSprite sprite) {
        if (sprite == null) {
            return null;
        }

        return sprite.isFromUrl() ? readUrlImage(sprite.getName()) : readSprite(sprite.getName());
    }

    private BufferedImage fetchUrlImage(String name) {
        String requestUrl = name.startsWith("//") ? "http:" + name : name;
        try {
            byte[] bytes = this.urlFetcher.fetch(requestUrl, this.options);
            if (bytes == null) {
                LOGGER.debug("Camera sprite URL refused or not fetched: {}", requestUrl);
                return null;
            }
            return decodeBounded(bytes, this.options.fetchMaxPixels(), requestUrl);
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
