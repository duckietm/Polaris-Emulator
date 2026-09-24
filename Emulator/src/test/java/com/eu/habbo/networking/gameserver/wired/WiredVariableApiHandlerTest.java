package com.eu.habbo.networking.gameserver.wired;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraVariableWebApi;
import com.eu.habbo.networking.gameserver.wired.WiredApiRooms.Scope;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class WiredVariableApiHandlerTest {
    private static final String READ = WiredExtraVariableWebApi.mintKey();
    private static final String WRITE = WiredExtraVariableWebApi.mintKey();

    private EmbeddedChannel channel() {
        FakeWiredApiRooms rooms = new FakeWiredApiRooms();
        FakeWiredApiRooms.FakeRoom room = rooms.room(5, READ, WRITE);
        room.variable("points", Scope.USER, true);
        room.user(1, "alice");
        room.hold("points", 1, 12);
        WiredApiRouter router = new WiredApiRouter(
                () -> WiredApiSettings.defaults(true), rooms, new WiredApiLimits(System::currentTimeMillis));
        return new EmbeddedChannel(new WiredVariableApiHandler(router));
    }

    @Test
    void answersApiRequestsOnTheChannel() {
        EmbeddedChannel channel = this.channel();
        FullHttpRequest request = new DefaultFullHttpRequest(
                HttpVersion.HTTP_1_1, HttpMethod.GET, "/api/public/rooms/5/variables/user/points/users/1");
        request.headers().set(HttpHeaderNames.AUTHORIZATION, "Bearer " + READ);

        channel.writeInbound(request);
        FullHttpResponse response = channel.readOutbound();

        assertEquals(200, response.status().code());
        String body = response.content().toString(StandardCharsets.UTF_8);
        assertTrue(body.contains("\"value\":12"), body);
        assertEquals(body.length(), response.headers().getInt(HttpHeaderNames.CONTENT_LENGTH));
        assertEquals("application/json; charset=utf-8", response.headers().get(HttpHeaderNames.CONTENT_TYPE));
        assertEquals(0, request.refCnt());
        response.release();
        channel.finishAndReleaseAll();
    }

    @Test
    void writesThroughThePipeline() {
        EmbeddedChannel channel = this.channel();
        FullHttpRequest request = new DefaultFullHttpRequest(
                HttpVersion.HTTP_1_1,
                HttpMethod.PATCH,
                "/api/public/rooms/5/variables/user/points/users/1",
                Unpooled.copiedBuffer("{\"add\":3}", StandardCharsets.UTF_8));
        request.headers().set(HttpHeaderNames.AUTHORIZATION, "Bearer " + WRITE);
        request.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json");

        channel.writeInbound(request);
        FullHttpResponse response = channel.readOutbound();

        assertEquals(200, response.status().code());
        assertTrue(response.content().toString(StandardCharsets.UTF_8).contains("\"value\":15"));
        response.release();
        channel.finishAndReleaseAll();
    }

    @Test
    void refusesAKeyInTheQueryString() {
        EmbeddedChannel channel = this.channel();

        channel.writeInbound(new DefaultFullHttpRequest(
                HttpVersion.HTTP_1_1, HttpMethod.GET, "/api/public/rooms/5/variables?key=" + READ));
        FullHttpResponse response = channel.readOutbound();

        assertEquals(400, response.status().code());
        response.release();
        channel.finishAndReleaseAll();
    }

    @Test
    void passesEverythingElseDownThePipeline() {
        EmbeddedChannel channel = this.channel();
        FullHttpRequest request =
                new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/api/wired/variable");

        channel.writeInbound(request);

        assertNull(channel.readOutbound());
        assertSame(request, channel.readInbound());
        request.release();
        channel.finishAndReleaseAll();
    }

    @Test
    void malformedEscapesAreABadRequest() {
        EmbeddedChannel channel = this.channel();

        channel.writeInbound(new DefaultFullHttpRequest(
                HttpVersion.HTTP_1_1, HttpMethod.GET, "/api/public/rooms/5/variables?page=%zz"));
        FullHttpResponse response = channel.readOutbound();

        assertEquals(400, response.status().code());
        response.release();
        channel.finishAndReleaseAll();
    }
}
