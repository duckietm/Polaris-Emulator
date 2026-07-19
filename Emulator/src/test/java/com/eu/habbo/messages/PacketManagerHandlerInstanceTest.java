package com.eu.habbo.messages;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.messages.incoming.MessageHandler;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;

class PacketManagerHandlerInstanceTest {

    @Test
    void createsAFreshHandlerWithExactInvocationStateForEveryPacket()
            throws Exception {
        PacketManager manager = mock(
                PacketManager.class,
                CALLS_REAL_METHODS);
        setField(manager, "incoming", new HashMap<>());
        setField(manager, "callables", new HashMap<>());
        manager.registerHandler(987654, RecordingHandler.class);
        GameClient client = mock(GameClient.class);
        ClientMessage first =
                new ClientMessage(987654, Unpooled.buffer(0));
        ClientMessage second =
                new ClientMessage(987654, Unpooled.buffer(0));
        boolean originallyShuttingDown = Emulator.isShuttingDown;
        Emulator.isShuttingDown = false;
        RecordingHandler.invocations.clear();

        try {
            manager.handlePacket(client, first);
            manager.handlePacket(client, second);
        } finally {
            Emulator.isShuttingDown = originallyShuttingDown;
        }

        assertEquals(2, RecordingHandler.invocations.size());
        RecordingHandler firstHandler =
                RecordingHandler.invocations.get(0);
        RecordingHandler secondHandler =
                RecordingHandler.invocations.get(1);
        assertNotSame(firstHandler, secondHandler);
        assertSame(client, firstHandler.client);
        assertSame(first, firstHandler.packet);
        assertSame(client, secondHandler.client);
        assertSame(second, secondHandler.packet);
    }

    @Test
    void reusesTheResolvedConstructorWithoutReusingHandlers() {
        assertSame(
                PacketManager.constructorFor(RecordingHandler.class),
                PacketManager.constructorFor(RecordingHandler.class));
    }

    private static void setField(
            PacketManager manager,
            String name,
            Object value) throws Exception {
        Field field = PacketManager.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(manager, value);
    }

    @NoAuthMessage
    public static final class RecordingHandler extends MessageHandler {

        private static final List<RecordingHandler> invocations =
                new ArrayList<>();

        @Override
        public void handle() {
            invocations.add(this);
        }
    }
}
