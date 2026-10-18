package com.eu.habbo.imaging.camera.render;

/**
 * One entry of the scene's {@code sprites} array, as the client writes it: name, x, y, z and,
 * only when they differ from the default, alpha (0..255), flipH, skew, frame, color (an RGB int),
 * blendMode, width/height (external images) and posture.
 */
public class CameraSprite implements Comparable<CameraSprite> {
    private String name;
    private int x;
    private int color = -1;
    private int y;
    private double z;
    private Integer alpha;
    private boolean flipH;
    private double skew;
    private String blendMode = "";
    private String paletteSourceName = "";
    private int width;
    private int height;

    public String getName() {
        return this.name;
    }

    public int getX() {
        return this.x;
    }

    public int getColor() {
        return this.color;
    }

    public int getY() {
        return this.y;
    }

    public double getZ() {
        return this.z;
    }

    public Integer getAlpha() {
        return this.alpha;
    }

    public boolean isFlipH() {
        return this.flipH;
    }

    public String getBlendMode() {
        return this.blendMode == null ? "" : this.blendMode;
    }

    public String getPaletteSourceName() {
        return this.paletteSourceName == null ? "" : this.paletteSourceName;
    }

    /** Width the client drew an external image at, or 0. */
    public int getWidth() {
        return this.width;
    }

    /** Height the client drew an external image at, or 0. */
    public int getHeight() {
        return this.height;
    }

    public boolean isFromUrl() {
        return this.name != null && this.name.contains("//");
    }

    /** A group badge our client names by its code ({@code group_badge:<code>}), drawn by the server. */
    public boolean isGroupBadge() {
        return this.name != null && this.name.startsWith("group_badge:");
    }

    /** An image drawn at the {@code width}/{@code height} the client sent: a URL or a group badge. */
    public boolean isExternalImage() {
        return this.isFromUrl() || this.isGroupBadge();
    }

    /**
     * True when {@code color} is the picture's average colour rather than a tint: the client (AIR
     * RoomObjectCache.getSortableSpriteList) replaces the colour of skewed thumbnails and of small
     * external images and badges with their average colour, for a server that draws them as a block.
     */
    public boolean colorIsAverage() {
        return this.skew != 0 || this.isExternalImage();
    }

    public double getSkew() {
        return this.skew;
    }

    @Override
    public int compareTo(CameraSprite o) {
        int result = (o.getY() - this.getY());

        if (result == 0) {
            result = o.getX() - this.getX();
        }

        if (result == 0) {
            return Double.compare(o.getZ(), this.getZ());
        }

        return result;
    }

    @Override
    public boolean equals(Object other) {
        return this == other;
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(this);
    }
}
