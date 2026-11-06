package polaris.messages.incoming.camera;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import gnu.trove.map.hash.THashMap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import polaris.Emulator;
import polaris.core.ConfigurationManager;
import polaris.core.TextsManager;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.User;
import polaris.hotel.users.UserInfo;
import polaris.hotel.users.UserStats;
import polaris.imaging.camera.CameraRenderMode;
import polaris.imaging.camera.CameraRenderRuntime;
import polaris.imaging.camera.CameraRenderService;
import polaris.imaging.camera.CameraRenderSettings;
import polaris.imaging.camera.CameraTestAssets;
import polaris.imaging.camera.assets.CameraAssetSource;
import polaris.messages.ClientMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.camera.CameraURLComposer;

class CameraRoomPictureServerRenderTest {
    private static final int NOW = 1_700_000_000;
    private static final int ROOM = 7;
    private static final String PLANES = "[{\"z\":9,\"cornerPoints\":[{\"x\":320,\"y\":320},{\"x\":0,\"y\":320},"
            + "{\"x\":320,\"y\":0},{\"x\":0,\"y\":0}],\"color\":255}]";

    @TempDir
    Path dir;

    private final GameClient client = mock(GameClient.class);
    private final User user = mock(User.class);
    private final UserInfo info = mock(UserInfo.class);
    private final Room room = mock(Room.class);
    private UserStats stats;
    private MockedStatic<Emulator> emulator;
    private CameraRenderService service;

    @BeforeEach
    void setUp() throws Exception {
        CameraQuota.reset();
        this.stats = mock(UserStats.class);
        Field cache = UserStats.class.getField("cache");
        cache.setAccessible(true);
        cache.set(this.stats, new THashMap<String, Object>());

        when(this.client.getUser()).thenReturn(this.user);
        when(this.user.hasPermission("acc_camera")).thenReturn(true);
        when(this.user.getUserInfo()).thenReturn(this.info);
        when(this.user.getUserStats()).thenReturn(this.stats);
        when(this.info.getId()).thenReturn(1);
        when(this.info.getUsername()).thenReturn("tester");
        when(this.info.getCurrentRoom()).thenReturn(this.room);
        when(this.room.getId()).thenReturn(ROOM);
        when(this.room.getBackgroundTonerColor()).thenReturn(Color.BLACK);
        when(this.room.getWallPaint()).thenReturn("0.0");

        TextsManager texts = mock(TextsManager.class);
        when(texts.getValue(anyString())).thenAnswer(call -> "text:" + call.getArgument(0));
        when(texts.getValue(anyString(), anyString())).thenAnswer(call -> "text:" + call.getArgument(0));
        ConfigurationManager config = mock(ConfigurationManager.class);
        when(config.getValue(eq(CameraRenderSettings.MODE), anyString())).thenReturn("auto");

        this.emulator = mockStatic(Emulator.class);
        this.emulator.when(Emulator::getIntUnixTimestamp).thenReturn(NOW);
        this.emulator.when(Emulator::getTexts).thenReturn(texts);
        this.emulator.when(Emulator::getConfig).thenReturn(config);
    }

    @AfterEach
    void tearDown() {
        CameraRenderRuntime.uninstall();
        this.emulator.close();
    }

    private void install(CameraRenderMode mode, CameraAssetSource sprites, int queue) {
        CameraRenderSettings base = CameraTestAssets.settings(5000, true, 65536, 64);
        CameraRenderSettings settings = new CameraRenderSettings(
                mode,
                base.timeoutMs(),
                base.maxCompressedBytes(),
                base.maxInflatedBytes(),
                base.maxPlanes(),
                base.maxSprites(),
                base.maxFilters(),
                base.verifyChecksum(),
                0,
                base.renderOptions(),
                this.dir.resolve("photos").toString(),
                this.dir.resolve("thumbnails").toString(),
                "http://localhost/camera/",
                "{\"t\":%timestamp%,\"s\":%room_id%,\"w\":\"%url%\"}");
        this.service = new CameraRenderService(() -> settings, sprites, null, null, 1, queue);
        CameraRenderRuntime.install(this.service);
    }

