package com.eu.habbo.networking.gameserver.decoders;

import com.eu.habbo.networking.gameserver.GameServerAttributes;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.util.List;

public class GameByteDecryption extends ByteToMessageDecoder {

    public GameByteDecryption() {
        setSingleDecode(true);
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        // Copy the readable region into a plain array (offset-safe, so this is
        // correct for pooled buffers too — buf.array() would have read the wrong
        // region for a pooled/sliced buffer).
        byte[] bytes = new byte[in.readableBytes()];
        in.readBytes(bytes);

        // Decrypt in place.
        ctx.channel().attr(GameServerAttributes.CRYPTO_CLIENT).get().parse(bytes);

        // Continue in the pipeline.
        out.add(Unpooled.wrappedBuffer(bytes));
    }

}
