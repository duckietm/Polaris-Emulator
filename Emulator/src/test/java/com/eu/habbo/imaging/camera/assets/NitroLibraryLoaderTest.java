package com.eu.habbo.imaging.camera.assets;

import static com.eu.habbo.imaging.camera.CameraTestAssets.BLUE;
import static com.eu.habbo.imaging.camera.CameraTestAssets.GREEN;
import static com.eu.habbo.imaging.camera.CameraTestAssets.RED;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eu.habbo.imaging.camera.CameraTestAssets;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NitroLibraryLoaderTest {
    private static final NitroLibraryLoader LOADER = new NitroLibraryLoader(1024 * 1024, 1024 * 1024);

    /** An 8x4 sheet: a red 2x2 block at (0,0), a green 3x2 block at (2,0), blue elsewhere transparent. */
    static BufferedImage sheet() {
        BufferedImage sheet = new BufferedImage(8, 4, BufferedImage.TYPE_INT_ARGB);
        for (int x = 0; x < 2; x++) for (int y = 0; y < 2; y++) sheet.setRGB(x, y, RED);
        for (int x = 2; x < 5; x++) for (int y = 0; y < 2; y++) sheet.setRGB(x, y, GREEN);
        sheet.setRGB(7, 3, BLUE);
        return sheet;
    }

    static final String CHAIR_JSON = "{\"name\":\"chair\",\"assets\":{"
            + "\"chair_64_a_0_0\":{\"x\":-3,\"y\":-4},"
            + "\"chair_64_a_2_0\":{\"source\":\"chair_64_a_0_0\",\"x\":3,\"y\":-4,\"flipH\":true},"
            + "\"chair_64_b_0_0\":{\"x\":0,\"y\":0}},"
            + "\"palettes\":{\"1\":{\"id\":1,\"source\":\"chair_palette1\",\"rgb\":[[1,2,3],[4,5,6]]}},"
            + "\"spritesheet\":{\"meta\":{\"image\":\"chair.png\"},\"frames\":{"
            + "\"chair_chair_64_a_0_0.png\":{\"frame\":{\"x\":0,\"y\":0,\"w\":2,\"h\":2},\"rotated\":false,"
            + "\"trimmed\":true,\"spriteSourceSize\":{\"x\":1,\"y\":3,\"w\":2,\"h\":2},\"sourceSize\":{\"w\":5,\"h\":6}},"
            + "\"chair_chair_64_b_0_0.png\":{\"frame\":{\"x\":2,\"y\":0,\"w\":3,\"h\":2},\"rotated\":false,"
            + "\"trimmed\":false,\"spriteSourceSize\":{\"x\":0,\"y\":0,\"w\":3,\"h\":2},\"sourceSize\":{\"w\":3,\"h\":2}}"
            + "}}}";

    @Test
    void anUntrimmedFrameIsSlicedAsIs() throws IOException {
        NitroLibrary library = LOADER.readBundle(CameraTestAssets.nitro("chair", CHAIR_JSON, sheet()), "chair");

        BufferedImage image = library.image("chair_64_b_0_0");
        assertNotNull(image);
        assertEquals(3, image.getWidth());
        assertEquals(2, image.getHeight());
        assertEquals(GREEN, image.getRGB(0, 0));
        assertEquals(GREEN, image.getRGB(2, 1));
    }

    @Test
    void aTrimmedFrameIsPaddedBackToItsSourceSize() throws IOException {
        NitroLibrary library = LOADER.readBundle(CameraTestAssets.nitro("chair", CHAIR_JSON, sheet()), "chair");

        BufferedImage image = library.image("chair_64_a_0_0");
        assertEquals(5, image.getWidth());
        assertEquals(6, image.getHeight());
        assertEquals(0, image.getRGB(0, 0) >>> 24, "padding stays transparent");
        assertEquals(0, image.getRGB(0, 3) >>> 24);
        assertEquals(RED, image.getRGB(1, 3));
        assertEquals(RED, image.getRGB(2, 4));
        assertEquals(0, image.getRGB(3, 4) >>> 24);
    }

    @Test
    void anAliasResolvesToItsSourceUnmirroredAndKeepsItsFlip() throws IOException {
        NitroLibrary library = LOADER.readBundle(CameraTestAssets.nitro("chair", CHAIR_JSON, sheet()), "chair");

        NitroLibrary.ResolvedAsset alias = library.resolve("chair_64_a_2_0");
        assertNotNull(alias);
        assertEquals("chair_64_a_0_0", alias.source());
        assertTrue(alias.definition().flipH(), "the flip is kept as metadata; the sprite's flipH mirrors it");
        BufferedImage source = library.image("chair_64_a_0_0");
        for (int x = 0; x < source.getWidth(); x++) {
            for (int y = 0; y < source.getHeight(); y++) {
                assertEquals(source.getRGB(x, y), alias.image().getRGB(x, y));
            }
        }
    }

    @Test
    void unknownNamesAndPalettes() throws IOException {
        NitroLibrary library = LOADER.readBundle(CameraTestAssets.nitro("chair", CHAIR_JSON, sheet()), "chair");

        assertNull(library.image("chair_64_c_0_0"));
        assertFalse(library.hasAsset("table_64_a_0_0"));
        NitroLibrary.Palette palette = library.palette("chair_palette1");
        assertNotNull(palette);
        assertEquals(0xFF010203, palette.rgb()[0]);
        assertEquals(0xFF040506, palette.rgb()[1]);
        assertEquals(0xFF000000, palette.rgb()[2]);
        assertArrayEquals(palette.rgb(), library.palette("1").rgb());
    }

    @Test
    void aRotatedFrameIsNotGuessed() throws IOException {
        String json = CHAIR_JSON.replace("\"rotated\":false,\"trimmed\":false", "\"rotated\":true,\"trimmed\":false");
        NitroLibrary library = LOADER.readBundle(CameraTestAssets.nitro("chair", json, sheet()), "chair");

        assertNull(library.image("chair_64_b_0_0"));
    }

    @Test
    void aTruncatedBundleIsRefused() throws IOException {
        byte[] bundle = CameraTestAssets.nitro("chair", CHAIR_JSON, sheet());
        byte[] truncated = java.util.Arrays.copyOf(bundle, bundle.length - 10);

        assertThrows(IOException.class, () -> LOADER.readBundle(truncated, "chair"));
    }

    @Test
    void aFolderLibraryReadsLooseImagesLikeTheClientRoomFolder(@TempDir Path dir) throws IOException {
        Path room = dir.resolve("room");
        Files.createDirectories(room.resolve("images"));
        Files.writeString(
                room.resolve("room.asset.json"),
                "{\"name\":\"room\",\"assets\":{\"wall_texture_64_0_wall_white\":{\"x\":0,\"y\":0},"
                        + "\"door_64_flipH\":{\"source\":\"door_64\",\"flipH\":true}}}");
        CameraTestAssets.writePng(
                room.resolve("images/room_wall_texture_64_0_wall_white.png"), CameraTestAssets.image(4, 4, BLUE));
        CameraTestAssets.writePng(room.resolve("images/room_door_64.png"), CameraTestAssets.image(2, 3, RED));

        NitroLibrary library = LOADER.load(room);

        assertEquals("room", library.name());
        assertEquals(BLUE, library.image("wall_texture_64_0_wall_white").getRGB(3, 3));
        assertEquals(RED, library.image("door_64_flipH").getRGB(1, 2));
        assertNull(library.image("../../etc/passwd"));
    }

    @Test
    void safeNames() {
        assertTrue(NitroLibraryLoader.isSafeName("h_std_hd_1_2_0"));
        assertTrue(NitroLibraryLoader.isSafeName("chair_64_a_0_0"));
        assertFalse(NitroLibraryLoader.isSafeName("../chair"));
        assertFalse(NitroLibraryLoader.isSafeName("a/b"));
        assertFalse(NitroLibraryLoader.isSafeName(""));
        assertFalse(NitroLibraryLoader.isSafeName(null));
    }
}
