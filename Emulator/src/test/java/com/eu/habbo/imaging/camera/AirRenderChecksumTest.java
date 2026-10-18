package com.eu.habbo.imaging.camera;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AirRenderChecksumTest {
    /** The official client's composer, transliterated step by step, to cross-check {@link AirRenderChecksum#seal}. */
    private static String airCompose(
            String planes, String sprites, String filters, int roomId, int zoom, double clock, int level) {
        String s = "{ \"planes\" : " + planes + ",\"sprites\" : " + sprites + ",\"modifiers\" : {}" + ",\"filters\" : "
                + filters + ",\"roomid\" : " + roomId;
        if (zoom != 1) {
            s += ",\"zoom\" : " + zoom;
        }
        double time = clock;
        int timeLastDigits = (int) (time % 100);
        time -= timeLastDigits;
        int status = (int) (time / 100 % 23 + level);
        s += ",\"status\" : " + status;
        int check = s.length();
        check = (int) ((check + time / 100 * 17) % 1493);
        int a = check;
        int b = roomId;
        for (int i = 0; i < s.length(); i++) {
            a = (a + (s.charAt(i) & 0xFF)) % 255;
            b = (a + b) % 255;
        }
        int checksum = (a + b) % 100;
        s += ",\"timestamp\" : " + (long) (time + checksum);
        s += ",\"checksum\" : " + (timeLastDigits + 13) * (check + 29) + " }";
        return s;
    }

    @Test
    void fletcher100MatchesTheClient() {
        // a: 5+1=6, 6+2=8; b: 0+6=6, 6+8=14 -> (8+14)%100
        assertEquals(22, AirRenderChecksum.fletcher100(new byte[] {1, 2}, 5, 0));
        // bytes above 127 count unsigned, as an AS3 ByteArray index does
        assertEquals(
                (200 % 255 + (200 % 255 + 7) % 255) % 100,
                AirRenderChecksum.fletcher100(new byte[] {(byte) 200}, 0, 7));
    }

    @Test
    void sealMatchesTheClientsComposer() {
        String planes = "[{\"z\":1.5,\"cornerPoints\":[{\"x\":0,\"y\":0}],\"color\":0}]";
        String sprites = "[{\"name\":\"chair_64_a_0_0\",\"x\":10,\"y\":-4,\"z\":3.25}]";
        String filters = "[{\"name\":\"frame_gold\",\"alpha\":255}]";
        long clock = 1_760_000_123_457L;

        assertEquals(
                airCompose(planes, sprites, filters, 4242, 2, clock, 3),
                AirRenderChecksum.seal(
                        "{ \"planes\" : " + planes + ",\"sprites\" : " + sprites + ",\"modifiers\" : {},\"filters\" : "
                                + filters + ",\"roomid\" : 4242,\"zoom\" : 2",
                        4242,
                        clock,
                        3));
    }

    @Test
    void aGenuinePayloadVerifies() {
        String json = airCompose("[]", "[]", "[]", 17, 1, 1_760_000_000_099d, 7);
        assertEquals(AirRenderChecksum.Result.OK, AirRenderChecksum.verify(json, 17));
    }

    @Test
    void tamperingIsDetected() {
        String json = CameraTestAssets.airScene(
                "[]", "[{\"name\":\"a\",\"x\":1,\"y\":2,\"z\":0}]", "[]", 17, 1, 1_760_000_000_042L);
        assertEquals(AirRenderChecksum.Result.OK, AirRenderChecksum.verify(json, 17));

        assertEquals(
                AirRenderChecksum.Result.BAD_TIMESTAMP,
                AirRenderChecksum.verify(json.replace("\"x\":1", "\"x\":9"), 17));
        assertEquals(AirRenderChecksum.Result.BAD_TIMESTAMP, AirRenderChecksum.verify(json, 18));
        assertEquals(AirRenderChecksum.Result.MISSING_FIELDS, AirRenderChecksum.verify("{\"planes\":[]}", 17));
        int checksumAt = json.lastIndexOf(",\"checksum\" : ");
        String badChecksum = json.substring(0, checksumAt) + ",\"checksum\" : 7 }";
        assertEquals(AirRenderChecksum.Result.BAD_CHECKSUM, AirRenderChecksum.verify(badChecksum, 17));
    }

    @Test
    void theClientClockIsRecovered() {
        String json = CameraTestAssets.airScene("[]", "[]", "[]", 1, 1, 1_760_000_000_042L);
        assertTrue(json.contains(",\"timestamp\" : 17600000000"));
        assertEquals(1_760_000_000_000L, AirRenderChecksum.clientTimeMillis(1_760_000_000_063L));
    }
}
