package com.eu.habbo.imaging.camera;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.eu.habbo.imaging.camera.CameraRenderMode.Decision;
import com.eu.habbo.imaging.camera.CameraRenderMode.Payload;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class CameraRenderModeTest {
    @Test
    void payloadsAreToldApartByTheirFirstBytes() throws IOException {
        assertEquals(Payload.PNG, Payload.detect(CameraTestAssets.png(CameraTestAssets.image(1, 1, 0))));
        assertEquals(Payload.ZLIB, Payload.detect(CameraTestAssets.deflate("{ \"planes\" : []")));
        assertEquals(Payload.ZLIB, Payload.detect(new byte[] {0x78, (byte) 0xDA}));
        assertEquals(Payload.UNKNOWN, Payload.detect(new byte[] {'{', '"'}));
        assertEquals(Payload.UNKNOWN, Payload.detect(new byte[] {0x78}));
        assertEquals(Payload.UNKNOWN, Payload.detect(null));
    }

    @Test
    void modesParse() {
        assertEquals(CameraRenderMode.UPLOAD, CameraRenderMode.parse("upload"));
        assertEquals(CameraRenderMode.SERVER, CameraRenderMode.parse(" Server "));
        assertEquals(CameraRenderMode.AUTO, CameraRenderMode.parse("auto"));
        assertEquals(CameraRenderMode.AUTO, CameraRenderMode.parse("nonsense"));
        assertEquals(CameraRenderMode.AUTO, CameraRenderMode.parse(null));
    }

    @Test
    void uploadModeOnlyStoresPngs() {
        assertEquals(Decision.STORE_UPLOAD, CameraRenderMode.UPLOAD.decide(Payload.PNG, true));
        assertEquals(Decision.REFUSE_RENDER_DISABLED, CameraRenderMode.UPLOAD.decide(Payload.ZLIB, true));
    }

    @Test
    void serverModeOnlyRenders() {
        assertEquals(Decision.REFUSE_UPLOAD_DISABLED, CameraRenderMode.SERVER.decide(Payload.PNG, true));
        assertEquals(Decision.RENDER, CameraRenderMode.SERVER.decide(Payload.ZLIB, true));
        assertEquals(Decision.REFUSE_RENDER_UNAVAILABLE, CameraRenderMode.SERVER.decide(Payload.ZLIB, false));
    }

    @Test
    void autoModeDoesBothButRendersOnlyWithAssets() {
        assertEquals(Decision.STORE_UPLOAD, CameraRenderMode.AUTO.decide(Payload.PNG, false));
        assertEquals(Decision.RENDER, CameraRenderMode.AUTO.decide(Payload.ZLIB, true));
        assertEquals(Decision.REFUSE_RENDER_UNAVAILABLE, CameraRenderMode.AUTO.decide(Payload.ZLIB, false));
        assertEquals(Decision.REFUSE_UNKNOWN, CameraRenderMode.AUTO.decide(Payload.UNKNOWN, true));
    }
}
