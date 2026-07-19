package com.eu.habbo.networking.rconserver;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RCONServerHandlerWriteTest {

    @Test
    void responseUsesItsExactUtf8Bytes() {
        ByteBuf response = RCONServerHandler.responseBuffer("{\"message\":\"på gensyn\"}");
        try {
            assertEquals("{\"message\":\"på gensyn\"}", response.toString(StandardCharsets.UTF_8));
        } finally {
            response.release();
        }
    }

    @Test
    void connectionClosesOnlyAfterTheResponseWriteCompletes() {
        ChannelHandlerContext context = mock(ChannelHandlerContext.class);
        Channel channel = mock(Channel.class);
        ChannelPromise voidPromise = mock(ChannelPromise.class);
        ChannelFuture legacyFuture = mock(ChannelFuture.class);
        ChannelFuture responseFuture = mock(ChannelFuture.class);
        when(context.channel()).thenReturn(channel);
        when(channel.voidPromise()).thenReturn(voidPromise);
        when(channel.write(any(ByteBuf.class), eq(voidPromise))).thenAnswer(invocation -> {
            ((ByteBuf) invocation.getArgument(0)).release();
            return legacyFuture;
        });
        when(legacyFuture.channel()).thenReturn(channel);
        when(context.writeAndFlush(any(ByteBuf.class))).thenAnswer(invocation -> {
            ((ByteBuf) invocation.getArgument(0)).release();
            return responseFuture;
        });

        RCONServerHandler.writeAndClose(context, "{\"status\":1}");

        verify(context).writeAndFlush(any(ByteBuf.class));
        verify(responseFuture).addListener(ChannelFutureListener.CLOSE);
        verify(channel, never()).voidPromise();
        verify(channel, never()).close();
    }
}
