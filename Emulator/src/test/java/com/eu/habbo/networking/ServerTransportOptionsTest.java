package com.eu.habbo.networking;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.AdaptiveRecvByteBufAllocator;
import io.netty.channel.ChannelOption;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class ServerTransportOptionsTest {

    @Test
    void configuresBacklogAndAdaptiveChildReceiveBuffers() {
        ServerBootstrap bootstrap = new ServerBootstrap();

        Server.configureTransportOptions(bootstrap);

        Map<ChannelOption<?>, Object> parent =
                bootstrap.config().options();
        Map<ChannelOption<?>, Object> child =
                bootstrap.config().childOptions();
        assertEquals(1024, parent.get(ChannelOption.SO_BACKLOG));
        assertSame(Boolean.TRUE, child.get(ChannelOption.TCP_NODELAY));
        assertSame(Boolean.TRUE, child.get(ChannelOption.SO_KEEPALIVE));
        assertSame(Boolean.TRUE, child.get(ChannelOption.SO_REUSEADDR));
        assertNull(child.get(ChannelOption.SO_RCVBUF));
        assertInstanceOf(
                AdaptiveRecvByteBufAllocator.class,
                child.get(ChannelOption.RCVBUF_ALLOCATOR));
        assertSame(
                Server.allocator(),
                child.get(ChannelOption.ALLOCATOR));
    }
}
