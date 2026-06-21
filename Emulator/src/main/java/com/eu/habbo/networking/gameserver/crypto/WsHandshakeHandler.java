package com.eu.habbo.networking.gameserver.crypto;

import com.eu.habbo.Emulator;
import com.eu.habbo.networking.gameserver.GameServerAttributes;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;

public class WsHandshakeHandler extends ChannelInboundHandlerAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger(WsHandshakeHandler.class);
    public static final String HANDLER_NAME = "wsCryptoHandshake";
    private static final boolean SIGN_ENABLED = Emulator.getConfig().getBoolean("crypto.ws.signing.enabled", false);
    private KeyPair serverKeyPair;
    private boolean helloSent = false;
    private boolean handshakeComplete = false;

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof WebSocketServerProtocolHandler.HandshakeComplete) {
            sendServerHello(ctx);
        }
        super.userEventTriggered(ctx, evt);
    }

    private void sendServerHello(ChannelHandlerContext ctx) {
        if (helloSent) return;
        try {
            this.serverKeyPair = WsSessionCrypto.generateEphemeralKeyPair();
            byte[] spki = WsSessionCrypto.encodePublicKeySpki(serverKeyPair.getPublic());
            byte[] sigIeee = null;
            if (SIGN_ENABLED) {
                KeyPair signingKp = CryptoSigningKeyManager.get();
                byte[] sigDer = WsSessionCrypto.signEcdsaSha256(signingKp.getPrivate(), spki);
                sigIeee = WsSessionCrypto.derToIeee1363(sigDer);
            }

            int frameLen = 4 + 1 + 2 + spki.length + (sigIeee != null ? 2 + sigIeee.length : 0);
            ByteBuf buf = ctx.alloc().buffer(frameLen);
            buf.writeInt(WsSessionCrypto.HANDSHAKE_MAGIC);
            buf.writeByte(WsSessionCrypto.TYPE_SERVER_HELLO);
            buf.writeShort(spki.length);
            buf.writeBytes(spki);
            if (sigIeee != null) {
                buf.writeShort(sigIeee.length);
                buf.writeBytes(sigIeee);
            }

            ctx.writeAndFlush(buf);
            helloSent = true;
        } catch (Exception e) {
            LOGGER.error("[ws-crypto] failed to send server_hello", e);
            ctx.close();
        }
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (handshakeComplete) {
            ctx.fireChannelRead(msg);
            return;
        }

        if (!(msg instanceof ByteBuf in)) {
            ctx.fireChannelRead(msg);
            return;
        }

        try {
            if (in.readableBytes() < 7) {
                LOGGER.warn("[ws-crypto] handshake frame too short ({} bytes) from {}", in.readableBytes(), clientAddress(ctx));
                ctx.close();
                return;
            }

            int magic = in.readInt();
            if (magic != WsSessionCrypto.HANDSHAKE_MAGIC) {
                LOGGER.warn("[ws-crypto] handshake magic mismatch: 0x{} from {}", Integer.toHexString(magic), clientAddress(ctx));
                ctx.close();
                return;
            }

            byte type = in.readByte();
            if (type != WsSessionCrypto.TYPE_CLIENT_HELLO) {
                LOGGER.warn("[ws-crypto] expected client_hello, got type=0x{} from {}", Integer.toHexString(type & 0xff), clientAddress(ctx));
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

            PublicKey clientPub = WsSessionCrypto.decodePublicKeySpki(clientSpki);
            PrivateKey ourPriv = serverKeyPair.getPrivate();
            byte[] shared = WsSessionCrypto.deriveSharedSecret(ourPriv, clientPub);
            byte[] aesKey = WsSessionCrypto.deriveAesKey(shared);
            ctx.channel().attr(GameServerAttributes.WS_AES_KEY).set(aesKey);
            ChannelPipeline p = ctx.pipeline();
            p.addAfter(HANDLER_NAME, "wsAesDecoder", new WsAesDecoder());
            p.addAfter(HANDLER_NAME, "wsAesEncoder", new WsAesEncoder());
            handshakeComplete = true;
            p.remove(this);

            LOGGER.debug("[ws-crypto] handshake complete for {}", clientAddress(ctx));
        } catch (Exception e) {
            LOGGER.warn("[ws-crypto] handshake failed from {} : {}", clientAddress(ctx), friendlyReason(e));
            ctx.close();
        } finally {
            in.release();
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
            LOGGER.debug("[ws-crypto] client disconnected during handshake ({}): {}",
                    clientAddress(ctx), friendlyReason(cause));
        } else {
            LOGGER.error("[ws-crypto] handshake handler error from " + clientAddress(ctx), cause);
        }
        ctx.close();
    }
}
