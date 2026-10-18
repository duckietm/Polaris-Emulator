package com.eu.habbo.imaging.camera;

import static com.eu.habbo.imaging.camera.CameraTestAssets.BLUE;
import static com.eu.habbo.imaging.camera.CameraTestAssets.GREEN;
import static com.eu.habbo.imaging.camera.CameraTestAssets.RED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eu.habbo.imaging.camera.assets.BundleAssetSource;
import com.eu.habbo.imaging.camera.assets.CameraAssetSource;
import com.eu.habbo.imaging.camera.assets.LooseImageDirectory;
import com.eu.habbo.imaging.camera.assets.NitroLibraryLoader;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CameraRenderServiceTest {
    private static final int ROOM = 4242;
    private static final int BACKGROUND = 0x102030;
    private static final NitroLibraryLoader LOADER = new NitroLibraryLoader(1024 * 1024, 1024 * 1024);

    /** The full-viewport background plane the client always sends first. */
    private static final String BACKGROUND_PLANE =
            "{\"z\":999.5,\"cornerPoints\":[{\"x\":320,\"y\":320},{\"x\":0,\"y\":320},"
                    + "{\"x\":320,\"y\":0},{\"x\":0,\"y\":0}],\"color\":" + BACKGROUND + "}";
    /** A floor across the bottom of the photo (y 200..320), textured from the room library. */
    private static final String FLOOR_PLANE = "{\"z\":100,\"cornerPoints\":[{\"x\":0,\"y\":200},{\"x\":320,\"y\":200},"
            + "{\"x\":0,\"y\":320},{\"x\":320,\"y\":320}],\"color\":16777215,\"bottomAligned\":false,"
            + "\"texCols\":[{\"assetNames\":[\"floor_texture_64_0_floor_basic\"]}]}";

    private static final String PLANES = "[" + BACKGROUND_PLANE + "," + FLOOR_PLANE + "]";
    private static final String SPRITES = "[{\"name\":\"chair_64_a_0_0\",\"x\":50,\"y\":250,\"z\":10},"
            + "{\"name\":\"chair_64_a_2_0\",\"x\":100,\"y\":250,\"z\":10,\"flipH\":true},"
            + "{\"name\":\"avatar_7\",\"x\":0,\"y\":0,\"z\":5}]";

    @TempDir
    Path dir;

    private CameraAssetSource sprites;
    private CameraAssetSource effects;

    @BeforeEach
    void assets() throws IOException {
        Path room = this.dir.resolve("room");
        Files.createDirectories(room.resolve("images"));
        Files.writeString(
                room.resolve("room.asset.json"),
                "{\"name\":\"room\",\"assets\":{\"floor_texture_64_0_floor_basic\":{\"x\":0,\"y\":0}}}");
        CameraTestAssets.writePng(
                room.resolve("images/room_floor_texture_64_0_floor_basic.png"), CameraTestAssets.image(8, 8, GREEN));

        // chair_64_a_0_0: a 6x6 red frame trimmed out of a 10x10 asset at (1,2); chair_64_a_2_0 mirrors it.
        BufferedImage sheet = CameraTestAssets.image(6, 6, RED);
        CameraTestAssets.writeNitro(
                this.dir.resolve("furniture"),
                "chair",
                "{\"name\":\"chair\",\"assets\":{\"chair_64_a_0_0\":{\"x\":-5,\"y\":-5},"
                        + "\"chair_64_a_2_0\":{\"source\":\"chair_64_a_0_0\",\"x\":5,\"y\":-5,\"flipH\":true}},"
                        + "\"spritesheet\":{\"frames\":{\"chair_chair_64_a_0_0.png\":{\"frame\":{\"x\":0,\"y\":0,\"w\":6,"
                        + "\"h\":6},\"rotated\":false,\"trimmed\":true,\"spriteSourceSize\":{\"x\":1,\"y\":2,\"w\":6,"
                        + "\"h\":6},\"sourceSize\":{\"w\":10,\"h\":10}}}}}",
                sheet);

        // frame_gold: a 2 px blue border.
        BufferedImage frame = new BufferedImage(320, 320, BufferedImage.TYPE_INT_ARGB);
        for (int i = 0; i < 320; i++) {
            for (int j = 0; j < 2; j++) {
                frame.setRGB(i, j, BLUE);
                frame.setRGB(j, i, BLUE);
            }
        }
        CameraTestAssets.writePng(this.dir.resolve("effects/frame_gold.png"), frame);

        this.sprites = new BundleAssetSource(
                new BundleAssetSource.Roots(this.dir.resolve("furniture"), null, null, null, null, null, room),
                LOADER,
                8L * 1024 * 1024,
                8L * 1024 * 1024);
        this.effects = new LooseImageDirectory(this.dir.resolve("effects"), LOADER, 4L * 1024 * 1024);
    }

    private CameraRenderService service(
            CameraRenderSettings settings, CameraAssetSource spriteSource, int workers, int queue) {
        return new CameraRenderService(() -> settings, spriteSource, this.effects, null, workers, queue);
    }

    private CameraRenderService service(CameraRenderSettings settings) {
        return this.service(settings, this.sprites, 1, 4);
    }

    private static byte[] scene(String planes, String sprites, String filters, int roomId) {
        return CameraTestAssets.deflate(
                CameraTestAssets.airScene(planes, sprites, filters, roomId, 1, System.currentTimeMillis()));
    }

    private static int rgb(BufferedImage image, int x, int y) {
        return image.getRGB(x, y) & 0xFFFFFF;
    }

    @Test
    void aSmallSceneRendersPlanesSpritesAFilterAndAFrame() throws Exception {
        CameraRenderService service = this.service(CameraTestAssets.settings(5000, true, 65536, 64));
        String filters = "[{\"name\":\"Yellow\",\"alpha\":255},{\"name\":\"frame_gold\",\"alpha\":255}]";

        BufferedImage photo =
                service.render(CameraRenderService.Kind.PHOTO, scene(PLANES, SPRITES, filters, ROOM), ROOM, 0, "");

        assertEquals(320, photo.getWidth());
        assertEquals(320, photo.getHeight());
        // background plane colour, with the "Yellow" matrix removing blue
        assertEquals(0x102000, rgb(photo, 50, 50));
        // the floor texture
        assertEquals(0x00FF00, rgb(photo, 200, 300));
        // the trimmed chair: transparent padding keeps the floor, the frame sits at (1,2)..(6,7)
        assertEquals(0x00FF00, rgb(photo, 50, 250));
        assertEquals(0xFF0000, rgb(photo, 51, 252));
        assertEquals(0xFF0000, rgb(photo, 56, 257));
        assertEquals(0x00FF00, rgb(photo, 57, 252));
        // the alias is the same frame mirrored: x 3..8 of its 10 px
        assertEquals(0x00FF00, rgb(photo, 101, 252));
        assertEquals(0xFF0000, rgb(photo, 103, 252));
        assertEquals(0xFF0000, rgb(photo, 108, 252));
        assertEquals(0x00FF00, rgb(photo, 109, 252));
        // the frame overlay is drawn last, after the colour filter
        assertEquals(0x0000FF, rgb(photo, 0, 0));
        assertEquals(0x0000FF, rgb(photo, 319, 1));
    }

    @Test
    void aThumbnailIs110Square() throws Exception {
        BufferedImage thumbnail = this.service(CameraTestAssets.settings(5000, true, 65536, 64))
                .render(CameraRenderService.Kind.THUMBNAIL, scene(PLANES, "[]", "[]", ROOM), ROOM, 0, "");

        assertEquals(110, thumbnail.getWidth());
        assertEquals(110, thumbnail.getHeight());
    }

    @Test
    void zoomEnlargesTheCentre() throws Exception {
        String planes = "[" + BACKGROUND_PLANE + "]";
        String sprites = "[{\"name\":\"chair_64_a_0_0\",\"x\":155,\"y\":155,\"z\":10}]";
        byte[] zoomed = CameraTestAssets.deflate(
                CameraTestAssets.airScene(planes, sprites, "[]", ROOM, 2, System.currentTimeMillis()));

        BufferedImage photo = this.service(CameraTestAssets.settings(5000, true, 65536, 64))
                .render(CameraRenderService.Kind.PHOTO, zoomed, ROOM, 0, "");

        // the chair's red block (x 156..161, y 157..162) doubles around the centre (x 152..175)
        int centre = rgb(photo, 157, 159);
        assertTrue((centre >> 16 & 0xFF) > 200 && (centre >> 8 & 0xFF) < 60, Integer.toHexString(centre));
        assertEquals(BACKGROUND, rgb(photo, 20, 20));
    }

    @Test
    void aSceneForAnotherRoomIsRefused() {
        CameraRenderService service = this.service(CameraTestAssets.settings(5000, true, 65536, 64));

        CameraRenderException error = assertThrows(
                CameraRenderException.class,
                () -> service.render(CameraRenderService.Kind.PHOTO, scene(PLANES, "[]", "[]", ROOM + 1), ROOM, 0, ""));
        assertEquals(CameraRenderException.Reason.ROOM_MISMATCH, error.reason());
    }

    @Test
    void aTamperedSceneFailsTheChecksumUnlessCheckingIsOff() throws Exception {
        String json = CameraTestAssets.airScene(PLANES, SPRITES, "[]", ROOM, 1, System.currentTimeMillis())
                .replace("\"x\":50", "\"x\":60");
        byte[] tampered = CameraTestAssets.deflate(json);

        CameraRenderException error = assertThrows(
                CameraRenderException.class,
                () -> this.service(CameraTestAssets.settings(5000, true, 65536, 64))
                        .render(CameraRenderService.Kind.PHOTO, tampered, ROOM, 0, ""));
        assertEquals(CameraRenderException.Reason.CHECKSUM_FAILED, error.reason());

        assertNotNull(this.service(CameraTestAssets.settings(5000, false, 65536, 64))
                .render(CameraRenderService.Kind.PHOTO, tampered, ROOM, 0, ""));
    }

    @Test
    void limitsAreEnforced() {
        CameraRenderService tooManySprites = this.service(CameraTestAssets.settings(5000, true, 65536, 2));
        CameraRenderException limit = assertThrows(
                CameraRenderException.class,
                () -> tooManySprites.render(
                        CameraRenderService.Kind.PHOTO, scene(PLANES, SPRITES, "[]", ROOM), ROOM, 0, ""));
        assertEquals(CameraRenderException.Reason.LIMIT_EXCEEDED, limit.reason());

        CameraRenderService smallPayloads = this.service(CameraTestAssets.settings(5000, true, 1024, 64));
        byte[] big = CameraTestAssets.deflate(
                new java.util.Random(1).ints(4000).mapToObj(Integer::toString).reduce("", String::concat));
        CameraRenderException tooLarge = assertThrows(
                CameraRenderException.class,
                () -> smallPayloads.render(CameraRenderService.Kind.PHOTO, big, ROOM, 0, ""));
        assertEquals(CameraRenderException.Reason.TOO_LARGE, tooLarge.reason());

        byte[] bomb = CameraTestAssets.deflate(new byte[2 * 1024 * 1024]);
        CameraRenderException inflate = assertThrows(
                CameraRenderException.class,
                () -> this.service(CameraTestAssets.settings(5000, true, 65536, 64))
                        .render(CameraRenderService.Kind.PHOTO, bomb, ROOM, 0, ""));
        assertEquals(CameraRenderException.Reason.INFLATE_FAILED, inflate.reason());

        CameraRenderException garbage = assertThrows(
                CameraRenderException.class,
                () -> this.service(CameraTestAssets.settings(5000, true, 65536, 64))
                        .render(
                                CameraRenderService.Kind.PHOTO,
                                CameraTestAssets.deflate("[[[[ not json"),
                                ROOM,
                                0,
                                ""));
        assertEquals(CameraRenderException.Reason.PARSE_FAILED, garbage.reason());
    }

    @Test
    void aRenderPastItsDeadlineIsAbandoned() {
        CameraRenderService service = this.service(CameraTestAssets.settings(0, true, 65536, 64));

        CameraRenderException error = assertThrows(
                CameraRenderException.class,
                () -> service.render(CameraRenderService.Kind.PHOTO, scene(PLANES, SPRITES, "[]", ROOM), ROOM, 0, ""));
        assertEquals(CameraRenderException.Reason.TIMEOUT, error.reason());
    }

    @Test
    void aFullQueueRefusesAtOnceAndQueuedWorkStillRuns() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CameraAssetSource blocking = new CameraAssetSource() {
            @Override
            public BufferedImage image(String assetName) {
                entered.countDown();
                try {
                    release.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return null;
            }

            @Override
            public boolean isConfigured() {
                return true;
            }

            @Override
            public String describe() {
                return "blocking";
            }
        };
        CameraRenderService service = this.service(CameraTestAssets.settings(5000, true, 65536, 64), blocking, 1, 1);
        CountDownLatch done = new CountDownLatch(2);
        AtomicReference<BufferedImage> result = new AtomicReference<>();
        CameraRenderService.Job job = new CameraRenderService.Job(
                CameraRenderService.Kind.THUMBNAIL,
                scene("[]", "[{\"name\":\"x_1\",\"x\":0,\"y\":0,\"z\":0}]", "[]", ROOM),
                ROOM,
                BACKGROUND,
                "",
                image -> {
                    result.set(image);
                    done.countDown();
                },
                error -> done.countDown());

        assertTrue(service.submit(job), "runs");
        assertTrue(entered.await(5, TimeUnit.SECONDS));
        assertTrue(service.submit(job), "waits in the queue");
        assertFalse(service.submit(job), "the queue is full");
        release.countDown();
        assertTrue(done.await(5, TimeUnit.SECONDS));
        assertEquals(BACKGROUND, result.get().getRGB(5, 5) & 0xFFFFFF);
        service.shutdown();
    }

    @Test
    void aServerRenderedPhotoIsWrittenWithItsSmallVariant() throws Exception {
        BufferedImage photo = this.service(CameraTestAssets.settings(5000, true, 65536, 64))
                .render(CameraRenderService.Kind.PHOTO, scene(PLANES, SPRITES, "[]", ROOM), ROOM, 0, "");
        Path out = this.dir.resolve("out");

        String name = CameraPhotoFiles.writePhoto(photo, out.toString(), "7_1700000000");

        assertEquals("7_1700000000.png", name);
        BufferedImage small =
                javax.imageio.ImageIO.read(out.resolve("7_1700000000_small.png").toFile());
        assertEquals(110, small.getWidth());
        assertEquals(320, javax.imageio.ImageIO.read(out.resolve(name).toFile()).getWidth());
    }
}
