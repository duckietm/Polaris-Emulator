package com.eu.habbo.imaging.camera.assets;

import java.awt.image.BufferedImage;
import java.util.Optional;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Group badges named {@code group_badge:<badge code>} in the scene. Our client builds group badges
 * locally from the badge parts, so when it has no {@code group.badge.url} imager it names the badge
 * by its code. The badge is drawn in memory from the code (the hotel's badge parts, as the badge
 * imager draws them) and kept in a bounded cache; no file is written.
 */
public final class GroupBadgeAssetSource implements CameraAssetSource {
    public static final String PREFIX = "group_badge:";
    private static final Logger LOGGER = LoggerFactory.getLogger(GroupBadgeAssetSource.class);
    private static final Pattern BADGE_CODE = Pattern.compile("(?:[A-Za-z]\\d{6}){1,5}");

    /** Draws a badge from its code; null when it cannot. */
    @FunctionalInterface
    public interface BadgeComposer {
        BufferedImage compose(String badgeCode);
    }

    private final BadgeComposer composer;
    private final WeightedLruCache<String, Optional<BufferedImage>> cache;

    public GroupBadgeAssetSource(BadgeComposer composer, long cacheBytes) {
        this.composer = composer;
        this.cache = new WeightedLruCache<>(
                cacheBytes,
                image -> image.map(i -> (long) i.getWidth() * i.getHeight() * 4L + 64L)
                        .orElse(64L));
    }

    /** True for a {@code group_badge:} sprite name. */
    public static boolean isGroupBadgeName(String name) {
        return name != null && name.startsWith(PREFIX);
    }

    @Override
    public BufferedImage image(String assetName) {
        if (!isGroupBadgeName(assetName) || this.composer == null) {
            return null;
        }
        String code = assetName.substring(PREFIX.length());
        if (!BADGE_CODE.matcher(code).matches()) {
            return null;
        }
        Optional<BufferedImage> cached = this.cache.get(code);
        if (cached != null) {
            return cached.orElse(null);
        }
        BufferedImage image = null;
        try {
            image = this.composer.compose(code);
        } catch (RuntimeException e) {
            LOGGER.debug("Camera renderer could not draw group badge {}", code, e);
        }
        this.cache.put(code, Optional.ofNullable(image));
        return image;
    }

    /** Badges alone cannot render a scene, so they never make server rendering available. */
    @Override
    public boolean isConfigured() {
        return false;
    }

    @Override
    public String describe() {
        return "group badges";
    }
}
