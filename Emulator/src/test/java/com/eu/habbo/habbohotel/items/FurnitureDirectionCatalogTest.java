package com.eu.habbo.habbohotel.items;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.users.HabboItem;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.zip.Deflater;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Directions read from furniture bundles: the hotel's real {@code .hab} beds and made-up {@code .nitro} files. */
class FurnitureDirectionCatalogTest {
    /** The monorepo's furniture bundles (absent in a standalone Polaris checkout: those tests are skipped). */
    private static final Path FURNITURE = Path.of("../../Furniture");

    @TempDir
    Path temp;

    @AfterEach
    void uninstall() {
        FurnitureDirectionsRuntime.uninstall();
    }

    private static FurnitureDirectionCatalog catalog(Path dir, long waitMillis) {
        return new FurnitureDirectionCatalog(
                new FurnitureDirectionCatalog.Settings(true, dir, 64, waitMillis, 64L * 1024 * 1024));
    }

    /** The checkout may carry the Furniture folder with only the furnidata in it. */
    static boolean hasBundles(Path dir) {
        if (!Files.isDirectory(dir)) return false;
        try (var files = Files.list(dir)) {
            return files.anyMatch(file -> file.getFileName().toString().endsWith(".hab"));
        } catch (java.io.IOException e) {
            return false;
        }
    }

    private static FurnitureDirectionCatalog beds() {
        assumeTrue(hasBundles(FURNITURE), "no furni bundles in this checkout");
        return catalog(FURNITURE, 5000);
    }

    @Test
    void theHotelsBedsListTheirDirections() {
        FurnitureDirectionCatalog catalog = beds();
        try {
            assertArrayEquals(new int[] {0, 2}, catalog.directions("bed_polyfon"));
            assertArrayEquals(new int[] {0, 2}, catalog.directions("hosptl_bed"));
            // logic lists [90, 0]: sorted as AIR does.
            assertArrayEquals(new int[] {0, 2}, catalog.directions("darkelegant_c20_bed"));
            assertArrayEquals(new int[] {2, 4}, catalog.directions("bling_bed"));
            assertArrayEquals(new int[] {2, 4}, catalog.directions("gothic_bed"));
            assertArrayEquals(new int[] {0, 6}, catalog.directions("clothing_bedhair"));
            assertArrayEquals(new int[] {0, 2, 4, 6}, catalog.directions("habbo25_c25_bedfloat1"));
        } finally {
            catalog.shutdown();
        }
    }

    @Test
    void aColourVariantUsesItsBaseBundle() {
        FurnitureDirectionCatalog catalog = beds();
        try {
            assertArrayEquals(new int[] {2, 4}, catalog.directions("bling_bed*3"));
        } finally {
            catalog.shutdown();
        }
    }

    @Test
    void aMissingOrUnsafeNameIsUnknown() {
        FurnitureDirectionCatalog catalog = beds();
        try {
            assertArrayEquals(new int[0], catalog.directions("no_such_furni_here"));
            assertArrayEquals(new int[0], catalog.directions("../Furniture/bed_polyfon"));
            assertArrayEquals(new int[0], catalog.directions(null));
            assertArrayEquals(new int[0], catalog.directions(""));
        } finally {
            catalog.shutdown();
        }
    }

    @Test
    void theRuntimeAnswersARoomItemWithItsDirectionsAndDefaultDirection() {
        assumeTrue(hasBundles(FURNITURE), "no furni bundles in this checkout");
        FurnitureDirectionsRuntime.install(catalog(FURNITURE, 5000), name -> name.startsWith("bling_bed") ? 4 : -1);

        FurnitureDirections bling = FurnitureDirections.of(floorItem("bling_bed*2"));
        assertArrayEquals(new int[] {2, 4}, bling.values());
        assertEquals(4, bling.defaultDirection());
        assertEquals(4, bling.place(0));

        FurnitureDirections polyfon = FurnitureDirections.of(floorItem("bed_polyfon"));
        assertEquals(-1, polyfon.defaultDirection());
        assertEquals(0, polyfon.place(5));

        Item wallBase = mock(Item.class);
        when(wallBase.getType()).thenReturn(FurnitureType.WALL);
        when(wallBase.getName()).thenReturn("bed_polyfon");
        HabboItem wall = mock(HabboItem.class);
        when(wall.getBaseItem()).thenReturn(wallBase);
        assertSame(FurnitureDirections.UNKNOWN, FurnitureDirections.of(wall));
    }

    @Test
    void withoutAnInstalledCatalogEveryFurniIsUnknown() {
        FurnitureDirectionsRuntime.uninstall();
        assertSame(FurnitureDirections.UNKNOWN, FurnitureDirections.of(floorItem("bed_polyfon")));
        assertSame(FurnitureDirections.UNKNOWN, FurnitureDirections.of((HabboItem) null));
    }

    @Test
    void aNitroBundleIsReadWithoutItsSheet() throws IOException {
        Files.write(
                this.temp.resolve("wf_antenna2.nitro"),
                nitro(Map.of(
                        "wf_antenna2.json",
                        "{\"name\":\"wf_antenna2\",\"logic\":{\"model\":{\"directions\":[0,90]}}}",
                        "wf_antenna2.png",
                        "not really a png")));
        FurnitureDirectionCatalog catalog = catalog(this.temp, 5000);
        try {
            assertTrue(catalog.isEnabled());
            assertArrayEquals(new int[] {0, 2}, catalog.directions("wf_antenna2"));
        } finally {
            catalog.shutdown();
        }
    }

