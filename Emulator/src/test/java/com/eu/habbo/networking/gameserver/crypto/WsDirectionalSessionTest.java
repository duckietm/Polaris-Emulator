package com.eu.habbo.networking.gameserver.crypto;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

/**
 * Version 1 of the encrypted WebSocket used one key both ways and random nonces, so a frame could be replayed,
 * reordered or reflected. Version 2: a key per direction, bound to both handshake keys, and a counter in every nonce.
 */
class WsDirectionalSessionTest {

    private static final byte[] SHARED = sequence();
    private static final byte[] SERVER_SPKI = "server-spki".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] CLIENT_SPKI = "client-spki".getBytes(StandardCharsets.US_ASCII);
    // Computed independently (Python hmac/hashlib); the renderer's test checks the same values.
    private static final String CLIENT_TO_SERVER_KEY = "74ada030b255155d0698f6ab4b4add8f0866bc8c2c6a82d4f735126fef242721";
    private static final String SERVER_TO_CLIENT_KEY = "c97264796dd38c46c38dbfb2a64b50ec9c3fd86dbf3028efb969faf4ce6080dd";

    private static byte[] sequence() {
        byte[] bytes = new byte[32];
        for (int i = 0; i < bytes.length; i++) bytes[i] = (byte) (i + 1);
        return bytes;
    }

    private static byte[] key(String label) throws GeneralSecurityException {
        return WsDirectionalSession.key(SHARED, label, WsDirectionalSession.transcriptHash(SERVER_SPKI, CLIENT_SPKI));
    }

    /** A frame as the client builds it: its nonce, then the ciphertext. */
    private static byte[][] clientFrame(long counter, String text) throws GeneralSecurityException {
        byte[] nonce = WsDirectionalSession.nonce(WsDirectionalSession.CLIENT_TO_SERVER, counter);
        byte[] ciphertext = WsSessionCrypto.aesGcmEncrypt(
                key("octane-ws-v2 c2s"), nonce, text.getBytes(StandardCharsets.UTF_8));
        return new byte[][] {nonce, ciphertext};
    }

    @Test
    void bothSidesDeriveTheSameKeyPerDirection() throws Exception {
        assertEquals(CLIENT_TO_SERVER_KEY, HexFormat.of().formatHex(key("octane-ws-v2 c2s")));
        assertEquals(SERVER_TO_CLIENT_KEY, HexFormat.of().formatHex(key("octane-ws-v2 s2c")));
    }

    @Test
    void framesInOrderAreRead() throws Exception {
        WsDirectionalSession session = WsDirectionalSession.forServer(SHARED, SERVER_SPKI, CLIENT_SPKI);

        for (int i = 0; i < 3; i++) {
            byte[][] frame = clientFrame(i, "packet " + i);
            assertArrayEquals(("packet " + i).getBytes(StandardCharsets.UTF_8), session.decrypt(frame[0], frame[1]));
        }
    }

    @Test
    void aReplayedOrSkippedFrameIsRefused() throws Exception {
        WsDirectionalSession session = WsDirectionalSession.forServer(SHARED, SERVER_SPKI, CLIENT_SPKI);
        byte[][] first = clientFrame(0, "first");
        session.decrypt(first[0], first[1]);

        assertThrows(GeneralSecurityException.class, () -> session.decrypt(first[0], first[1]));
        byte[][] third = clientFrame(2, "third");
        assertThrows(GeneralSecurityException.class, () -> session.decrypt(third[0], third[1]));
    }

    @Test
    void aFrameSentBackToTheServerIsRefused() throws Exception {
        WsDirectionalSession session = WsDirectionalSession.forServer(SHARED, SERVER_SPKI, CLIENT_SPKI);
        byte[] sent = session.encrypt("to the client".getBytes(StandardCharsets.UTF_8));
        byte[] nonce = Arrays.copyOfRange(sent, 0, WsSessionCrypto.NONCE_LEN);
        byte[] ciphertext = Arrays.copyOfRange(sent, WsSessionCrypto.NONCE_LEN, sent.length);

        assertThrows(GeneralSecurityException.class, () -> session.decrypt(nonce, ciphertext));
    }

    @Test
    void theServerSendsWithItsOwnKeyAndCounter() throws Exception {
        WsDirectionalSession session = WsDirectionalSession.forServer(SHARED, SERVER_SPKI, CLIENT_SPKI);
        session.encrypt(new byte[] {1});
        byte[] second = session.encrypt("hello".getBytes(StandardCharsets.UTF_8));

        byte[] nonce = Arrays.copyOfRange(second, 0, WsSessionCrypto.NONCE_LEN);
        assertArrayEquals(WsDirectionalSession.nonce(WsDirectionalSession.SERVER_TO_CLIENT, 1), nonce);
        byte[] plain = WsSessionCrypto.aesGcmDecrypt(
                key("octane-ws-v2 s2c"), nonce, Arrays.copyOfRange(second, WsSessionCrypto.NONCE_LEN, second.length));
        assertArrayEquals("hello".getBytes(StandardCharsets.UTF_8), plain);
    }
}
