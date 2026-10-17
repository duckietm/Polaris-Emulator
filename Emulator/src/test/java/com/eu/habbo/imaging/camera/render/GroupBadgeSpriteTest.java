package com.eu.habbo.imaging.camera.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eu.habbo.imaging.camera.CameraTestAssets;
import com.eu.habbo.imaging.camera.assets.GroupBadgeAssetSource;
import com.google.gson.Gson;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class GroupBadgeSpriteTest {
    private static final int BACKGROUND = 0x101010;

    /** A 39x39 badge: red with a green top-left quarter, so scaling and tinting both show. */
    private static BufferedImage badge() {
        BufferedImage badge = CameraTestAssets.image(39, 39, CameraTestAssets.RED);
        for (int x = 0; x < 19; x++) {
            for (int y = 0; y < 19; y++) {
                badge.setRGB(x, y, CameraTestAssets.GREEN);
            }
        }
        return badge;
    }

    private static BufferedImage render(GroupBadgeAssetSource.BadgeComposer composer, String spriteJson) {
        CameraImageLoader loader = new CameraImageLoader(
                new GroupBadgeAssetSource(composer, 1024 * 1024), null, CameraRenderOptions.defaults());
        CameraSpriteRenderer renderer = new CameraSpriteRenderer(loader);
        BufferedImage photo = new BufferedImage(40, 40, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = photo.createGraphics();
        graphics.setColor(new java.awt.Color(BACKGROUND));
        graphics.fillRect(0, 0, 40, 40);
        CameraRenderUtils.applyPixelRenderingHints(graphics);
        renderer.renderSingleSprite(graphics, new Gson().fromJson(spriteJson, CameraSprite.class));
        graphics.dispose();
        return photo;
    }

    @Test
    void aGroupBadgeIsDrawnFromItsCodeAtTheSizeTheClientDrewIt() {
        AtomicInteger calls = new AtomicInteger();
        GroupBadgeAssetSource.BadgeComposer composer = code -> {
            calls.incrementAndGet();
            return "b001034s002044".equals(code) ? badge() : null;
        };

        // The average colour (dark grey) is not a tint: the badge keeps its own colours.
        BufferedImage photo = render(
                composer,
                "{\"name\":\"group_badge:b001034s002044\",\"x\":2,\"y\":3,\"z\":0,"
                        + "\"width\":20,\"height\":20,\"color\":4210752}");

        assertEquals(CameraTestAssets.GREEN & 0xFFFFFF, photo.getRGB(2, 3) & 0xFFFFFF);
        assertEquals(CameraTestAssets.GREEN & 0xFFFFFF, photo.getRGB(10, 11) & 0xFFFFFF);
        assertEquals(CameraTestAssets.RED & 0xFFFFFF, photo.getRGB(21, 22) & 0xFFFFFF, "scaled to 20x20");
        assertEquals(BACKGROUND, photo.getRGB(22, 23) & 0xFFFFFF, "nothing past 20x20");
        assertEquals(1, calls.get());
    }

    @Test
    void aBadgeThatCannotBeDrawnShowsItsAverageColourBlock() {
        BufferedImage photo = render(
                code -> null,
                "{\"name\":\"group_badge:b001034\",\"x\":0,\"y\":0,\"z\":0,\"width\":5,\"height\":4,\"color\":4210752}");

        assertEquals(0x404040, photo.getRGB(0, 0) & 0xFFFFFF);
        assertEquals(0x404040, photo.getRGB(4, 3) & 0xFFFFFF);
        assertEquals(BACKGROUND, photo.getRGB(5, 3) & 0xFFFFFF);
        assertEquals(BACKGROUND, photo.getRGB(4, 4) & 0xFFFFFF);
    }

    @Test
    void anUnfetchedUrlImageShowsItsAverageColourAndAFurniSpriteKeepsItsTint() {
        // Fetching is off in the default options, so the URL image falls back to its block.
        BufferedImage photo = render(
                code -> null,
                "{\"name\":\"http://images.example/photo.png\",\"x\":1,\"y\":1,\"z\":0,"
                        + "\"width\":3,\"height\":3,\"color\":16711680}");
        assertEquals(0xFF0000, photo.getRGB(2, 2) & 0xFFFFFF);

        CameraSprite tinted = new Gson().fromJson("{\"name\":\"chair_64_a_0_0\",\"color\":255}", CameraSprite.class);
        assertTrue(!tinted.colorIsAverage(), "a furni colour stays a tint");
        assertNull(CameraSpriteRenderer.averageColorBlock(tinted));
    }

    @Test
    void malformedBadgeNamesAreNotComposed() {
        AtomicInteger calls = new AtomicInteger();
        GroupBadgeAssetSource source = new GroupBadgeAssetSource(
                code -> {
                    calls.incrementAndGet();
                    return badge();
                },
                1024 * 1024);

        assertNull(source.image("group_badge:../../etc/passwd"));
        assertNull(source.image("group_badge:"));
        assertNull(source.image("b001034"));
        assertEquals(0, calls.get());
        assertEquals(false, source.isConfigured(), "badges alone do not make server rendering available");
        source.image("group_badge:b001034");
        source.image("group_badge:b001034");
        assertEquals(1, calls.get(), "a badge is composed once and then cached");
    }
}
