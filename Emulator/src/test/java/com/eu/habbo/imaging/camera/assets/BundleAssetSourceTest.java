package com.eu.habbo.imaging.camera.assets;

import static com.eu.habbo.imaging.camera.CameraTestAssets.BLUE;
import static com.eu.habbo.imaging.camera.CameraTestAssets.GREEN;
import static com.eu.habbo.imaging.camera.CameraTestAssets.RED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eu.habbo.imaging.camera.CameraTestAssets;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BundleAssetSourceTest {
    private static final NitroLibraryLoader LOADER = new NitroLibraryLoader(1024 * 1024, 1024 * 1024);

    private static String library(String name, String asset) {
        return "{\"name\":\"" + name + "\",\"assets\":{\"" + asset + "\":{\"x\":0,\"y\":0}},"
                + "\"spritesheet\":{\"frames\":{\"" + name + "_" + asset + ".png\":{\"frame\":{\"x\":0,\"y\":0,"
                + "\"w\":2,\"h\":2},\"rotated\":false,\"trimmed\":false,\"spriteSourceSize\":{\"x\":0,\"y\":0,"
                + "\"w\":2,\"h\":2},\"sourceSize\":{\"w\":2,\"h\":2}}}}}";
    }

    private BundleAssetSource source(Path dir) {
        return new BundleAssetSource(
                new BundleAssetSource.Roots(
                        dir.resolve("furniture"),
                        dir.resolve("figure"),
                        dir.resolve("FigureMap.json"),
                        dir.resolve("effect"),
                        dir.resolve("EffectMap.json"),
                        dir.resolve("pet"),
                        dir.resolve("room.nitro")),
                LOADER,
                8L * 1024 * 1024,
                8L * 1024 * 1024);
    }

    @Test
    void furnitureIsFoundByItsClassnamePrefix(@TempDir Path dir) throws IOException {
        CameraTestAssets.writeNitro(
                dir.resolve("furniture"),
                "rare_dragonlamp",
                library("rare_dragonlamp", "rare_dragonlamp_64_a_2_0"),
                CameraTestAssets.image(2, 2, RED));
        BundleAssetSource source = source(dir);

        assertTrue(source.isConfigured());
        assertEquals(RED, source.image("rare_dragonlamp_64_a_2_0").getRGB(1, 1));
        assertEquals(
                "rare_dragonlamp", source.libraryFor("rare_dragonlamp_64_a_2_0").name());
        assertSame(source.image("rare_dragonlamp_64_a_2_0"), source.image("rare_dragonlamp_64_a_2_0"), "cached");
        assertNull(source.image("rare_dragonlamp_64_b_2_0"));
        assertNull(source.image("unknown_64_a_0_0"));
    }

    @Test
    void aColourVariantUsesItsBaseLibrary(@TempDir Path dir) throws IOException {
        CameraTestAssets.writeNitro(
                dir.resolve("furniture"),
                "rare_fan",
                library("rare_fan", "rare_fan_64_a_0_0"),
                CameraTestAssets.image(2, 2, GREEN));

        assertEquals(GREEN, source(dir).image("rare_fan*3_64_a_0_0").getRGB(0, 0));
        assertEquals("rare_fan_64_a_0_0", BundleAssetSource.withoutColourIndex("rare_fan*12_64_a_0_0"));
    }

    @Test
    void avatarPartsComeFromTheLibraryTheFigureMapNames(@TempDir Path dir) throws IOException {
        Files.createDirectories(dir);
        Files.writeString(
                dir.resolve("FigureMap.json"),
                "{\"libraries\":[{\"id\":\"hh_human_body\",\"revision\":1,\"parts\":[{\"id\":\"1\",\"type\":\"hd\"},"
                        + "{\"id\":\"1\",\"type\":\"bd\"}]},{\"id\":\"hair_F_x\",\"parts\":[{\"id\":\"515\","
                        + "\"type\":\"hr\"}]}]}");
        Files.writeString(
                dir.resolve("EffectMap.json"), "{\"effects\":[{\"id\":\"29\",\"lib\":\"Dance1\",\"type\":\"dance\"}]}");
        CameraTestAssets.writeNitro(
                dir.resolve("figure"),
                "hh_human_body",
                library("hh_human_body", "h_std_hd_1_2_0"),
                CameraTestAssets.image(2, 2, RED));
        CameraTestAssets.writeNitro(
                dir.resolve("figure"),
                "hair_F_x",
                library("hair_F_x", "h_std_hr_515_2_0"),
                CameraTestAssets.image(2, 2, BLUE));
        CameraTestAssets.writeNitro(
                dir.resolve("effect"),
                "Dance1",
                library("Dance1", "h_std_fx29_1_2_0"),
                CameraTestAssets.image(2, 2, GREEN));
        BundleAssetSource source = source(dir);

        assertEquals(RED, source.image("h_std_hd_1_2_0").getRGB(0, 0));
        assertEquals(BLUE, source.image("h_std_hr_515_2_0").getRGB(0, 0));
        assertEquals(GREEN, source.image("h_std_fx29_1_2_0").getRGB(0, 0));
        assertNull(source.image("h_std_hd_2_2_0"), "part 2 is in no library");
    }

    @Test
    void theRoomLibraryIsAskedFirst(@TempDir Path dir) throws IOException {
        CameraTestAssets.writeNitro(
                dir, "room", library("room", "wall_texture_64_0_wall_white"), CameraTestAssets.image(2, 2, BLUE));
        BundleAssetSource source = source(dir);

        assertEquals(BLUE, source.image("wall_texture_64_0_wall_white").getRGB(0, 0));
    }

    @Test
    void namesThatLeaveTheFolderAreRefused(@TempDir Path dir) throws IOException {
        CameraTestAssets.writeNitro(
                dir.resolve("furniture"), "x", library("x", "x_a"), CameraTestAssets.image(2, 2, RED));
        BundleAssetSource source = source(dir);

        assertNull(source.image("../furniture/x_a"));
        assertNull(source.image("x/../../x_a"));
        assertNotNull(source.image("x_a"));
    }

    @Test
    void nothingConfiguredIsReported(@TempDir Path dir) {
        BundleAssetSource source = source(dir.resolve("missing"));
        assertFalse(source.isConfigured());
        CompositeAssetSource composite = new CompositeAssetSource(List.of(source));
        assertFalse(composite.isConfigured());
        assertNull(composite.image("chair_64_a_0_0"));
    }
}