    @Test
    void anUnreadableBundleIsUnknownAndDoesNotThrow() throws IOException {
        Files.write(this.temp.resolve("broken.nitro"), new byte[] {0, 1, 0, 5, 'b'});
        Files.write(this.temp.resolve("bad.hab"), "HAB\0garbage-garbage-garbage".getBytes(StandardCharsets.ISO_8859_1));
        FurnitureDirectionCatalog catalog = catalog(this.temp, 5000);
        try {
            assertArrayEquals(new int[0], catalog.directions("broken"));
            assertArrayEquals(new int[0], catalog.directions("bad"));
        } finally {
            catalog.shutdown();
        }
    }

    @Test
    void aDisabledOrUnconfiguredCatalogAnswersUnknown() throws IOException {
        Files.write(
                this.temp.resolve("x.nitro"),
                nitro(Map.of("x.json", "{\"logic\":{\"model\":{\"directions\":[0,90]}}}")));
        FurnitureDirectionCatalog off = new FurnitureDirectionCatalog(
                new FurnitureDirectionCatalog.Settings(false, this.temp, 64, 5000, 1 << 20));
        FurnitureDirectionCatalog none = catalog(null, 5000);
        FurnitureDirectionCatalog missing = catalog(this.temp.resolve("nope"), 5000);
        try {
            assertFalse(off.isEnabled());
            assertArrayEquals(new int[0], off.directions("x"));
            assertFalse(none.isEnabled());
            assertArrayEquals(new int[0], none.directions("x"));
            assertFalse(missing.isEnabled());
            assertArrayEquals(new int[0], missing.directions("x"));
        } finally {
            off.shutdown();
            none.shutdown();
            missing.shutdown();
        }
    }

    @Test
    void withoutWaitingALookupFallsBackUntilTheBackgroundReadIsCached() throws Exception {
        Files.write(
                this.temp.resolve("x.nitro"),
                nitro(Map.of("x.json", "{\"logic\":{\"model\":{\"directions\":[180,90]}}}")));
        FurnitureDirectionCatalog catalog = catalog(this.temp, 0);
        try {
            catalog.prefetch("x");
            int[] directions = new int[0];
            for (int i = 0; i < 200 && directions.length == 0; i++) {
                directions = catalog.directions("x");
                if (directions.length == 0) {
                    Thread.onSpinWait();
                    Thread.sleep(10);
                }
            }
            assertArrayEquals(new int[] {2, 4}, directions);
            // Cached: deleting the bundle does not change the answer.
            Files.delete(this.temp.resolve("x.nitro"));
            assertArrayEquals(new int[] {2, 4}, catalog.directions("x"));
        } finally {
            catalog.shutdown();
        }
    }

    @Test
    void settingsFallBackToTheCameraFurnitureFolder() {
        com.eu.habbo.core.ConfigurationManager config = mock(com.eu.habbo.core.ConfigurationManager.class);
        when(config.getValue(FurnitureDirectionsRuntime.ASSETS_DIR, "")).thenReturn("");
        when(config.getValue(FurnitureDirectionsRuntime.CAMERA_FURNITURE_DIR, ""))
                .thenReturn("/srv/furni");
        when(config.getBoolean(FurnitureDirectionsRuntime.ENABLED, true)).thenReturn(true);
        when(config.getInt(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyInt()))
                .thenAnswer(invocation -> invocation.getArgument(1));

        FurnitureDirectionCatalog.Settings settings = FurnitureDirectionsRuntime.settings(config);

        assertEquals(Path.of("/srv/furni"), settings.furnitureDir());
        assertTrue(settings.enabled());
        assertEquals(8192, settings.cacheEntries());
        assertEquals(50, settings.waitMillis());
        assertEquals(64L * 1024 * 1024, settings.maxBundleBytes());
    }

    private static HabboItem floorItem(String name) {
        Item base = mock(Item.class);
        when(base.getType()).thenReturn(FurnitureType.FLOOR);
        when(base.getName()).thenReturn(name);
        HabboItem item = mock(HabboItem.class);
        when(item.getBaseItem()).thenReturn(base);
        return item;
    }

    private static byte[] nitro(Map<String, String> files) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeShort(files.size());
            for (Map.Entry<String, String> file : files.entrySet()) {
                byte[] name = file.getKey().getBytes(StandardCharsets.UTF_8);
                out.writeShort(name.length);
                out.write(name);
                byte[] deflated = deflate(file.getValue().getBytes(StandardCharsets.UTF_8));
                out.writeInt(deflated.length);
                out.write(deflated);
            }
        }
        return bytes.toByteArray();
    }

    private static byte[] deflate(byte[] input) {
        Deflater deflater = new Deflater();
        deflater.setInput(input);
        deflater.finish();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        while (!deflater.finished()) {
            out.write(buffer, 0, deflater.deflate(buffer));
        }
        deflater.end();
        return out.toByteArray();
    }
}
