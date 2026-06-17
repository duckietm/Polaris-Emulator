package com.eu.habbo.networking.gameserver.encoders;

import com.eu.habbo.networking.gameserver.GameServerAttributes;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.util.ReferenceCountUtil;

public class GameByteEncryption extends ChannelOutboundHandlerAdapter {

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        // convert to Bytebuf
        ByteBuf in = (ByteBuf) msg;

        // Copy the readable region into a plain array (respects readerIndex /
        // arrayOffset, so this is correct for pooled buffers too — buf.array()
        // would have returned the wrong region for a pooled/sliced buffer).
        byte[] bytes = new byte[in.readableBytes()];
        in.readBytes(bytes);

        //release old object
        ReferenceCountUtil.release(in);

        // Encrypt in place.
        ctx.channel().attr(GameServerAttributes.CRYPTO_SERVER).get().parse(bytes);

        // Continue in the pipeline.
        ctx.write(Unpooled.wrappedBuffer(bytes), promise);
    }
}
