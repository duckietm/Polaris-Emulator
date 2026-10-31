package com.eu.habbo.networking.gameserver.crypto;

import com.eu.habbo.networking.gameserver.GameServerAttributes;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WsAesDecoder extends MessageToMessageDecoder<ByteBuf> {
    private static final Logger LOGGER = LoggerFactory.getLogger(WsAesDecoder.class);

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        WsDirectionalSession session =
                ctx.channel().attr(WsDirectionalSession.ATTRIBUTE).get();
        byte[] key = ctx.channel().attr(GameServerAttributes.WS_AES_KEY).get();
        if (key == null && session == null) {
            LOGGER.warn("[ws-crypto] inbound frame with no session key, closing");
            ctx.close();
            return;
        }

        int readable = in.readableBytes();
        if (readable < WsSessionCrypto.NONCE_LEN + 16) {
            LOGGER.warn("[ws-crypto] inbound frame too short ({} bytes)", readable);
            ctx.close();
            return;
        }

        byte[] nonce = new byte[WsSessionCrypto.NONCE_LEN];
        in.readBytes(nonce);

        byte[] ct = new byte[in.readableBytes()];
        in.readBytes(ct);

        try {
            // Version 2 also refuses a replayed, reordered or reflected frame.
            byte[] plain =
                    (session != null) ? session.decrypt(nonce, ct) : WsSessionCrypto.aesGcmDecrypt(key, nonce, ct);
            out.add(Unpooled.wrappedBuffer(plain));
        } catch (Exception e) {
            LOGGER.warn(
                    "[ws-crypto] AES-GCM decrypt failed ({}), closing channel",
                    e.getClass().getSimpleName());
            ctx.close();
        }
    }
}
