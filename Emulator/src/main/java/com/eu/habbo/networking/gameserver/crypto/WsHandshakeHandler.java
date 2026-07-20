package com.eu.habbo.networking.gameserver.crypto;

import com.eu.habbo.Emulator;
import com.eu.habbo.networking.gameserver.GameServerAttributes;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.ArrayDeque;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WsHandshakeHandler extends ChannelInboundHandlerAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger(WsHandshakeHandler.class);
    public static final String HANDLER_NAME = "wsCryptoHandshake";
    private static final int MAX_PENDING_HANDSHAKE_FRAMES = 64;
    private static final int MAX_PENDING_HANDSHAKE_BYTES = 1024 * 1024;
    private final Executor cryptoExecutor;
    private final boolean signingEnabled;
    private KeyPair serverKeyPair;
    private boolean helloStarted = false;
    private boolean helloSent = false;
    private boolean agreementStarted = false;
    private boolean handshakeComplete = false;
    private ArrayDeque<ByteBuf> pendingFrames;
    private int pendingFrameBytes;

    public WsHandshakeHandler() {
        this(WsCryptoExecutor.executor(), signingEnabled());
    }

    WsHandshakeHandler(Executor cryptoExecutor, boolean signingEnabled) {
        this.cryptoExecutor = Objects.requireNonNull(cryptoExecutor, "cryptoExecutor");
        this.signingEnabled = signingEnabled;
    }

    private static boolean signingEnabled() {
        var configuration = Emulator.getConfig();
        return configuration != null && configuration.getBoolean("crypto.ws.signing.enabled", false);
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof WebSocketServerProtocolHandler.HandshakeComplete) {
            sendServerHello(ctx, evt);
            return;
        }
        super.userEventTriggered(ctx, evt);
    }

    private void sendServerHello(ChannelHandlerContext ctx, Object handshakeEvent) throws Exception {
        if (helloStarted) {
            if (helloSent) {
                super.userEventTriggered(ctx, handshakeEvent);
            }
            return;
        }
        helloStarted = true;

        try {
            this.cryptoExecutor.execute(() -> createServerHello(ctx, handshakeEvent));
        } catch (RejectedExecutionException rejected) {
            LOGGER.warn("[ws-crypto] handshake executor is full; " + "closing {}", clientAddress(ctx));
            ctx.close();
        }
    }

    private void createServerHello(ChannelHandlerContext ctx, Object handshakeEvent) {
        try {
            KeyPair keyPair = WsSessionCrypto.generateEphemeralKeyPair();
            byte[] spki = WsSessionCrypto.encodePublicKeySpki(keyPair.getPublic());
            byte[] signature = null;
            if (this.signingEnabled) {
                KeyPair signingKeyPair = CryptoSigningKeyManager.get();
                byte[] der = WsSessionCrypto.signEcdsaSha256(signingKeyPair.getPrivate(), spki);
                signature = WsSessionCrypto.derToIeee1363(der);
            }

            ServerHello serverHello = new ServerHello(keyPair, spki, signature);
            executeOnIoThread(ctx, () -> finishServerHello(ctx, handshakeEvent, serverHello));
        } catch (Exception exception) {
            executeOnIoThread(ctx, () -> failServerHello(ctx, exception));
        }
    }

    private void finishServerHello(ChannelHandlerContext ctx, Object handshakeEvent, ServerHello serverHello) {
        if (!ctx.channel().isActive()) {
            return;
        }

        this.serverKeyPair = serverHello.keyPair();
        byte[] spki = serverHello.spki();
        byte[] signature = serverHello.signature();
        int frameLength = 4 + 1 + 2 + spki.length + (signature != null ? 2 + signature.length : 0);
        ByteBuf buffer = ctx.alloc().buffer(frameLength);
        buffer.writeInt(WsSessionCrypto.HANDSHAKE_MAGIC);
        buffer.writeByte(WsSessionCrypto.TYPE_SERVER_HELLO);
        buffer.writeShort(spki.length);
        buffer.writeBytes(spki);
        if (signature != null) {
            buffer.writeShort(signature.length);
            buffer.writeBytes(signature);
        }

        ctx.writeAndFlush(buffer);
        this.helloSent = true;
        ctx.fireUserEventTriggered(handshakeEvent);
    }

    private static void failServerHello(ChannelHandlerContext ctx, Exception exception) {
        LOGGER.error("[ws-crypto] failed to send server_hello", exception);
        ctx.close();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (handshakeComplete) {
            ctx.fireChannelRead(msg);
            return;
        }

        if (agreementStarted) {
            // Key agreement is running on the crypto pool. The client derives the
            // session key locally and can start sending AES frames before the
            // decoder is installed; queue them and replay once finishAgreement
            // completes, instead of misreading them as handshake frames and
            // closing the connection.
            if (!(msg instanceof ByteBuf)) {
                ctx.fireChannelRead(msg);
                return;
            }
            ByteBuf frame = (ByteBuf) msg;
            if (this.pendingFrames == null) {
                this.pendingFrames = new ArrayDeque<>();
            }
            if (this.pendingFrames.size() >= MAX_PENDING_HANDSHAKE_FRAMES
                    || frame.readableBytes() > MAX_PENDING_HANDSHAKE_BYTES - this.pendingFrameBytes) {
                LOGGER.warn("[ws-crypto] too many frames during key agreement from {}", clientAddress(ctx));
                frame.release();
                ctx.close();
                return;
            }
            this.pendingFrames.add(frame);
            this.pendingFrameBytes += frame.readableBytes();
            return;
        }

        if (!(msg instanceof ByteBuf)) {
            ctx.fireChannelRead(msg);
            return;
        }

        ByteBuf in = (ByteBuf) msg;
        try {
            if (in.readableBytes() < 7) {
                LOGGER.warn(
                        "[ws-crypto] handshake frame too short ({} bytes) from {}",
                        in.readableBytes(),
                        clientAddress(ctx));
                ctx.close();
                return;
            }

            int magic = in.readInt();
            if (magic != WsSessionCrypto.HANDSHAKE_MAGIC) {
                LOGGER.warn(
                        "[ws-crypto] handshake magic mismatch: 0x{} from {}",
                        Integer.toHexString(magic),
                        clientAddress(ctx));
                ctx.close();
                return;
            }

            byte type = in.readByte();
            if (type != WsSessionCrypto.TYPE_CLIENT_HELLO) {
                LOGGER.warn(
                        "[ws-crypto] expected client_hello, got type=0x{} from {}",
                        Integer.toHexString(type & 0xff),
                        clientAddress(ctx));
                ctx.close();
                return;
            }

            int keyLen = in.readUnsignedShort();
            if (keyLen <= 0 || keyLen > in.readableBytes() || keyLen > 2048) {
                LOGGER.warn("[ws-crypto] invalid client key length {} from {}", keyLen, clientAddress(ctx));
                ctx.close();
                return;
            }

            byte[] clientSpki = new byte[keyLen];
            in.readBytes(clientSpki);

            if (!helloSent || serverKeyPair == null || agreementStarted) {
                LOGGER.warn("[ws-crypto] unexpected client_hello " + "state from {}", clientAddress(ctx));
                ctx.close();
                return;
            }
            this.agreementStarted = true;
            PrivateKey serverPrivate = this.serverKeyPair.getPrivate();
            submitAgreement(ctx, serverPrivate, clientSpki);
        } catch (Exception e) {
            LOGGER.warn("[ws-crypto] handshake failed from {} : {}", clientAddress(ctx), friendlyReason(e));
            ctx.close();
        } finally {
            in.release();
        }
    }

    private void submitAgreement(ChannelHandlerContext ctx, PrivateKey serverPrivate, byte[] clientSpki) {
        try {
            this.cryptoExecutor.execute(() -> deriveSessionKey(ctx, serverPrivate, clientSpki));
        } catch (RejectedExecutionException rejected) {
            LOGGER.warn("[ws-crypto] handshake executor is full; " + "closing {}", clientAddress(ctx));
            ctx.close();
        }
    }

    private void deriveSessionKey(ChannelHandlerContext ctx, PrivateKey serverPrivate, byte[] clientSpki) {
        try {
            PublicKey clientPublic = WsSessionCrypto.decodePublicKeySpki(clientSpki);
            byte[] sharedSecret = WsSessionCrypto.deriveSharedSecret(serverPrivate, clientPublic);
            byte[] sessionKey = WsSessionCrypto.deriveAesKey(sharedSecret);
            executeOnIoThread(ctx, () -> finishAgreement(ctx, sessionKey));
        } catch (Exception exception) {
            executeOnIoThread(ctx, () -> failAgreement(ctx, exception));
        }
    }

    private void finishAgreement(ChannelHandlerContext ctx, byte[] sessionKey) {
        if (!ctx.channel().isActive()) {
            return;
        }

        ctx.channel().attr(GameServerAttributes.WS_AES_KEY).set(sessionKey);
        ChannelPipeline pipeline = ctx.pipeline();
        pipeline.addAfter(HANDLER_NAME, "wsAesDecoder", new WsAesDecoder());
        pipeline.addAfter(HANDLER_NAME, "wsAesEncoder", new WsAesEncoder());
        this.handshakeComplete = true;
        replayPendingFrames(ctx);
        pipeline.remove(this);

        LOGGER.debug("[ws-crypto] handshake complete for {}", clientAddress(ctx));
    }

    private void replayPendingFrames(ChannelHandlerContext ctx) {
        if (this.pendingFrames == null) {
            return;
        }
        // Fire queued frames through the now-installed AES decoder, in arrival
        // order, while this handler is still in the pipeline so ctx stays valid.
        ArrayDeque<ByteBuf> queued = this.pendingFrames;
        this.pendingFrames = null;
        this.pendingFrameBytes = 0;
        try {
            ByteBuf frame;
            while ((frame = queued.poll()) != null) {
                ctx.fireChannelRead(frame);
            }
        } finally {
            ByteBuf leftover;
            while ((leftover = queued.poll()) != null) {
                leftover.release();
            }
        }
    }

    private void releasePendingFrames() {
        if (this.pendingFrames == null) {
            return;
        }
        ByteBuf frame;
        while ((frame = this.pendingFrames.poll()) != null) {
            frame.release();
        }
        this.pendingFrames = null;
        this.pendingFrameBytes = 0;
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) {
        releasePendingFrames();
    }

    private static void failAgreement(ChannelHandlerContext ctx, Exception exception) {
        LOGGER.warn("[ws-crypto] handshake failed from {} : {}", clientAddress(ctx), friendlyReason(exception));
        ctx.close();
    }

    private static void executeOnIoThread(ChannelHandlerContext ctx, Runnable task) {
        try {
            ctx.executor().execute(task);
        } catch (RejectedExecutionException rejected) {
            ctx.close();
        }
    }

    private static String clientAddress(ChannelHandlerContext ctx) {
        String wsIp = ctx.channel().attr(GameServerAttributes.WS_IP).get();
        if (wsIp != null && !wsIp.isEmpty()) return wsIp;
        return String.valueOf(ctx.channel().remoteAddress());
    }

    private static String friendlyReason(Throwable t) {
        if (t == null) return "unknown";
        String name = t.getClass().getSimpleName();
        String msg = t.getMessage();
        return (msg == null || msg.isEmpty()) ? name : name + ": " + msg;
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (cause instanceof java.io.IOException) {
            LOGGER.debug(
                    "[ws-crypto] client disconnected during handshake ({}): {}",
                    clientAddress(ctx),
                    friendlyReason(cause));
        } else {
            LOGGER.error("[ws-crypto] handshake handler error from " + clientAddress(ctx), cause);
        }
        ctx.close();
    }

    private record ServerHello(KeyPair keyPair, byte[] spki, byte[] signature) {}
}
