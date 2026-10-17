package com.eu.habbo.imaging.camera.assets;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.stream.Collectors;

/** Asks each source in turn; the first that knows a name wins. */
public final class CompositeAssetSource implements CameraAssetSource {
    private final List<CameraAssetSource> sources;

    public CompositeAssetSource(List<CameraAssetSource> sources) {
        this.sources = List.copyOf(sources);
    }

    @Override
    public BufferedImage image(String assetName) {
        for (CameraAssetSource source : this.sources) {
            BufferedImage image = source.image(assetName);
            if (image != null) {
                return image;
            }
        }
        return null;
    }

    @Override
    public int[] palette(String assetName, String paletteSource) {
        for (CameraAssetSource source : this.sources) {
            int[] palette = source.palette(assetName, paletteSource);
            if (palette != null) {
                return palette;
            }
        }
        return null;
    }

    @Override
    public boolean isConfigured() {
        for (CameraAssetSource source : this.sources) {
            if (source.isConfigured()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String describe() {
        return this.sources.stream()
                .filter(CameraAssetSource::isConfigured)
                .map(CameraAssetSource::describe)
                .collect(Collectors.joining("; "));
    }
}
