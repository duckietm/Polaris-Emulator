package com.eu.habbo.networking.gameserver.handlers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.netty.channel.ChannelHandlerContext;
import java.net.InetSocketAddress;
import org.junit.jupiter.api.Test;

/** The per-IP limit applied only after the WebSocket upgrade, so one address could open any number of HTTP connections. */
class PreUpgradeConnectionLimitTest {

    private static ChannelHandlerContext from(String ip) {
        ChannelHandlerContext ctx = mock(ChannelHandlerContext.class, RETURNS_DEEP_STUBS);
        when(ctx.channel().remoteAddress()).thenReturn(new InetSocketAddress(ip, 40000));
        return ctx;
    }

    @Test
    void aClientOverItsShareIsClosedAndAClosedOneGivesItsPlaceBack() throws Exception {
        PreUpgradeConnectionLimit first = new PreUpgradeConnectionLimit(2);
        PreUpgradeConnectionLimit second = new PreUpgradeConnectionLimit(2);
        PreUpgradeConnectionLimit third = new PreUpgradeConnectionLimit(2);
        ChannelHandlerContext a = from("203.0.113.7");
        ChannelHandlerContext b = from("203.0.113.7");
        ChannelHandlerContext c = from("203.0.113.7");

        first.channelActive(a);
        second.channelActive(b);
        third.channelActive(c);
        verify(a, never()).close();
        verify(c).close();

        third.channelInactive(c);
        first.handlerRemoved(a);
        first.channelInactive(a);
        assertEquals(1, PreUpgradeConnectionLimit.openFor("203.0.113.7"));

        second.channelInactive(b);
        assertEquals(0, PreUpgradeConnectionLimit.openFor("203.0.113.7"));
    }

    @Test
    void aTrustedProxyIsNotCounted() throws Exception {
        PreUpgradeConnectionLimit limit = new PreUpgradeConnectionLimit(1);
        ChannelHandlerContext local = from("127.0.0.1");

        limit.channelActive(local);
        new PreUpgradeConnectionLimit(1).channelActive(from("127.0.0.1"));

        verify(local, never()).close();
        assertEquals(0, PreUpgradeConnectionLimit.openFor("127.0.0.1"));
    }
}
