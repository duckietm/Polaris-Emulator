package com.eu.habbo.imaging.camera.render;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.awt.image.ColorConvertOp;
import java.awt.image.FilteredImageSource;
import java.awt.image.ImageFilter;
import java.awt.image.ImageProducer;
import java.awt.image.RGBImageFilter;

public class CameraUtils {
    public static void addAlpha(BufferedImage image, BufferedImage mask, int startX, int startY) {
        int width = mask.getWidth();
        int height = mask.getHeight();

        int[] imagePixels = image.getRGB(startX, startY, width, height, null, 0, width);
        int[] maskPixels = mask.getRGB(0, 0, width, height, null, 0, width);

        for (int i = 0; i < imagePixels.length; i++) {
            Color baseColor = new Color(imagePixels[i]);
            Color maskColor = new Color(maskPixels[i]);
            float red = screen(baseColor.getRed() / 255.0F, (maskColor.getRed() / 255.0F));
            float green = screen(baseColor.getGreen() / 255.0F, (maskColor.getGreen() / 255.0F));
            float blue = screen(baseColor.getBlue() / 255.0F, (maskColor.getBlue() / 255.0F));
            imagePixels[i] = new Color(red, green, blue).getRGB();
        }

        image.setRGB(startX, startY, width, height, imagePixels, 0, width);
    }

    public static float screen(float base, float mask) {
        return 1 - ((1 - base) * (1 - mask));
    }

    public static void paletteMap(
            BufferedImage sourceBitmapData, int[] redArray, int[] greenArray, int[] blueArray, int[] alphaArray) {
        int sw = sourceBitmapData.getWidth();
        int sh = sourceBitmapData.getHeight();

        int[] pixels = sourceBitmapData.getRGB(0, 0, sw, sh, null, 0, sw);

        int pixelValue;
        int r;
        int g;
        int b;
        int a;
        int color;
        int c1;
        int c2;
        int c3;
        int c4;

        for (int i = 0; i < pixels.length; i++) {
            pixelValue = pixels[i];
            if (pixelValue >> 24 == 0x00 || pixelValue >> 24 == 0xFF) continue;

            c1 = (alphaArray == null) ? pixelValue & 0xFF000000 : alphaArray[(pixelValue >> 24) & 0xFF];
            c2 = (redArray == null) ? pixelValue & 0x00FF0000 : redArray[(pixelValue >> 16) & 0xFF];
            c3 = (greenArray == null) ? pixelValue & 0x0000FF00 : greenArray[(pixelValue >> 8) & 0xFF];
            c4 = (blueArray == null) ? pixelValue & 0x000000FF : blueArray[(pixelValue) & 0xFF];

            a = ((c1 >> 24) & 0xFF) + ((c2 >> 24) & 0xFF) + ((c3 >> 24) & 0xFF) + ((c4 >> 24) & 0xFF);
            if (a > 0xFF) a = 0xFF;

            r = ((c1 >> 16) & 0xFF) + ((c2 >> 16) & 0xFF) + ((c3 >> 16) & 0xFF) + ((c4 >> 16) & 0xFF);
            if (r > 0xFF) r = 0xFF;

            g = ((c1 >> 8) & 0xFF) + ((c2 >> 8) & 0xFF) + ((c3 >> 8) & 0xFF) + ((c4 >> 8) & 0xFF);
            if (g > 0xFF) g = 0xFF;

            b = ((c1) & 0xFF) + ((c2) & 0xFF) + ((c3) & 0xFF) + ((c4) & 0xFF);
            if (b > 0xFF) b = 0xFF;

            color = a << 24 | r << 16 | g << 8 | b;

            pixels[i] = color;
        }

        sourceBitmapData.setRGB(0, 0, sw, sh, pixels, 0, sw);
    }

