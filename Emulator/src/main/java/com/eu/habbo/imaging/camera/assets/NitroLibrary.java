package com.eu.habbo.imaging.camera.assets;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.Map;

/**
 * One asset library as the client loads it: the asset definitions (offsets, {@code source}
 * aliases and flips), the sheet frames and the palettes, plus the decoded sheet image.
 *
 * <p>{@link #image(String)} returns the picture the client puts on screen for an asset name: an
 * alias resolves to its source's frame, and a trimmed frame is padded back to its {@code
 * sourceSize}, so the image lines up with the sprite's x/y exactly as the client drew it. The
 * image is not mirrored: the client copies an asset's flip onto the sprite ({@code flipH} in the
 * camera JSON), and the renderer mirrors from that flag.
 */
public final class NitroLibrary {
    /** One entry of the library's {@code assets} map. */
    public record AssetDefinition(String source, int x, int y, boolean flipH, boolean flipV, boolean usesPalette) {}

    /** One {@code spritesheet.frames} entry. */
    public record Frame(
            int x,
            int y,
            int width,
            int height,
            boolean rotated,
            boolean trimmed,
            int offsetX,
            int offsetY,
            int sourceWidth,
            int sourceHeight) {}

    /** One {@code palettes} entry: its {@code source} name and 256 opaque RGB colours. */
    public record Palette(String source, int[] rgb) {}

    /** The resolved picture of an asset with the definition it came from. */
    public record ResolvedAsset(String name, String source, BufferedImage image, AssetDefinition definition) {}

    /** Reads an image for an asset name that has no sheet frame (a library kept as loose files). */
    @FunctionalInterface
    public interface LooseImageReader {
        BufferedImage read(String libraryName, String source);
    }

    private final String name;
    private final Map<String, AssetDefinition> assets;
    private final Map<String, Frame> frames;
    private final Map<String, Palette> palettes;
    private final BufferedImage sheet;
    private final LooseImageReader looseImages;
    private final long weight;

    public NitroLibrary(
            String name,
            Map<String, AssetDefinition> assets,
            Map<String, Frame> frames,
            Map<String, Palette> palettes,
            BufferedImage sheet,
            LooseImageReader looseImages) {
        this.name = name == null ? "" : name;
        this.assets = assets == null ? Map.of() : Collections.unmodifiableMap(assets);
        this.frames = frames == null ? Map.of() : Collections.unmodifiableMap(frames);
        this.palettes = palettes == null ? Map.of() : Collections.unmodifiableMap(palettes);
        this.sheet = sheet;
        this.looseImages = looseImages;
        long sheetBytes = sheet == null ? 0 : (long) sheet.getWidth() * sheet.getHeight() * 4L;
        this.weight = sheetBytes + 256L * (this.assets.size() + this.frames.size()) + 1024L * this.palettes.size();
    }

    public String name() {
        return this.name;
    }

    public Map<String, AssetDefinition> assets() {
        return this.assets;
    }

    public boolean hasAsset(String assetName) {
        return this.assets.containsKey(assetName) || this.frameFor(assetName) != null;
    }

    /** Approximate memory held by this library, for the cache budget. */
    public long weight() {
        return this.weight;
    }

    public BufferedImage image(String assetName) {
        ResolvedAsset resolved = this.resolve(assetName);
        return resolved == null ? null : resolved.image();
    }

    public ResolvedAsset resolve(String assetName) {
        if (assetName == null || assetName.isEmpty()) {
            return null;
        }
        AssetDefinition definition = this.assets.get(assetName);
        String source = definition != null
                        && definition.source() != null
                        && !definition.source().isEmpty()
                ? definition.source()
                : assetName;

        BufferedImage image = null;
        Frame frame = this.frameFor(source);
        if (frame != null) {
            image = this.slice(frame);
        } else if (this.looseImages != null) {
            image = this.looseImages.read(this.name, source);
        }
        if (image == null) {
            return null;
        }
        return new ResolvedAsset(assetName, source, image, definition);
    }

    /** The palette whose {@code source} (or key) is {@code paletteSource}, or null. */
    public Palette palette(String paletteSource) {
        if (paletteSource == null || paletteSource.isEmpty()) {
            return null;
        }
        Palette byKey = this.palettes.get(paletteSource);
        if (byKey != null) {
            return byKey;
        }
        for (Palette palette : this.palettes.values()) {
            if (paletteSource.equals(palette.source())) {
                return palette;
            }
        }
        return null;
    }

    private Frame frameFor(String source) {
        Frame frame = this.frames.get(this.name + "_" + source);
        if (frame == null) {
            frame = this.frames.get(source);
        }
        return frame;
    }

    private BufferedImage slice(Frame frame) {
        if (this.sheet == null || frame.rotated() || frame.width() <= 0 || frame.height() <= 0) {
            return null;
        }
        int x = Math.max(0, frame.x());
        int y = Math.max(0, frame.y());
        int width = Math.min(frame.width(), this.sheet.getWidth() - x);
        int height = Math.min(frame.height(), this.sheet.getHeight() - y);
        if (width <= 0 || height <= 0) {
            return null;
        }

        int outWidth = width;
        int outHeight = height;
        int drawX = 0;
        int drawY = 0;
        if (frame.trimmed() && frame.sourceWidth() > 0 && frame.sourceHeight() > 0) {
            outWidth = frame.sourceWidth();
            outHeight = frame.sourceHeight();
            drawX = frame.offsetX();
            drawY = frame.offsetY();
        }

        BufferedImage out = new BufferedImage(outWidth, outHeight, BufferedImage.TYPE_INT_ARGB);
        int[] pixels = this.sheet.getRGB(x, y, width, height, null, 0, width);
        int copyX0 = Math.max(0, -drawX);
        int copyY0 = Math.max(0, -drawY);
        int copyX1 = Math.min(width, outWidth - drawX);
        int copyY1 = Math.min(height, outHeight - drawY);
        if (copyX1 <= copyX0 || copyY1 <= copyY0) {
            return out;
        }
        if (copyX0 == 0 && copyY0 == 0 && copyX1 == width && copyY1 == height) {
            out.setRGB(drawX, drawY, width, height, pixels, 0, width);
        } else {
            Graphics2D graphics = out.createGraphics();
            try {
                BufferedImage region = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                region.setRGB(0, 0, width, height, pixels, 0, width);
                graphics.drawImage(region, drawX, drawY, null);
            } finally {
                graphics.dispose();
            }
        }
        return out;
    }
}