    private static CameraAssetSource configured() {
        return new CameraAssetSource() {
            @Override
            public BufferedImage image(String assetName) {
                return null;
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
    }

    private void send(byte[] body) {
        ByteBuf buffer = Unpooled.buffer();
        buffer.writeInt(body.length);
        buffer.writeBytes(body);
        CameraRoomPictureEvent event = new CameraRoomPictureEvent();
        event.client = this.client;
        event.packet = new ClientMessage(3226, buffer);
        event.handle();
    }

    private static byte[] scene(int roomId) {
        return CameraTestAssets.deflate(
                CameraTestAssets.airScene(PLANES, "[]", "[]", roomId, 1, System.currentTimeMillis()));
    }

    private static byte[] png() throws Exception {
        return CameraTestAssets.png(CameraTestAssets.image(320, 320, 0xFF336699));
    }

    @Test
    void autoModeRendersTheOfficialClientsSceneAndRepliesWithItsUrl() {
        this.install(CameraRenderMode.AUTO, configured(), 4);

        this.send(scene(ROOM));

        verify(this.client, timeout(5000))
                .sendResponse(argThat((MessageComposer composer) -> composer instanceof CameraURLComposer url
                        && url.getURL().equals("1_" + NOW + ".png")));
        verify(this.info).setPhotoURL("http://localhost/camera/1_" + NOW + ".png");
        verify(this.info).setPhotoRoomId(ROOM);
        verify(this.info)
                .setPhotoJSON(
                        "{\"t\":" + NOW + ",\"s\":" + ROOM + ",\"w\":\"http://localhost/camera/1_" + NOW + ".png\"}");
        verify(this.info).setPhotoTimestamp(NOW);
        assertTrue(Files.isRegularFile(this.dir.resolve("photos/1_" + NOW + ".png")));
        assertTrue(Files.isRegularFile(this.dir.resolve("photos/1_" + NOW + "_small.png")));
        assertEquals(1, CameraQuota.dailyCount(1, NOW));
    }

    @Test
    void aSceneForAnotherRoomIsRefused() {
        this.install(CameraRenderMode.AUTO, configured(), 4);

        this.send(scene(ROOM + 1));

        verify(this.user, timeout(5000)).alert("text:camera.error.creation");
        verify(this.client, never()).sendResponse(any(MessageComposer.class));
        verify(this.info, never()).setPhotoTimestamp(NOW);
    }

    @Test
    void serverModeRefusesUploadedPngs() throws Exception {
        this.install(CameraRenderMode.SERVER, configured(), 4);

        this.send(png());

        verify(this.user).alert("text:camera.error.creation");
        verify(this.client, never()).sendResponse(any(MessageComposer.class));
        assertNull(CameraQuota.lastAttempt(1), "a refusal does not cost the cooldown");
    }

    @Test
    void uploadModeRefusesSceneJson() {
        this.install(CameraRenderMode.UPLOAD, configured(), 4);

        this.send(scene(ROOM));

        verify(this.user).alert("text:camera.error.creation");
        verify(this.client, after(300).never()).sendResponse(any(MessageComposer.class));
    }

    @Test
    void autoModeWithoutAssetsRefusesSceneJsonButStillStoresPngs() throws Exception {
        CameraAssetSource none = mock(CameraAssetSource.class);
        when(none.isConfigured()).thenReturn(false);
        this.install(CameraRenderMode.AUTO, none, 4);
        when(Emulator.getConfig().getValue("imager.location.output.camera"))
                .thenReturn(this.dir.resolve("uploads").toString());
        when(Emulator.getConfig().getValue("camera.url")).thenReturn("http://localhost/camera/");
        when(Emulator.getConfig().getValue("camera.extradata")).thenReturn("{\"t\":%timestamp%}");

        this.send(scene(ROOM));
        verify(this.user).alert("text:camera.error.creation");

        CameraQuota.reset();
        this.send(png());
        verify(this.client).sendResponse(argThat((MessageComposer composer) -> composer instanceof CameraURLComposer));
        assertTrue(Files.isRegularFile(this.dir.resolve("uploads/1_" + NOW + ".png")));
    }

    @Test
    void aFullRenderQueueTellsTheUserToTryAgain() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch entered = new CountDownLatch(1);
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
        this.install(CameraRenderMode.AUTO, blocking, 1);
        byte[] busyScene = CameraTestAssets.deflate(CameraTestAssets.airScene(
                PLANES, "[{\"name\":\"x_1\",\"x\":0,\"y\":0,\"z\":0}]", "[]", ROOM, 1, System.currentTimeMillis()));
        CameraRenderService.Job filler = new CameraRenderService.Job(
                CameraRenderService.Kind.THUMBNAIL, busyScene, ROOM, 0, "", image -> {}, error -> {});
        assertTrue(this.service.submit(filler));
        assertTrue(entered.await(5, TimeUnit.SECONDS));
        assertTrue(this.service.submit(filler));

        this.send(scene(ROOM));

        verify(this.user).alert("text:camera.render.busy");
        assertNull(CameraQuota.lastAttempt(1));
        release.countDown();
    }
}
