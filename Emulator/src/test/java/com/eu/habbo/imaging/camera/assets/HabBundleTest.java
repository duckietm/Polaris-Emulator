package com.eu.habbo.imaging.camera.assets;

import static com.eu.habbo.imaging.camera.CameraTestAssets.GREEN;
import static com.eu.habbo.imaging.camera.CameraTestAssets.RED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eu.habbo.imaging.camera.CameraTestAssets;
import com.eu.habbo.imaging.camera.CameraTestAssets.HabFile;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.zip.Deflater;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HabBundleTest {
    private static final NitroLibraryLoader LOADER = new NitroLibraryLoader(1024 * 1024, 1024 * 1024);

    private static byte[] chairHab() throws IOException {
        return CameraTestAssets.hab(
                "chair",
                new HabFile(
                        "chair.json",
                        "application/json",
                        NitroLibraryLoaderTest.CHAIR_JSON.getBytes(StandardCharsets.UTF_8),
                        true),
                new HabFile("chair.png", "image/png", CameraTestAssets.png(NitroLibraryLoaderTest.sheet()), false));
    }

    @Test
    void aHabWithTheJsonAndSheetOfANitroReadsTheSame() throws IOException {
        byte[] hab = chairHab();
        assertTrue(HabBundle.isHab(hab));

        NitroLibrary library = LOADER.read(hab, "chair");

        assertEquals("chair", library.name());
        BufferedImage untrimmed = library.image("chair_64_b_0_0");
        assertEquals(3, untrimmed.getWidth());
        assertEquals(GREEN, untrimmed.getRGB(0, 0));
        BufferedImage trimmed = library.image("chair_64_a_0_0");
        assertEquals(5, trimmed.getWidth());
        assertEquals(RED, trimmed.getRGB(1, 3));
        assertNotNull(library.palette("chair_palette1"));
    }

    @Test
    void theLoaderPicksTheHabFileAndBundleSourceFindsIt(@TempDir Path dir) throws IOException {
        Path furniture = dir.resolve("furniture");
        Files.createDirectories(furniture);
        Files.write(furniture.resolve("chair.hab"), chairHab());

        assertEquals(
                GREEN,
                LOADER.load(furniture.resolve("chair.hab"))
                        .image("chair_64_b_0_0")
                        .getRGB(0, 0));

        BundleAssetSource source = new BundleAssetSource(
                new BundleAssetSource.Roots(furniture, null, null, null, null, null, null),
                LOADER,
                1024 * 1024,
                1024 * 1024);
        assertEquals(GREEN, source.image("chair_64_b_0_0").getRGB(0, 0));
    }

    @Test
    void aLibraryWithoutANameTakesDocumentClassThenTheIndexName() throws IOException {
        String noName = NitroLibraryLoaderTest.CHAIR_JSON.replace("\"name\":\"chair\",", "");
        byte[] withDocumentClass = CameraTestAssets.hab(
                "index_name",
                new HabFile(
                        "x.json",
                        "",
                        noName.replace("{\"assets\"", "{\"documentClass\":\"chair\",\"assets\"")
                                .getBytes(StandardCharsets.UTF_8),
                        true),
                new HabFile("x.png", "", CameraTestAssets.png(NitroLibraryLoaderTest.sheet()), true));
        assertEquals("chair", LOADER.read(withDocumentClass, "fallback").name());

        byte[] indexOnly = CameraTestAssets.hab(
                "chair", new HabFile("x.json", "application/json", noName.getBytes(StandardCharsets.UTF_8), false));
        NitroLibrary library = LOADER.read(indexOnly, "fallback");
        assertEquals("chair", library.name());
    }

    @Test
    void aFlashStyleLibraryIsRefused() throws IOException {
        byte[] xmlOnly = CameraTestAssets.hab(
                "chair",
                new HabFile("chair_assets.xml", "text/xml", "<assets/>".getBytes(StandardCharsets.UTF_8), true),
                new HabFile(
                        "chair_64_a_0_0.png",
                        "image/png",
                        CameraTestAssets.png(CameraTestAssets.image(1, 1, RED)),
                        false));
        IOException noJson = assertThrows(IOException.class, () -> LOADER.read(xmlOnly, "chair"));
        assertTrue(noJson.getMessage().contains("flash-style"));

        byte[] looseImages = CameraTestAssets.hab(
                "chair",
                new HabFile("chair.json", "application/json", "{}".getBytes(StandardCharsets.UTF_8), false),
                new HabFile("a.png", "image/png", CameraTestAssets.png(CameraTestAssets.image(1, 1, RED)), false),
                new HabFile("b.png", "image/png", CameraTestAssets.png(CameraTestAssets.image(1, 1, RED)), false));
        assertThrows(IOException.class, () -> LOADER.read(looseImages, "chair"));
    }

    @Test
    void aRawDeflateEntryIsAccepted() throws IOException {
        byte[] json = "{\"name\":\"raw\"}".getBytes(StandardCharsets.UTF_8);
        Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION, true);
        deflater.setInput(json);
        deflater.finish();
        byte[] buffer = new byte[256];
        byte[] raw = Arrays.copyOf(buffer, deflater.deflate(buffer));
        deflater.end();
        byte[] hab = CameraTestAssets.hab("raw", new HabFile("raw.json", "application/json", json, true, raw));

        HabBundle parsed = HabBundle.read(hab, 1024);
        assertEquals("raw", parsed.name());
        assertEquals("{\"name\":\"raw\"}", new String(parsed.entries().get(0).bytes(), StandardCharsets.UTF_8));
        assertFalse(parsed.entries().get(0).isImage());
    }

    @Test
    void damagedBundlesFailInsteadOfReadingPastTheEnd() throws IOException {
        byte[] hab = chairHab();

        assertThrows(IOException.class, () -> HabBundle.read(Arrays.copyOf(hab, hab.length - 5), 1024 * 1024));
        byte[] badVersion = hab.clone();
        badVersion[4] = 2;
        assertThrows(IOException.class, () -> HabBundle.read(badVersion, 1024 * 1024));
        assertThrows(IOException.class, () -> HabBundle.read(new byte[] {1, 2, 3}, 1024));
        // An entry bigger than the limit is refused rather than inflated.
        assertThrows(IOException.class, () -> HabBundle.read(hab, 64));
    }

    @Test
    void anEntryOutsideTheDataSectionIsRefused() throws IOException {
        byte[] hab = CameraTestAssets.hab(
                "chair", new HabFile("chair.json", "application/json", "{}".getBytes(StandardCharsets.UTF_8), false));
        // Shrink the declared data length so the entry no longer fits.
        hab[16] = 1;
        assertThrows(IOException.class, () -> HabBundle.read(hab, 1024));
    }
}
