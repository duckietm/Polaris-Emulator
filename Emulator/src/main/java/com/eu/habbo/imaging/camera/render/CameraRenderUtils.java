package com.eu.habbo.imaging.camera.render;

import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

final class CameraRenderUtils {
    private CameraRenderUtils() {}

    static void applyPixelRenderingHints(java.awt.Graphics2D target) {
        target.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        target.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        target.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_SPEED);
        target.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
        target.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
    }

    static Rectangle expandRectangle(Rectangle bounds, int padding) {
        if (bounds == null || padding <= 0) {
            return bounds;
        }
        return new Rectangle(
                bounds.x - padding, bounds.y - padding, bounds.width + (padding * 2), bounds.height + (padding * 2));
    }

    static Rectangle getAlphaBounds(BufferedImage image) {
        int minX = image.getWidth();
        int minY = image.getHeight();
        int maxX = -1;
        int maxY = -1;

        for (int x = 0; x < image.getWidth(); x++) {
            for (int y = 0; y < image.getHeight(); y++) {
                if (((image.getRGB(x, y) >>> 24) & 0xFF) == 0) {
                    continue;
                }
                minX = Math.min(minX, x);
                minY = Math.min(minY, y);
                maxX = Math.max(maxX, x);
                maxY = Math.max(maxY, y);
            }
        }

        if (maxX < minX || maxY < minY) {
            return new Rectangle();
        }

        return new Rectangle(minX, minY, maxX - minX + 1, maxY - minY + 1);
    }

    static BufferedImage expandMask(BufferedImage sourceMask, Rectangle bounds, int radius) {
        if (radius <= 0) {
            return sourceMask;
        }

        int w = sourceMask.getWidth();
        int h = sourceMask.getHeight();
        int[] sourcePixels = new int[w * h];
        sourceMask.getRGB(0, 0, w, h, sourcePixels, 0, w);

        int x0 = Math.max(0, bounds.x);
        int y0 = Math.max(0, bounds.y);
        int x1 = Math.min(w, bounds.x + bounds.width);
        int y1 = Math.min(h, bounds.y + bounds.height);

        int hStart = Math.max(0, y0 - radius);
        int hEnd = Math.min(h, y1 + radius);

        boolean[] horizontal = new boolean[w * h];
        for (int y = hStart; y < hEnd; y++) {
            int rowOffset = y * w;
            for (int x = x0; x < x1; x++) {
                int xMin = Math.max(0, x - radius);
                int xMax = Math.min(w - 1, x + radius);
                for (int sx = xMin; sx <= xMax; sx++) {
                    if (((sourcePixels[rowOffset + sx] >>> 24) & 0xFF) > 0) {
                        horizontal[rowOffset + x] = true;
                        break;
                    }
                }
            }
        }

        int[] outPixels = new int[w * h];
        for (int x = x0; x < x1; x++) {
            for (int y = y0; y < y1; y++) {
                int yMin = Math.max(0, y - radius);
                int yMax = Math.min(h - 1, y + radius);
                for (int sy = yMin; sy <= yMax; sy++) {
                    if (horizontal[sy * w + x]) {
                        outPixels[y * w + x] = 0xFFFFFFFF;
                        break;
                    }
                }
            }
        }

        BufferedImage expandedMask = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        expandedMask.setRGB(0, 0, w, h, outPixels, 0, w);
        return expandedMask;
    }

    static double rectangleCenterDistanceSquared(Rectangle first, Rectangle second) {
        if (first == null || second == null) {
            return 0.0;
        }

        double centerXFirst = first.getCenterX();
        double centerYFirst = first.getCenterY();
        double centerXSecond = second.getCenterX();
        double centerYSecond = second.getCenterY();
        double dx = centerXFirst - centerXSecond;
        double dy = centerYFirst - centerYSecond;
        return (dx * dx) + (dy * dy);
    }

    static void drawMaskedLayer(
            java.awt.Graphics2D graphics,
            BufferedImage layer,
            BufferedImage maskAlpha,
            Rectangle maskBounds,
            int width,
            int height) {
        int x0 = Math.max(0, maskBounds.x);
        int y0 = Math.max(0, maskBounds.y);
        int x1 = Math.min(width, maskBounds.x + maskBounds.width);
        int y1 = Math.min(height, maskBounds.y + maskBounds.height);
        int rw = x1 - x0;
        int rh = y1 - y0;
        if (rw <= 0 || rh <= 0) {
            return;
        }

        int[] layerPixels = new int[rw * rh];
        layer.getRGB(x0, y0, rw, rh, layerPixels, 0, rw);
        int[] maskPixels = new int[rw * rh];
        maskAlpha.getRGB(x0, y0, rw, rh, maskPixels, 0, rw);

        int[] clippedPixels = new int[rw * rh];
        for (int i = 0; i < layerPixels.length; i++) {
            int layerPixel = layerPixels[i];
            int layerAlpha = (layerPixel >>> 24) & 0xFF;
            if (layerAlpha == 0) {
                continue;
            }
            int maskAlphaValue = (maskPixels[i] >>> 24) & 0xFF;
            if (maskAlphaValue == 0) {
                continue;
            }
            int alpha = (layerAlpha * maskAlphaValue) / 255;
            clippedPixels[i] = (layerPixel & 0x00FFFFFF) | (alpha << 24);
        }

        BufferedImage clippedLayer = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        clippedLayer.setRGB(x0, y0, rw, rh, clippedPixels, 0, rw);
        graphics.drawImage(clippedLayer, 0, 0, null);
    }

    static int clampByte(float v) {
        if (v <= 0f) {
            return 0;
        }
        if (v >= 255f) {
            return 255;
        }
        return Math.round(v);
    }
}
