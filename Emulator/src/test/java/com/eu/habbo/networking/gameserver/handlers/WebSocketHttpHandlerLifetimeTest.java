package com.eu.habbo.networking.gameserver.handlers;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.eu.habbo.Emulator;
import com.eu.habbo.core.ConfigurationManager;
import com.eu.habbo.networking.gameserver.GameServer;
import com.eu.habbo.networking.gameserver.GameServerAttributes;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.ReferenceCountUtil;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/**
 * The handler removed itself after the first request of any kind, so on a keep-alive connection the next
 * request - an upgrade included - skipped the Origin check and kept the forwarded IP of the first one.
 */
class WebSocketHttpHandlerLifetimeTest {

    private static FullHttpRequest request(boolean upgrade) {
        FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/");
        if (upgrade) {
            request.headers().set(HttpHeaderNames.UPGRADE, "websocket");
            request.headers().set(HttpHeaderNames.CONNECTION, "Upgrade");
            request.headers().set("Origin", "http://localhost");
        }
        return request;
    }

    private static MockedStatic<Emulator> emulator() {
        ConfigurationManager config = mock(ConfigurationManager.class);
        when(config.getBoolean(anyString(), anyBoolean())).thenReturn(false);
        when(config.getValue(eq("ws.whitelist"), anyString())).thenReturn("localhost");
        when(config.getValue(eq("ws.ip.header"), anyString())).thenReturn("");
        GameServer server = mock(GameServer.class, RETURNS_DEEP_STUBS);
        MockedStatic<Emulator> emulator = mockStatic(Emulator.class);
        emulator.when(Emulator::getConfig).thenReturn(config);
        emulator.when(Emulator::getGameServer).thenReturn(server);
        return emulator;
    }

    @Test
    void plainRequestsKeepTheHandlerAndAnUpgradeEndsIt() {
        try (MockedStatic<Emulator> ignored = emulator()) {
            EmbeddedChannel channel = new EmbeddedChannel(new WebSocketHttpHandler());

            channel.writeInbound(request(false));
            ReferenceCountUtil.release(channel.readInbound());
            assertNotNull(channel.pipeline().get(WebSocketHttpHandler.class), "a plain request leaves it in place");

            channel.writeInbound(request(false));
            ReferenceCountUtil.release(channel.readInbound());
            assertNotNull(channel.pipeline().get(WebSocketHttpHandler.class), "still there for the next one");

            channel.writeInbound(request(true));
            ReferenceCountUtil.release(channel.readInbound());
            assertNull(channel.pipeline().get(WebSocketHttpHandler.class), "an accepted upgrade ends the HTTP phase");
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void anIpKeptFromAnEarlierRequestIsDropped() {
        try (MockedStatic<Emulator> ignored = emulator()) {
            EmbeddedChannel channel = new EmbeddedChannel(new WebSocketHttpHandler());
            channel.attr(GameServerAttributes.WS_IP).set("203.0.113.7");

            channel.writeInbound(request(false));
            ReferenceCountUtil.release(channel.readInbound());

            assertNull(channel.attr(GameServerAttributes.WS_IP).get(), "a request without the header carries no IP");
            channel.finishAndReleaseAll();
        }
    }
}
