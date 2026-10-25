package com.eu.habbo.networking.rconserver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.eu.habbo.Emulator;
import com.eu.habbo.core.ConfigurationManager;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.embedded.EmbeddedChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/**
 * Every TCP read was parsed as a complete request, so a command split over several segments - any long
 * one - failed as invalid JSON.
 */
class RCONServerFramingTest {

    private static MockedStatic<Emulator> emulator(int maxPayloadBytes) throws Exception {
        ConfigurationManager config = mock(ConfigurationManager.class);
        when(config.getInt(eq("rcon.max_payload_bytes"), anyInt())).thenReturn(maxPayloadBytes);
        RCONServer server = mock(RCONServer.class);
        server.allowedAdresses = List.of("embedded");
        when(server.handle(any(ChannelHandlerContext.class), anyString(), anyString()))
                .thenAnswer(call -> "{\"handled\":\"" + call.getArgument(1) + "\"}");
        MockedStatic<Emulator> emulator = mockStatic(Emulator.class);
        emulator.when(Emulator::getConfig).thenReturn(config);
        emulator.when(Emulator::getRconServer).thenReturn(server);
        return emulator;
    }

    private static EmbeddedChannel channel() {
        return new EmbeddedChannel(RCONServerHandler.framer(), new RCONServerHandler());
    }

    private static ByteBuf bytes(String text) {
        return Unpooled.copiedBuffer(text, StandardCharsets.UTF_8);
    }

    private static String response(EmbeddedChannel channel) {
        ByteBuf out = channel.readOutbound();
        try {
            return out.toString(StandardCharsets.UTF_8);
        } finally {
            out.release();
        }
    }

    @Test
    void aRequestSplitAcrossReadsIsHandledOnceWhole() throws Exception {
        try (MockedStatic<Emulator> ignored = emulator(64 * 1024)) {
            EmbeddedChannel channel = channel();
            String message = "x".repeat(4000);

            channel.writeInbound(bytes("{\"key\":\"alertuser\",\"da"));
            assertNull(channel.readOutbound(), "half an object is not answered");
            channel.writeInbound(bytes("ta\":{\"message\":\"" + message + "\"}}"));

            assertEquals("{\"handled\":\"alertuser\"}", response(channel));
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void anObjectOverTheLimitIsRefusedWhileItArrives() throws Exception {
        try (MockedStatic<Emulator> ignored = emulator(64)) {
            EmbeddedChannel channel = channel();

            channel.writeInbound(bytes("{\"key\":\"alertuser\",\"data\":{\"message\":\""));
            channel.writeInbound(bytes("y".repeat(200)));

            assertTrue(response(channel).contains("payload too large"));
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void somethingThatIsNotJsonIsAnInvalidRequest() throws Exception {
        try (MockedStatic<Emulator> ignored = emulator(64 * 1024)) {
            EmbeddedChannel channel = channel();

            channel.writeInbound(bytes("hello"));

            assertTrue(response(channel).contains("invalid request"));
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void theListenerFramesBeforeTheHandler() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/eu/habbo/networking/rconserver/RCONServer.java"));

        assertTrue(source.contains("addLast(RCONServerHandler.framer(), new RCONServerHandler())"));
    }
}
