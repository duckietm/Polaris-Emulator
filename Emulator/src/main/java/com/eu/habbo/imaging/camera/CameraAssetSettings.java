package com.eu.habbo.imaging.camera;

import com.eu.habbo.core.ConfigurationManager;
import com.eu.habbo.imaging.camera.assets.BundleAssetSource;
import com.eu.habbo.imaging.camera.assets.CameraAssetSource;
import com.eu.habbo.imaging.camera.assets.CompositeAssetSource;
import com.eu.habbo.imaging.camera.assets.LooseImageDirectory;
import com.eu.habbo.imaging.camera.assets.NitroLibraryLoader;
import com.eu.habbo.imaging.camera.render.WallColorResolver;
import java.nio.file.Path;
import java.util.List;

/** Where the camera renderer reads assets from ({@code camera.render.assets.*}), read at start-up. */
public record CameraAssetSettings(
        String spritesDir,
        String effectsDir,
        String furnitureDir,
        String figureDir,
        String figureMap,
        String effectDir,
        String effectMap,
        String petDir,
        String roomLibrary,
        String wallColors,
        int cacheMegabytes,
        int maxBundleMegabytes) {

    public static final String SPRITES_DIR = "camera.render.assets.sprites_dir";
    public static final String EFFECTS_DIR = "camera.render.assets.effects_dir";
    public static final String FURNITURE_DIR = "camera.render.assets.furniture_dir";
    public static final String FIGURE_DIR = "camera.render.assets.figure_dir";
    public static final String FIGURE_MAP = "camera.render.assets.figuremap";
    public static final String EFFECT_DIR = "camera.render.assets.effect_dir";
    public static final String EFFECT_MAP = "camera.render.assets.effectmap";
    public static final String PET_DIR = "camera.render.assets.pet_dir";
    public static final String ROOM_LIBRARY = "camera.render.assets.room_library";
    public static final String WALL_COLORS = "camera.render.assets.wall_colors";
    public static final String CACHE_MB = "camera.render.cache_mb";
    public static final String MAX_BUNDLE_MB = "camera.render.assets.max_bundle_mb";

    public static CameraAssetSettings from(ConfigurationManager config) {
        return new CameraAssetSettings(
                config.getValue(SPRITES_DIR, ""),
                config.getValue(EFFECTS_DIR, ""),
                config.getValue(FURNITURE_DIR, ""),
                config.getValue(FIGURE_DIR, ""),
                config.getValue(FIGURE_MAP, ""),
                config.getValue(EFFECT_DIR, ""),
                config.getValue(EFFECT_MAP, ""),
                config.getValue(PET_DIR, ""),
                config.getValue(ROOM_LIBRARY, ""),
                config.getValue(WALL_COLORS, ""),
                CameraRenderSettings.clamp(config.getInt(CACHE_MB, 128), 8, 4096),
                CameraRenderSettings.clamp(config.getInt(MAX_BUNDLE_MB, 64), 1, 1024));
    }

    private static Path path(String value) {
        return value == null || value.isBlank() ? null : Path.of(value.trim());
    }

    private long cacheBytes() {
        return (long) this.cacheMegabytes * 1024L * 1024L;
    }

    private NitroLibraryLoader loader() {
        long maxBytes = (long) this.maxBundleMegabytes * 1024L * 1024L;
        return new NitroLibraryLoader(maxBytes, 64L * 1024 * 1024 / 4);
    }

    /** The sprite source: loose PNG folder first, then the bundles. */
    public CameraAssetSource sprites() {
        NitroLibraryLoader loader = this.loader();
        long cache = this.cacheBytes();
        LooseImageDirectory loose = new LooseImageDirectory(path(this.spritesDir), loader, cache / 8);
        BundleAssetSource bundles = new BundleAssetSource(
                new BundleAssetSource.Roots(
                        path(this.furnitureDir),
                        path(this.figureDir),
                        path(this.figureMap),
                        path(this.effectDir),
                        path(this.effectMap),
                        path(this.petDir),
                        path(this.roomLibrary)),
                loader,
                cache * 5 / 8,
                cache / 4);
        return new CompositeAssetSource(List.of(loose, bundles));
    }

    /** Camera effect and frame overlays ({@code <name>.png}); null when not configured. */
    public CameraAssetSource effects() {
        Path dir = path(this.effectsDir);
        return dir == null ? null : new LooseImageDirectory(dir, this.loader(), 16L * 1024 * 1024);
    }

    public WallColorResolver wallColorResolver() {
        Path file = path(this.wallColors);
        return file == null ? null : new WallColorResolver(file);
    }
}
