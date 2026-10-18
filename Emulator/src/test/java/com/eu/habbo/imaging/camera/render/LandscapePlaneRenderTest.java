package com.eu.habbo.imaging.camera.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eu.habbo.imaging.camera.CameraTestAssets;
import com.eu.habbo.imaging.camera.assets.CameraAssetSource;
import com.google.gson.Gson;
import java.awt.image.BufferedImage;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * A 40x40 photo of one 20x30 wall (screen x 0..19, y 0..29) with a 10x12 window cut by its mask
 * (screen x 5..14, y 4..15) and a landscape behind it: a green colour layer and a red, bottom
 * aligned 20x22 texture layer (screen y 8..29), as AIR sends a landscape plane.
 */
class LandscapePlaneRenderTest {
    private static final int BACKGROUND = 0x333333;
    private static final int WALL = 0x0000FF;
    private static final int SKY = 0x00FF00;
    private static final int HILLS = 0xFF0000;

    private static final Map<String, BufferedImage> ASSETS = Map.of(
            "wall_texture_64_test", CameraTestAssets.image(20, 30, 0xFF000000 | WALL),
            "window_64_test", CameraTestAssets.image(10, 12, 0xFF000000),
            "landscape_64_test_1", CameraTestAssets.image(20, 22, 0xFF000000 | HILLS));

    private static final CameraAssetSource SOURCE = new CameraAssetSource() {
        @Override
        public BufferedImage image(String assetName) {
            return ASSETS.get(assetName);
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

    private static final String CORNERS =
            "\"cornerPoints\":[{\"x\":20,\"y\":30},{\"x\":0,\"y\":30},{\"x\":20,\"y\":0},{\"x\":0,\"y\":0}]";
    private static final String BACKGROUND_PLANE = "{\"z\":99,\"color\":" + BACKGROUND
            + ",\"cornerPoints\":[{\"x\":40,\"y\":40},{\"x\":0,\"y\":40},{\"x\":40,\"y\":0},{\"x\":0,\"y\":0}],\"texCols\":[]}";
    private static final String WALL_PLANE = "{\"z\":10," + CORNERS + ",\"color\":16777215,\"bottomAligned\":false,"
            + "\"texCols\":[{\"assetNames\":[\"wall_texture_64_test\"]}],"
            + "\"masks\":[{\"name\":\"window_64_test\",\"location\":{\"x\":5,\"y\":4},\"flipH\":false,\"flipV\":false}]}";
    private static final String LANDSCAPE_COLOUR = "{\"z\":10.02," + CORNERS + ",\"color\":" + SKY + ",\"texCols\":[]}";
    private static final String LANDSCAPE_TEXTURE =
            "{\"z\":10.02," + CORNERS + ",\"color\":16777215,\"bottomAligned\":true,"
                    + "\"texCols\":[{\"assetNames\":[\"landscape_64_test_1\",\"\"]}]}";

    private static BufferedImage render(String... planes) {
        String json = "{\"planes\":[" + String.join(",", planes) + "],\"sprites\":[],\"filters\":[]}";
        JSONCamera scene = new Gson().fromJson(json, JSONCamera.class);
        return new CameraRender(scene, 40, 40, BACKGROUND, SOURCE, null, "", null, CameraRenderOptions.defaults())
                .render();
    }

    private static int at(BufferedImage image, int x, int y) {
        return image.getRGB(x, y) & 0xFFFFFF;
    }

    @Test
    void theLandscapeShowsThroughTheWindowAndStaysBehindTheWall() {
        // The client lists planes back to front: the landscape layers (just behind) before the wall.
        BufferedImage photo = render(BACKGROUND_PLANE, LANDSCAPE_COLOUR, LANDSCAPE_TEXTURE, WALL_PLANE);

        assertEquals(SKY, at(photo, 9, 5), "the colour layer above the hills");
        assertEquals(SKY, at(photo, 12, 6));
        assertEquals(HILLS, at(photo, 9, 12), "the bottom aligned texture layer");
        assertEquals(HILLS, at(photo, 6, 14));
        assertEquals(WALL, at(photo, 2, 2), "the wall around the window");
        assertEquals(WALL, at(photo, 9, 22), "the landscape under the wall stays hidden");
        assertEquals(WALL, at(photo, 17, 12));
        assertEquals(BACKGROUND, at(photo, 30, 30), "outside the wall");
    }

    @Test
    void withoutALandscapeTheWindowShowsTheBackground() {
        BufferedImage photo = render(BACKGROUND_PLANE, WALL_PLANE);

        assertEquals(BACKGROUND, at(photo, 9, 12));
        assertEquals(WALL, at(photo, 2, 2));
    }

    @Test
    void landscapeLayersAreRecognisedByTheirWall() {
        JSONCamera scene = new Gson()
                .fromJson(
                        "{\"planes\":[" + BACKGROUND_PLANE + "," + LANDSCAPE_COLOUR + "," + LANDSCAPE_TEXTURE + ","
                                + WALL_PLANE + "]}",
                        JSONCamera.class);
        CameraPlaneRenderer planes = new CameraPlaneRenderer(
                scene, 40, 40, new CameraImageLoader(SOURCE, null, CameraRenderOptions.defaults()), "", null);
        CameraPlane[] all = scene.getPlanes();

        assertFalse(planes.isLandscapeLayer(all[0]), "the background plane");
        assertTrue(planes.isLandscapeLayer(all[1]), "the untextured colour layer behind a wall");
        assertTrue(planes.isLandscapeLayer(all[2]));
        assertFalse(planes.isLandscapeLayer(all[3]), "the wall itself");
        assertEquals(java.util.List.of(all[1], all[2]), planes.getLandscapeLayersBehind(all[3]));
    }
}