    /**
     * Recolours a palette-indexed pet sprite the way the client does: every visible pixel takes the
     * palette colour at the index held in its green channel, keeping its alpha.
     */
    public static void applyGreenIndexedPalette(BufferedImage image, int[] palette) {
        int w = image.getWidth();
        int h = image.getHeight();
        int[] pixels = image.getRGB(0, 0, w, h, null, 0, w);
        for (int i = 0; i < pixels.length; i++) {
            int alpha = pixels[i] >>> 24;
            if (alpha == 0) {
                continue;
            }
            int index = (pixels[i] >> 8) & 0xFF;
            int colour = index < palette.length ? palette[index] : 0;
            pixels[i] = (alpha << 24) | (colour & 0x00FFFFFF);
        }
        image.setRGB(0, 0, w, h, pixels, 0, w);
    }

    public static BufferedImage makeColorTransparent(Image im, final Color color) {
        ImageFilter filter = new RGBImageFilter() {
            public int markerRGB = color.getRGB() | 0xFF000000;

            public final int filterRGB(int x, int y, int rgb) {
                if ((rgb | 0xFF000000) == markerRGB) {
                    return 0x00FFFFFF & rgb;
                } else {
                    return rgb;
                }
            }
        };

        ImageProducer ip = new FilteredImageSource(im.getSource(), filter);
        return toBufferedImage(Toolkit.getDefaultToolkit().createImage(ip));
    }

    public static BufferedImage toBufferedImage(Image img) {
        if (img instanceof BufferedImage) {
            return (BufferedImage) img;
        }

        BufferedImage bimage = new BufferedImage(img.getWidth(null), img.getHeight(null), BufferedImage.TYPE_INT_ARGB);

        Graphics2D bGr = bimage.createGraphics();
        bGr.drawImage(img, 0, 0, null);
        bGr.dispose();

        return bimage;
    }

    public static void recolor(BufferedImage image, Color maskColor) {
        if (image.getColorModel().getPixelSize() < 32) {
            image = convert32(image);
        }
        int w = image.getWidth();
        int h = image.getHeight();
        int[] pixels = new int[w * h];
        image.getRGB(0, 0, w, h, pixels, 0, w);

        int maskR = maskColor.getRed();
        int maskG = maskColor.getGreen();
        int maskB = maskColor.getBlue();
        for (int i = 0; i < pixels.length; i++) {
            int pixel = pixels[i];
            int srcAlpha = (pixel >>> 24) & 0xFF;
            if (srcAlpha == 0) {
                continue;
            }
            int srcR = (pixel >> 16) & 0xFF;
            int srcG = (pixel >> 8) & 0xFF;
            int srcB = pixel & 0xFF;
            int outR = (srcR * maskR) / 0xFF;
            int outG = (srcG * maskG) / 0xFF;
            int outB = (srcB * maskB) / 0xFF;
            // Preserve the source pixel's alpha — using new Color(r,g,b) defaulted alpha
            // to 255, which made the window furni's semi-transparent glass area opaque
            // and painted the entire wall in the glass colour during photo render.
            pixels[i] = (srcAlpha << 24) | (outR << 16) | (outG << 8) | outB;
        }
        image.setRGB(0, 0, w, h, pixels, 0, w);
    }

    public static void rotatePointMatrix(Point[] origPoints, double angle, Point[] storeTo, Point center) {
        AffineTransform.getRotateInstance(Math.toRadians(angle), center.x, center.y)
                .transform(origPoints, 0, storeTo, 0, origPoints.length);
    }

    public static BufferedImage convert32(BufferedImage src) {
        BufferedImage dest = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        ColorConvertOp cco = new ColorConvertOp(
                src.getColorModel().getColorSpace(), dest.getColorModel().getColorSpace(), null);
        return cco.filter(src, dest);
    }

    public static BufferedImage flipHorizontal(BufferedImage image) {
        AffineTransform tx = AffineTransform.getScaleInstance(-1, 1);
        tx.translate(-image.getWidth(null), 0);
        AffineTransformOp op = new AffineTransformOp(tx, AffineTransformOp.TYPE_NEAREST_NEIGHBOR);
        return op.filter(image, null);
    }

    public static BufferedImage resize(BufferedImage image, int newWidth, int newHeight) {
        Image tmp = image.getScaledInstance(newWidth, newHeight, Image.SCALE_SMOOTH);
        BufferedImage dimg = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = dimg.createGraphics();
        g2d.drawImage(tmp, 0, 0, null);
        g2d.dispose();
        return dimg;
    }
}
