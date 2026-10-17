package com.eu.habbo.imaging.camera.assets;

import java.awt.image.BufferedImage;

/** Where the camera renderer finds the pictures named in the client's scene JSON. */
public interface CameraAssetSource {
    /**
     * The picture for a sprite, plane texture or mask name, unmirrored and padded to the asset's
     * source size, or null when the name is unknown. Callers must not modify the returned image.
     */
    BufferedImage image(String assetName);

    /**
     * The 256 colours of the palette named {@code paletteSource} in the library that holds {@code
     * assetName} (pets), or null.
     */
    default int[] palette(String assetName, String paletteSource) {
        return null;
    }

    /** True when at least one source is configured, so a server render can find any assets. */
    boolean isConfigured();

    /** A short human description for logs. */
    String describe();
}
