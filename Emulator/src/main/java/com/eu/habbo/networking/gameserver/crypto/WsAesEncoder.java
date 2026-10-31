package com.eu.habbo.networking.gameserver.crypto;

import com.eu.habbo.networking.gameserver.GameServerAttributes;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class WsAesEncoder extends MessageToMessageEncoder<ByteBuf> {
    private static final Logger LOGGER = LoggerFactory.getLogger(WsAesEncoder.class);

    @Override
    protected void encode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        WsDirectionalSession session = ctx.channel().attr(WsDirectionalSession.ATTRIBUTE).get();
        if (session != null) {
            byte[] plain = new byte[in.readableBytes()];
            in.readBytes(plain);
            out.add(ctx.alloc().buffer().writeBytes(session.encrypt(plain)));
            return;
        }

        byte[] key = ctx.channel().attr(GameServerAttributes.WS_AES_KEY).get();
        if (key == null) {
            LOGGER.warn("[ws-crypto] outbound frame with no session key, dropping");
            return;
        }

        byte[] plain = new byte[in.readableBytes()];
        in.readBytes(plain);

        byte[] nonce = WsSessionCrypto.randomNonce();
        byte[] ct = WsSessionCrypto.aesGcmEncrypt(key, nonce, plain);

        ByteBuf framed = ctx.alloc().buffer(nonce.length + ct.length);
        framed.writeBytes(nonce);
        framed.writeBytes(ct);
        out.add(framed);
    }
}
