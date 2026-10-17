package com.eu.habbo.imaging.camera;

import com.eu.habbo.core.ConfigurationManager;
import com.eu.habbo.imaging.camera.render.CameraRenderOptions;

/**
 * The {@code camera.render.*} settings read for one request. Paths of asset sources are read once
 * when the render service starts (see {@link CameraAssetSettings}); everything here applies on the
 * next request after an {@code emulator_settings} change.
 */
public record CameraRenderSettings(
        CameraRenderMode mode,
        int timeoutMs,
        int maxCompressedBytes,
        int maxInflatedBytes,
        int maxPlanes,
        int maxSprites,
        int maxFilters,
        boolean verifyChecksum,
        int maxClockSkewSeconds,
        CameraRenderOptions renderOptions,
        String photoOutputDir,
        String thumbnailOutputDir,
        String photoUrlBase,
        String photoExtradata) {

    public static final String MODE = "camera.render.mode";
    public static final String WORKERS = "camera.render.workers";
    public static final String QUEUE = "camera.render.queue";
    public static final String TIMEOUT_MS = "camera.render.timeout_ms";
    public static final String MAX_COMPRESSED = "camera.render.limits.compressed_bytes";
    public static final String MAX_INFLATED = "camera.render.limits.inflated_bytes";
    public static final String MAX_PLANES = "camera.render.limits.planes";
    public static final String MAX_SPRITES = "camera.render.limits.sprites";
    public static final String MAX_FILTERS = "camera.render.limits.filters";
    public static final String MAX_PLANE_TEXTURE = "camera.render.limits.plane_texture_pixels";
    public static final String VERIFY_CHECKSUM = "camera.render.verify_checksum";
    public static final String MAX_CLOCK_SKEW = "camera.render.max_clock_skew_seconds";
    public static final String FETCH_ENABLED = "camera.render.fetch.enabled";
    public static final String FETCH_MAX = "camera.render.fetch.max_per_render";
    public static final String FETCH_BUDGET = "camera.render.fetch.budget_ms";
    public static final String FETCH_CONNECT = "camera.render.fetch.connect_timeout_ms";
    public static final String FETCH_READ = "camera.render.fetch.read_timeout_ms";
    public static final String FETCH_MAX_BYTES = "camera.render.fetch.max_bytes";
    public static final String FETCH_MAX_PIXELS = "camera.render.fetch.max_pixels";
    public static final String FETCH_ALLOWED_HOSTS = "camera.render.fetch.allowed_hosts";

    public static final int DEFAULT_WORKERS = 2;
    public static final int DEFAULT_QUEUE = 16;

    /** Defaults, for tests and for a hotel without configuration. */
    public static CameraRenderSettings defaults() {
        return new CameraRenderSettings(
                CameraRenderMode.AUTO,
                5000,
                65536,
                1_048_576,
                64,
                1024,
                16,
                true,
                0,
                new CameraRenderOptions(1_048_576, true, 30, 3000, 1500, 2000, 2_097_152, 4_000_000, "", 0L),
                "",
                "",
                "",
                "");
    }

    public static CameraRenderSettings from(ConfigurationManager config) {
        if (config == null) {
            return defaults();
        }
        return new CameraRenderSettings(
                CameraRenderMode.parse(config.getValue(MODE, "auto")),
                clamp(config.getInt(TIMEOUT_MS, 5000), 250, 60_000),
                clamp(config.getInt(MAX_COMPRESSED, 65536), 1024, 2 * 1024 * 1024),
                clamp(config.getInt(MAX_INFLATED, 1_048_576), 4096, 16 * 1024 * 1024),
                clamp(config.getInt(MAX_PLANES, 64), 1, 1024),
                clamp(config.getInt(MAX_SPRITES, 1024), 1, 8192),
                clamp(config.getInt(MAX_FILTERS, 16), 0, 64),
                config.getBoolean(VERIFY_CHECKSUM, true),
                Math.max(0, config.getInt(MAX_CLOCK_SKEW, 0)),
                new CameraRenderOptions(
                        clamp(config.getInt(MAX_PLANE_TEXTURE, 1_048_576), 1024, 16_000_000),
                        config.getBoolean(FETCH_ENABLED, true),
                        clamp(config.getInt(FETCH_MAX, 30), 0, 100),
                        clamp(config.getInt(FETCH_BUDGET, 3000), 0, 60_000),
                        clamp(config.getInt(FETCH_CONNECT, 1500), 100, 30_000),
                        clamp(config.getInt(FETCH_READ, 2000), 100, 30_000),
                        clamp(config.getInt(FETCH_MAX_BYTES, 2_097_152), 1024, 16 * 1024 * 1024),
                        clamp(config.getInt(FETCH_MAX_PIXELS, 4_000_000), 1024, 16_000_000),
                        config.getValue(FETCH_ALLOWED_HOSTS, ""),
                        0L),
                config.getValue("imager.location.output.camera", ""),
                config.getValue("imager.location.output.thumbnail", ""),
                config.getValue("camera.url", ""),
                config.getValue("camera.extradata", ""));
    }

    static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
