package com.eu.habbo.networking.gameserver.crypto;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eu.habbo.networking.gameserver.GameServerAttributes;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.EmptyHttpHeaders;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import java.security.KeyPair;
import java.security.PublicKey;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class WsHandshakeOffloadTest {

    @Test
    void keyGenerationLeavesTheCallingIoThread() throws Exception {
        LinkedBlockingQueue<Runnable> cryptoTasks = new LinkedBlockingQueue<>();
        WsHandshakeHandler handler = new WsHandshakeHandler(cryptoTasks::add, false);
        EmbeddedChannel channel = new EmbeddedChannel();
        channel.pipeline().addLast(WsHandshakeHandler.HANDLER_NAME, handler);
        try {
            channel.pipeline()
                    .fireUserEventTriggered(
                            new WebSocketServerProtocolHandler.HandshakeComplete("/", EmptyHttpHeaders.INSTANCE, null));

            assertNull(channel.readOutbound());
            assertEquals(1, cryptoTasks.size());

            runCryptoTask(cryptoTasks);
            channel.runPendingTasks();

            ByteBuf serverHello = channel.readOutbound();
            assertNotNull(serverHello);
            serverHello.release();
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void keyAgreementLeavesTheCallingIoThread() throws Exception {
        LinkedBlockingQueue<Runnable> cryptoTasks = new LinkedBlockingQueue<>();
        WsHandshakeHandler handler = new WsHandshakeHandler(cryptoTasks::add, false);
        EmbeddedChannel channel = new EmbeddedChannel();
        channel.pipeline().addLast(WsHandshakeHandler.HANDLER_NAME, handler);
        try {
            channel.pipeline()
                    .fireUserEventTriggered(
                            new WebSocketServerProtocolHandler.HandshakeComplete("/", EmptyHttpHeaders.INSTANCE, null));
            runCryptoTask(cryptoTasks);
            channel.runPendingTasks();
            ByteBuf serverHello = channel.readOutbound();
            assertNotNull(serverHello);
            serverHello.release();

            KeyPair clientKeyPair = WsSessionCrypto.generateEphemeralKeyPair();
            byte[] clientSpki = WsSessionCrypto.encodePublicKeySpki(clientKeyPair.getPublic());
            ByteBuf clientHello = Unpooled.buffer(7 + clientSpki.length)
                    .writeInt(WsSessionCrypto.HANDSHAKE_MAGIC)
                    .writeByte(WsSessionCrypto.TYPE_CLIENT_HELLO)
                    .writeShort(clientSpki.length)
                    .writeBytes(clientSpki);

            assertFalse(channel.writeInbound(clientHello));
            assertNull(channel.attr(GameServerAttributes.WS_AES_KEY).get());
            assertEquals(1, cryptoTasks.size());

            runCryptoTask(cryptoTasks);
            channel.runPendingTasks();

            assertNotNull(channel.attr(GameServerAttributes.WS_AES_KEY).get());
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void framesArrivingDuringKeyAgreementDoNotCloseTheConnection() throws Exception {
        LinkedBlockingQueue<Runnable> cryptoTasks = new LinkedBlockingQueue<>();
        WsHandshakeHandler handler = new WsHandshakeHandler(cryptoTasks::add, false);
        EmbeddedChannel channel = new EmbeddedChannel();
        channel.pipeline().addLast(WsHandshakeHandler.HANDLER_NAME, handler);
        try {
            byte[] sessionKey = beginAgreement(channel, cryptoTasks);

            // The client derives the key locally and can send an AES frame before
            // the decoder is installed. It must be queued, not parsed as handshake.
            byte[] plaintext = new byte[] {0, 1, 2, 3, 4, 5, 6, 7};
            byte[] nonce = WsSessionCrypto.randomNonce();
            byte[] ciphertext = WsSessionCrypto.aesGcmEncrypt(sessionKey, nonce, plaintext);
            ByteBuf earlyFrame = Unpooled.buffer(nonce.length + ciphertext.length)
                    .writeBytes(nonce)
                    .writeBytes(ciphertext);
            channel.writeInbound(earlyFrame);

            assertTrue(channel.isActive());
            assertEquals(1, cryptoTasks.size());

            runCryptoTask(cryptoTasks);
            channel.runPendingTasks();

            ByteBuf replayed = channel.readInbound();
            byte[] actual = new byte[replayed.readableBytes()];
            replayed.readBytes(actual);
            replayed.release();
            assertArrayEquals(plaintext, actual);
            assertEquals(0, earlyFrame.refCnt());
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void pendingFramesAreBoundedByBytesAndReleasedOnClose() throws Exception {
        LinkedBlockingQueue<Runnable> cryptoTasks = new LinkedBlockingQueue<>();
        EmbeddedChannel channel = new EmbeddedChannel();
        channel.pipeline().addLast(WsHandshakeHandler.HANDLER_NAME, new WsHandshakeHandler(cryptoTasks::add, false));
        ByteBuf first = Unpooled.buffer(500_000).writeZero(500_000);
        ByteBuf second = Unpooled.buffer(500_000).writeZero(500_000);
        ByteBuf overflow = Unpooled.buffer(500_000).writeZero(500_000);
        try {
            beginAgreement(channel, cryptoTasks);

            channel.writeInbound(first);
            channel.writeInbound(second);
            channel.writeInbound(overflow);

            assertFalse(channel.isOpen());
            assertEquals(0, overflow.refCnt());
        } finally {
            channel.finishAndReleaseAll();
        }
        assertEquals(0, first.refCnt());
        assertEquals(0, second.refCnt());
    }

    private static byte[] beginAgreement(EmbeddedChannel channel, LinkedBlockingQueue<Runnable> cryptoTasks)
            throws Exception {
        channel.pipeline()
                .fireUserEventTriggered(
                        new WebSocketServerProtocolHandler.HandshakeComplete("/", EmptyHttpHeaders.INSTANCE, null));
        runCryptoTask(cryptoTasks);
        channel.runPendingTasks();

        ByteBuf serverHello = channel.readOutbound();
        serverHello.skipBytes(5);
        int serverKeyLength = serverHello.readUnsignedShort();
        byte[] serverSpki = new byte[serverKeyLength];
        serverHello.readBytes(serverSpki);
        serverHello.release();

        KeyPair clientKeyPair = WsSessionCrypto.generateEphemeralKeyPair();
        PublicKey serverPublic = WsSessionCrypto.decodePublicKeySpki(serverSpki);
        byte[] sessionKey = WsSessionCrypto.deriveAesKey(
                WsSessionCrypto.deriveSharedSecret(clientKeyPair.getPrivate(), serverPublic));
        byte[] clientSpki = WsSessionCrypto.encodePublicKeySpki(clientKeyPair.getPublic());
        ByteBuf clientHello = Unpooled.buffer(7 + clientSpki.length)
                .writeInt(WsSessionCrypto.HANDSHAKE_MAGIC)
                .writeByte(WsSessionCrypto.TYPE_CLIENT_HELLO)
                .writeShort(clientSpki.length)
                .writeBytes(clientSpki);
        assertFalse(channel.writeInbound(clientHello));
        assertEquals(1, cryptoTasks.size());
        return sessionKey;
    }

    private static void runCryptoTask(LinkedBlockingQueue<Runnable> cryptoTasks) throws InterruptedException {
        Thread worker = new Thread(cryptoTasks.remove(), "test-ws-crypto-worker");
        worker.start();
        worker.join(TimeUnit.SECONDS.toMillis(1));
        assertFalse(worker.isAlive());
    }
}
