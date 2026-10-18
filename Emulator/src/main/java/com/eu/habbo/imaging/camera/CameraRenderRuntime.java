package com.eu.habbo.imaging.camera;

import com.eu.habbo.core.ConfigurationManager;
import com.eu.habbo.imaging.camera.assets.GroupBadgeAssetSource;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Composition boundary for the camera packet handlers, whose no-argument construction by the
 * packet manager prevents constructor injection: the boot sequence installs the render service
 * here and the handlers read it.
 */
public final class CameraRenderRuntime {
    private static final Logger LOGGER = LoggerFactory.getLogger(CameraRenderRuntime.class);
    private static final AtomicReference<CameraRenderService> SERVICE = new AtomicReference<>();

    private CameraRenderRuntime() {}

    /** The installed service, or null before boot (or in tests that install none). */
    public static CameraRenderService service() {
        return SERVICE.get();
    }

    public static void install(CameraRenderService service) {
        CameraRenderService previous = SERVICE.getAndSet(service);
        if (previous != null && previous != service) {
            previous.shutdown();
        }
    }

    public static void uninstall() {
        install(null);
    }

    /** Builds the service from {@code emulator_settings} and installs it, without group badges. */
    public static CameraRenderService start(ConfigurationManager config) {
        return start(config, null);
    }

    /**
     * Builds the service from {@code emulator_settings} and installs it; {@code groupBadges} draws
     * the {@code group_badge:<code>} sprites (the badge imager's composition), or null.
     */
    public static CameraRenderService start(
            ConfigurationManager config, GroupBadgeAssetSource.BadgeComposer groupBadges) {
        CameraAssetSettings assets = CameraAssetSettings.from(config);
        int workers = config.getInt(CameraRenderSettings.WORKERS, CameraRenderSettings.DEFAULT_WORKERS);
        int queue = config.getInt(CameraRenderSettings.QUEUE, CameraRenderSettings.DEFAULT_QUEUE);
        CameraRenderService service = new CameraRenderService(
                () -> CameraRenderSettings.from(config),
                assets.sprites(groupBadges),
                assets.effects(),
                assets.wallColorResolver(),
                workers,
                queue);
        install(service);

        CameraRenderMode mode = service.settings().mode();
        if (service.isServerRenderingAvailable()) {
            LOGGER.info(
                    "Camera: mode {}, server rendering on {} worker(s), queue {}, assets: {}",
                    mode.name().toLowerCase(java.util.Locale.ROOT),
                    Math.max(1, Math.min(16, workers)),
                    queue,
                    service.describeAssets());
        } else if (mode == CameraRenderMode.SERVER) {
            LOGGER.warn("Camera: camera.render.mode is 'server' but no camera.render.assets.* source is configured; "
                    + "every photo will be refused. See docs/camera-server-render.md.");
        } else {
            LOGGER.info(
                    "Camera: mode {}, server rendering off (no camera.render.assets.* source configured).",
                    mode.name().toLowerCase(java.util.Locale.ROOT));
        }
        return service;
    }
}
