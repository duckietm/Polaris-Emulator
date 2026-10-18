package com.eu.habbo.imaging.camera.render;

import java.awt.Composite;
import java.awt.CompositeContext;
import java.awt.RenderingHints;
import java.awt.image.ColorModel;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.util.Locale;

/**
 * Draws a sprite with the blend mode the client drew it with. Our furniture visualizations emit
 * {@code add}, {@code subtract} and {@code darken} (layer {@code ink}); the official client may also
 * send Flash's {@code multiply}, {@code screen}, {@code lighten}, {@code difference}, {@code
 * overlay} and {@code hardlight}.
 *
 * <p>The maths follows Pixi/WebGL and Flash on a premultiplied source with alpha {@code a} (pixel
 * alpha times sprite alpha): {@code add} is {@code d + s*a}, {@code subtract} is {@code d - s*a},
 * and the separable modes mix the blended colour {@code B(d, s)} in by {@code a}: {@code d + (B -
 * d) * a}. Over a transparent destination pixel the source is drawn normally.
 */
final class BlendComposite implements Composite {
    enum Mode {
        ADD,
        SUBTRACT,
        MULTIPLY,
        SCREEN,
        DARKEN,
        LIGHTEN,
        DIFFERENCE,
        OVERLAY,
        HARDLIGHT;

        /** The mode for a scene {@code blendMode}, or null for normal drawing (and unknown names). */
        static Mode fromName(String name) {
            if (name == null) {
                return null;
            }
            return switch (name.trim().toLowerCase(Locale.ROOT)) {
                case "add" -> ADD;
                case "subtract" -> SUBTRACT;
                case "multiply" -> MULTIPLY;
                case "screen" -> SCREEN;
                case "darken" -> DARKEN;
                case "lighten" -> LIGHTEN;
                case "difference" -> DIFFERENCE;
                case "overlay" -> OVERLAY;
                case "hardlight", "hard-light" -> HARDLIGHT;
                default -> null;
            };
        }
    }

    private final Mode mode;
    private final float alpha;

    BlendComposite(Mode mode, float alpha) {
        this.mode = mode;
        this.alpha = Math.max(0f, Math.min(1f, alpha));
    }

    Mode mode() {
        return this.mode;
    }

    @Override
    public CompositeContext createContext(ColorModel srcColorModel, ColorModel dstColorModel, RenderingHints hints) {
        return new Context(this.mode, this.alpha, srcColorModel, dstColorModel);
    }

    private static float separable(Mode mode, float d, float s) {
        return switch (mode) {
            case MULTIPLY -> d * s / 255f;
            case SCREEN -> 255f - (255f - d) * (255f - s) / 255f;
            case DARKEN -> Math.min(d, s);
            case LIGHTEN -> Math.max(d, s);
            case DIFFERENCE -> Math.abs(d - s);
            case OVERLAY -> hardLight(s, d);
            case HARDLIGHT -> hardLight(d, s);
            default -> s;
        };
    }

    /** Hard light of {@code s} over {@code d}; overlay is hard light with the two swapped. */
    private static float hardLight(float d, float s) {
        return s < 128f ? 2f * d * s / 255f : 255f - 2f * (255f - d) * (255f - s) / 255f;
    }

    private static final class Context implements CompositeContext {
        private final Mode mode;
        private final float alpha;
        private final ColorModel srcModel;
        private final ColorModel dstModel;

        Context(Mode mode, float alpha, ColorModel srcModel, ColorModel dstModel) {
            this.mode = mode;
            this.alpha = alpha;
            this.srcModel = srcModel;
            this.dstModel = dstModel;
        }

        @Override
        public void compose(Raster src, Raster dstIn, WritableRaster dstOut) {
            int width = Math.min(src.getWidth(), dstIn.getWidth());
            int height = Math.min(src.getHeight(), dstIn.getHeight());
            Object srcPixel = null;
            Object dstPixel = null;
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    srcPixel = src.getDataElements(x, y, srcPixel);
                    int s = this.srcModel.getRGB(srcPixel);
                    float a = ((s >>> 24) & 0xFF) / 255f * this.alpha;
                    if (a <= 0f) {
                        if (dstIn != dstOut) {
                            dstPixel = dstIn.getDataElements(x, y, dstPixel);
                            dstOut.setDataElements(x, y, dstPixel);
                        }
                        continue;
                    }
                    dstPixel = dstIn.getDataElements(x, y, dstPixel);
                    int d = this.dstModel.getRGB(dstPixel);
                    int out = blendPixel(this.mode, d, this.dstModel.hasAlpha(), s, a);
                    dstPixel = this.dstModel.getDataElements(out, dstPixel);
                    dstOut.setDataElements(x, y, dstPixel);
                }
            }
        }

        @Override
        public void dispose() {}
    }

    /**
     * {@code s} (ARGB, unpremultiplied) drawn with coverage {@code a} onto {@code d}; the result is
     * composited as W3C compositing does it: the blended colour where the destination is opaque, the
     * plain source where it is transparent, then source-over.
     */
    static int blendPixel(Mode mode, int d, boolean dstHasAlpha, int s, float a) {
        float da = dstHasAlpha ? ((d >>> 24) & 0xFF) / 255f : 1f;
        float outA = a + da * (1f - a);
        if (outA <= 0f) {
            return 0;
        }
        int result = 0;
        for (int shift = 16; shift >= 0; shift -= 8) {
            float sc = (s >> shift) & 0xFF;
            float dc = (d >> shift) & 0xFF;
            float premultiplied;
            if (mode == Mode.ADD) {
                premultiplied = a * sc + da * dc;
            } else if (mode == Mode.SUBTRACT) {
                premultiplied = da * dc - a * sc;
            } else {
                float mixed = (1f - da) * sc + da * separable(mode, dc, sc);
                premultiplied = a * mixed + (1f - a) * da * dc;
            }
            int c = Math.round(Math.max(0f, Math.min(255f, premultiplied / outA)));
            result |= c << shift;
        }
        int alphaByte = dstHasAlpha ? Math.round(outA * 255f) : 0xFF;
        return (alphaByte << 24) | result;
    }
}
