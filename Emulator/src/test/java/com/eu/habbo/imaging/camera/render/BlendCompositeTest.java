package com.eu.habbo.imaging.camera.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.eu.habbo.imaging.camera.CameraTestAssets;
import com.eu.habbo.imaging.camera.assets.CameraAssetSource;
import com.google.gson.Gson;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;

class BlendCompositeTest {
    private static final int DST = 0xFF6496C8; // (100, 150, 200)
    private static final int SRC = 0xFFC86432; // (200, 100, 50)

    /** Draws a 2x2 sprite of {@code SRC} through the sprite renderer onto an opaque {@code DST} photo. */
    private static int renderPixel(String blendMode, Integer alpha, int srcArgb) {
        BufferedImage spriteImage = CameraTestAssets.image(2, 2, srcArgb);
        CameraAssetSource source = new CameraAssetSource() {
            @Override
            public BufferedImage image(String assetName) {
                return "lamp_64_b_0_0".equals(assetName) ? spriteImage : null;
            }

            @Override
            public boolean isConfigured() {
                return true;
            }

            @Override
            public String describe() {
                return "test";
            }
        };
        CameraSpriteRenderer renderer =
                new CameraSpriteRenderer(new CameraImageLoader(source, null, CameraRenderOptions.defaults()));
        String json = "{\"name\":\"lamp_64_b_0_0\",\"x\":1,\"y\":1,\"z\":0"
                + (blendMode == null ? "" : ",\"blendMode\":\"" + blendMode + "\"")
                + (alpha == null ? "" : ",\"alpha\":" + alpha) + "}";
        CameraSprite sprite = new Gson().fromJson(json, CameraSprite.class);

        BufferedImage photo = CameraTestAssets.image(4, 4, DST);
        BufferedImage rgb = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = rgb.createGraphics();
        graphics.drawImage(photo, 0, 0, null);
        CameraRenderUtils.applyPixelRenderingHints(graphics);
        renderer.renderSingleSprite(graphics, sprite);
        graphics.dispose();
        assertEquals(DST & 0xFFFFFF, rgb.getRGB(0, 0) & 0xFFFFFF, "outside the sprite stays untouched");
        return rgb.getRGB(2, 2) & 0xFFFFFF;
    }

    private static int rgb(int r, int g, int b) {
        return (r << 16) | (g << 8) | b;
    }

    @Test
    void addAndSubtract() {
        assertEquals(rgb(255, 250, 250), renderPixel("add", null, SRC));
        assertEquals(rgb(0, 50, 150), renderPixel("subtract", null, SRC));
        // Half alpha: d + s * a with a = 128/255.
        assertEquals(rgb(200, 200, 225), renderPixel("add", 128, SRC));
        assertEquals(rgb(0, 100, 175), renderPixel("subtract", 128, SRC));
    }

    @Test
    void darkenLightenAndDifference() {
        assertEquals(rgb(100, 100, 50), renderPixel("darken", null, SRC));
        assertEquals(rgb(200, 150, 200), renderPixel("lighten", null, SRC));
        assertEquals(rgb(100, 50, 150), renderPixel("difference", null, SRC));
        // Half alpha mixes the blended colour in: 150 + (100 - 150) * 128/255 = 125.
        assertEquals(rgb(100, 125, 125), renderPixel("darken", 128, SRC));
    }

    @Test
    void multiplyScreenOverlayAndHardLight() {
        assertEquals(rgb(78, 59, 39), renderPixel("multiply", null, SRC));
        assertEquals(rgb(222, 191, 211), renderPixel("screen", null, SRC));
        assertEquals(rgb(157, 127, 167), renderPixel("overlay", null, SRC));
        assertEquals(rgb(188, 118, 78), renderPixel("hardlight", null, SRC));
    }

    @Test
    void normalAndUnknownModesDrawWithPlainAlpha() {
        assertEquals(rgb(200, 100, 50), renderPixel(null, null, SRC));
        assertEquals(rgb(200, 100, 50), renderPixel("normal", null, SRC));
        assertEquals(rgb(200, 100, 50), renderPixel("erase", null, SRC));
        assertNull(BlendComposite.Mode.fromName("normal"));
    }

    @Test
    void transparentSourcePixelsLeaveThePhoto() {
        assertEquals(DST & 0xFFFFFF, renderPixel("add", null, 0x00FFFFFF));
        assertEquals(DST & 0xFFFFFF, renderPixel("subtract", null, 0x00FFFFFF));
    }

    @Test
    void overATransparentLayerTheSourceShowsAsDrawn() {
        // A sprite drawn into an empty (transparent) layer, as the doorway pass does.
        int out = BlendComposite.blendPixel(BlendComposite.Mode.DARKEN, 0x00000000, true, SRC, 1f);
        assertEquals(SRC, out);
        int add = BlendComposite.blendPixel(BlendComposite.Mode.ADD, 0x00000000, true, SRC, 1f);
        assertEquals(SRC, add);
    }
}
