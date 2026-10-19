package com.eu.habbo.habbohotel.items;

import com.eu.habbo.core.ConfigurationManager;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.ToIntFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Composition boundary for {@link FurnitureDirections#of}: the boot sequence installs the catalog
 * of furni directions (read from the furniture bundles) and the furnidata {@code defaultdir}
 * lookup here. Before boot, and in tests that install nothing, every furni is {@link
 * FurnitureDirections#UNKNOWN} and keeps the old rotation rules.
 */
public final class FurnitureDirectionsRuntime {
    private static final Logger LOGGER = LoggerFactory.getLogger(FurnitureDirectionsRuntime.class);

    public static final String ENABLED = "furni.directions.enabled";
    public static final String ASSETS_DIR = "furni.directions.assets_dir";
    public static final String CACHE_SIZE = "furni.directions.cache_size";
    public static final String WAIT_MS = "furni.directions.wait_ms";
    /** The camera renderer's furniture folder, used when {@link #ASSETS_DIR} is empty. */
    public static final String CAMERA_FURNITURE_DIR = "camera.render.assets.furniture_dir";

    private static final String MAX_BUNDLE_MB = "camera.render.assets.max_bundle_mb";
    private static final AtomicReference<Installed> INSTALLED = new AtomicReference<>();

    private record Installed(FurnitureDirectionCatalog catalog, ToIntFunction<String> defaultDirections) {}

    private FurnitureDirectionsRuntime() {}

    /**
     * Installs a catalog; {@code defaultDirections} answers the furnidata {@code defaultdir} of a
     * classname, or -1 (null: none known). Null uninstalls.
     */
    public static void install(FurnitureDirectionCatalog catalog, ToIntFunction<String> defaultDirections) {
        Installed next = catalog == null
                ? null
                : new Installed(catalog, defaultDirections == null ? name -> -1 : defaultDirections);
        Installed previous = INSTALLED.getAndSet(next);
        if (previous != null && (next == null || previous.catalog() != next.catalog())) {
            previous.catalog().shutdown();
        }
    }

    public static void uninstall() {
        install(null, null);
    }

    /** The installed catalog, or null. */
    public static FurnitureDirectionCatalog catalog() {
        Installed installed = INSTALLED.get();
        return installed == null ? null : installed.catalog();
    }

    /** The directions of a furni type; {@link FurnitureDirections#UNKNOWN} when not known. */
    public static FurnitureDirections lookup(String itemName) {
        Installed installed = INSTALLED.get();
        if (installed == null || itemName == null) {
            return FurnitureDirections.UNKNOWN;
        }
        int[] directions = installed.catalog().directions(itemName);
        if (directions.length == 0) {
            return FurnitureDirections.UNKNOWN;
        }
        int defaultDirection = -1;
        try {
            defaultDirection = installed.defaultDirections().applyAsInt(itemName);
        } catch (RuntimeException e) {
            LOGGER.debug("Furni directions: no defaultdir for {}", itemName, e);
        }
        return FurnitureDirections.of(directions, defaultDirection);
    }

    /** Starts reading a furni type's directions in the background, so a later lookup finds them. */
    public static void prefetch(String itemName) {
        FurnitureDirectionCatalog catalog = catalog();
        if (catalog != null) {
            catalog.prefetch(itemName);
        }
    }

    /** Reads the settings from {@code emulator_settings}. */
    public static FurnitureDirectionCatalog.Settings settings(ConfigurationManager config) {
        String dir = config.getValue(ASSETS_DIR, "").trim();
        if (dir.isEmpty()) {
            dir = config.getValue(CAMERA_FURNITURE_DIR, "").trim();
        }
        int maxBundleMb = Math.max(1, Math.min(1024, config.getInt(MAX_BUNDLE_MB, 64)));
        return new FurnitureDirectionCatalog.Settings(
                config.getBoolean(ENABLED, true),
                dir.isEmpty() ? null : Path.of(dir),
                Math.max(16, Math.min(1_000_000, config.getInt(CACHE_SIZE, 8192))),
                Math.max(0, Math.min(5000, config.getInt(WAIT_MS, 50))),
                maxBundleMb * 1024L * 1024L);
    }

    /** Builds the catalog from {@code emulator_settings} and installs it. */
    public static FurnitureDirectionCatalog start(
            ConfigurationManager config, ToIntFunction<String> defaultDirections) {
        FurnitureDirectionCatalog.Settings settings = settings(config);
        FurnitureDirectionCatalog catalog = new FurnitureDirectionCatalog(settings);
        install(catalog, defaultDirections);
        if (catalog.isEnabled()) {
            LOGGER.info("Furni directions: read from the furniture bundles in {}", catalog.furnitureDir());
        } else if (settings.enabled()) {
            LOGGER.info(
                    "Furni directions: off (no furniture bundle folder; set {} or {}). "
                            + "Furni keep the default rotations.",
                    ASSETS_DIR,
                    CAMERA_FURNITURE_DIR);
        } else {
            LOGGER.info("Furni directions: off ({} is 0). Furni keep the default rotations.", ENABLED);
        }
        return catalog;
    }
}
