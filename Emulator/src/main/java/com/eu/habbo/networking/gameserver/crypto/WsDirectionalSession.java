package com.eu.habbo.networking.gameserver.crypto;

import io.netty.util.AttributeKey;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;

/**
 * The encrypted WebSocket channel, version 2 (subprotocol {@value #SUBPROTOCOL}).
 *
 * <p>Version 1 used one AES key for both directions and a random nonce on every frame, so a frame could be replayed,
 * reordered or sent back to its own author. Version 2 derives one key per direction, bound to both handshake keys, and
 * every frame's nonce is the direction plus a 64-bit counter: the receiver takes exactly the next counter of the other
 * direction and closes the channel on anything else.
 *
 * <p>The client asks for the subprotocol when it supports it; an older client never does and keeps version 1, and an
 * older server never answers it, so the client keeps version 1 too.
 */
final class WsDirectionalSession {

    static final String SUBPROTOCOL = WsSessionCrypto.SUBPROTOCOL_V2;
    static final AttributeKey<WsDirectionalSession> ATTRIBUTE = AttributeKey.valueOf("WsDirectionalSession");

    static final int CLIENT_TO_SERVER = 1;
    static final int SERVER_TO_CLIENT = 2;
    private static final String INFO_CLIENT_TO_SERVER = "octane-ws-v2 c2s";
    private static final String INFO_SERVER_TO_CLIENT = "octane-ws-v2 s2c";

    private final byte[] receiveKey;
    private final byte[] sendKey;
    private long nextReceiveCounter;
    private long nextSendCounter;

    private WsDirectionalSession(byte[] receiveKey, byte[] sendKey) {
        this.receiveKey = receiveKey;
        this.sendKey = sendKey;
    }

    /** The server's session: it receives with the client-to-server key and sends with the other one. */
    static WsDirectionalSession forServer(byte[] sharedSecret, byte[] serverSpki, byte[] clientSpki)
            throws GeneralSecurityException {
        byte[] transcript = transcriptHash(serverSpki, clientSpki);
        return new WsDirectionalSession(
                key(sharedSecret, INFO_CLIENT_TO_SERVER, transcript),
                key(sharedSecret, INFO_SERVER_TO_CLIENT, transcript));
    }

    /** SHA-256 of both handshake public keys, so a key cannot be reused with another handshake. */
    static byte[] transcriptHash(byte[] serverSpki, byte[] clientSpki) throws GeneralSecurityException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(serverSpki);
        digest.update(clientSpki);
        return digest.digest();
    }

    static byte[] key(byte[] sharedSecret, String label, byte[] transcript) throws GeneralSecurityException {
        byte[] labelBytes = label.getBytes(StandardCharsets.UTF_8);
        byte[] info = new byte[labelBytes.length + transcript.length];
        System.arraycopy(labelBytes, 0, info, 0, labelBytes.length);
        System.arraycopy(transcript, 0, info, labelBytes.length, transcript.length);
        return WsSessionCrypto.hkdfSha256(sharedSecret, null, info, WsSessionCrypto.AES_KEY_LEN);
    }

    static byte[] nonce(int direction, long counter) {
        return ByteBuffer.allocate(WsSessionCrypto.NONCE_LEN)
                .putInt(direction)
                .putLong(counter)
                .array();
    }

    synchronized byte[] encrypt(byte[] plaintext) throws GeneralSecurityException {
        byte[] nonce = nonce(SERVER_TO_CLIENT, this.nextSendCounter++);
        byte[] ciphertext = WsSessionCrypto.aesGcmEncrypt(this.sendKey, nonce, plaintext);
        byte[] frame = new byte[nonce.length + ciphertext.length];
        System.arraycopy(nonce, 0, frame, 0, nonce.length);
        System.arraycopy(ciphertext, 0, frame, nonce.length, ciphertext.length);
        return frame;
    }

    /** Decrypts a client frame; a nonce of the wrong direction or not the next counter is refused. */
    synchronized byte[] decrypt(byte[] nonce, byte[] ciphertextWithTag) throws GeneralSecurityException {
        ByteBuffer read = ByteBuffer.wrap(nonce);
        int direction = read.getInt();
        long counter = read.getLong();
        if (direction != CLIENT_TO_SERVER) {
            throw new GeneralSecurityException("frame of the wrong direction");
        }
        if (counter != this.nextReceiveCounter) {
            throw new GeneralSecurityException("frame counter " + counter + ", expected " + this.nextReceiveCounter);
        }

        byte[] plaintext = WsSessionCrypto.aesGcmDecrypt(this.receiveKey, nonce, ciphertextWithTag);
        this.nextReceiveCounter++;
        return plaintext;
    }
}
