package com.eu.habbo.networking.gameserver.encoders;

import com.eu.habbo.monitoring.EmulatorNetworkStats;
import com.eu.habbo.messages.ServerMessage;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.util.IllegalReferenceCountException;

import java.io.IOException;

public class GameServerMessageEncoder extends MessageToByteEncoder<ServerMessage> {

    @Override
    protected void encode(ChannelHandlerContext ctx, ServerMessage message, ByteBuf out) throws Exception {
        try {
            ByteBuf buf = message.get();

            try {
                EmulatorNetworkStats.recordOutgoing(buf.readableBytes());
                out.writeBytes(buf);
            } finally {
                // Release copied buffer.
                buf.release();
            }
        } catch (IllegalReferenceCountException e) {
            throw new IOException(String.format("IllegalReferenceCountException happened for ServerMessage with packet id %d.", message.getHeader()), e);
        }
    }

}
