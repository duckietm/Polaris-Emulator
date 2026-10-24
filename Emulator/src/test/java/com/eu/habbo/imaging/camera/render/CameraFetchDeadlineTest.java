package com.eu.habbo.imaging.camera.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * A fetch is bounded as a whole, not per read call: the socket timeout only bounds the
 * wait for the next bytes, so a server sending one byte just inside it held a camera
 * worker for as long as it liked - and there are two workers by default.
 */
class CameraFetchDeadlineTest {

    /** Hands out one byte per read, as a slow-drip server does. */
    private static final class DripStream extends InputStream {
        private final byte[] bytes;
        private int position;
        private int reads;

        private DripStream(String text) {
            this.bytes = text.getBytes(StandardCharsets.ISO_8859_1);
        }

        @Override
        public int read() {
            this.reads++;
            return this.position < this.bytes.length ? this.bytes[this.position++] & 0xFF : -1;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) {
            if (length == 0) return 0;
            int value = this.read();
            if (value < 0) return -1;
            buffer[offset] = (byte) value;
            return 1;
        }
    }

    private static final String RESPONSE = "HTTP/1.1 200 OK\r\nContent-Length: 4\r\n\r\nbody";

    @Test
    void aFetchInsideItsBudgetIsRead() throws Exception {
        InputStream in = new CameraUrlFetcher.DeadlineInputStream(
                new ByteArrayInputStream(RESPONSE.getBytes(StandardCharsets.ISO_8859_1)),
                System.nanoTime() + 60_000_000_000L);

        byte[] body = CameraUrlFetcher.readResponse(in, 1024);

        assertEquals("body", new String(body, StandardCharsets.ISO_8859_1));
    }

    @Test
    void aFetchPastItsDeadlineStopsInsteadOfReadingOn() {
        DripStream drip = new DripStream(RESPONSE);
        InputStream in = new CameraUrlFetcher.DeadlineInputStream(drip, System.nanoTime() - 1L);

        IOException refused = assertThrows(IOException.class, () -> CameraUrlFetcher.readResponse(in, 1024));

        assertTrue(refused.getMessage().contains("time budget"), refused.getMessage());
        assertEquals(0, drip.reads, "the deadline is checked before the first byte, not after the last");
    }

    @Test
    void theDeadlineIsCheckedWhileTheBodyIsStillArriving() throws Exception {
        // A deadline that passes between the header and the body: the header is read,
        // the body read then stops rather than waiting out the rest of the drip.
        DripStream drip = new DripStream(RESPONSE);
        InputStream in = new CameraUrlFetcher.DeadlineInputStream(drip, System.nanoTime() + 20_000_000L);

        Thread.sleep(40);

        assertThrows(IOException.class, () -> CameraUrlFetcher.readResponse(in, 1024));
    }
}
