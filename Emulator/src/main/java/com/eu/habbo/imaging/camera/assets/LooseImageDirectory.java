package com.eu.habbo.imaging.camera.assets;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/** A folder of loose {@code <name>.png} files, as exported by the flash-era asset tools. */
public final class LooseImageDirectory implements CameraAssetSource {
    private final Path root;
    private final NitroLibraryLoader loader;
    private final WeightedLruCache<String, Optional<BufferedImage>> cache;

    public LooseImageDirectory(Path root, NitroLibraryLoader loader, long cacheBytes) {
        this.root = root == null ? null : root.toAbsolutePath().normalize();
        this.loader = loader;
        this.cache = new WeightedLruCache<>(
                cacheBytes,
                image -> image.map(i -> (long) i.getWidth() * i.getHeight() * 4L + 64L)
                        .orElse(64L));
    }

    @Override
    public BufferedImage image(String assetName) {
        if (this.root == null || !NitroLibraryLoader.isSafeName(assetName)) {
            return null;
        }
        Optional<BufferedImage> cached = this.cache.get(assetName);
        if (cached != null) {
            return cached.orElse(null);
        }
        BufferedImage image = this.read(assetName);
        this.cache.put(assetName, Optional.ofNullable(image));
        return image;
    }

    private BufferedImage read(String assetName) {
        Path file = this.root.resolve(assetName + ".png").normalize();
        if (!file.startsWith(this.root) || !Files.isRegularFile(file)) {
            return null;
        }
        try (InputStream in = Files.newInputStream(file)) {
            return this.loader.decode(in);
        } catch (IOException e) {
            return null;
        }
    }

    @Override
    public boolean isConfigured() {
        return this.root != null && Files.isDirectory(this.root);
    }

    @Override
    public String describe() {
        return "loose images in " + this.root;
    }
}
