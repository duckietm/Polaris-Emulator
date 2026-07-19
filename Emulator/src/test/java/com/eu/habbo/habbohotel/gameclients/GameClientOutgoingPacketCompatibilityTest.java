package com.eu.habbo.habbohotel.gameclients;

import com.eu.habbo.Emulator;
import com.eu.habbo.core.CryptoConfig;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.ServerMessageFrame;
import com.eu.habbo.plugin.PluginManager;
import com.eu.habbo.plugin.events.emulator.OutgoingPacketEvent;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class GameClientOutgoingPacketCompatibilityTest {

    private Field runtimeField;
    private Field cryptoField;
    private Field pluginManagerField;
    private Object originalRuntime;
    private Object originalCrypto;
    private Object originalPluginManager;
    private PluginManager pluginManager;
    private FlushCountingHandler flushCounter;
    private EmbeddedChannel channel;
    private GameClient client;

    @BeforeEach
    void setUp() throws Exception {
        this.runtimeField = field("polarisRuntime");
        this.cryptoField = field("crypto");
        this.pluginManagerField = field("pluginManager");
        this.originalRuntime = this.runtimeField.get(null);
        this.originalCrypto = this.cryptoField.get(null);
        this.originalPluginManager = this.pluginManagerField.get(null);
        this.runtimeField.set(null, null);
        this.cryptoField.set(
                null,
                new CryptoConfig(false, "", "", ""));
        this.pluginManager = mock(PluginManager.class);
        this.pluginManagerField.set(null, this.pluginManager);
        this.flushCounter = new FlushCountingHandler();
        this.channel = new EmbeddedChannel(
                this.flushCounter);
        this.client = new GameClient(this.channel);
    }

    @AfterEach
    void tearDown() throws Exception {
        this.channel.finishAndReleaseAll();
        this.pluginManagerField.set(null, this.originalPluginManager);
        this.cryptoField.set(null, this.originalCrypto);
        this.runtimeField.set(null, this.originalRuntime);
    }

    @Test
    void singleResponsePublishesTheExactPacketEventBeforeWriting() {
        org.mockito.Mockito.when(this.pluginManager.isRegistered(
                OutgoingPacketEvent.class,
                false)).thenReturn(true);
        ServerMessage response = new ServerMessage(100);

        this.client.sendResponse(response);

        ArgumentCaptor<OutgoingPacketEvent> event =
                ArgumentCaptor.forClass(OutgoingPacketEvent.class);
        verify(this.pluginManager).fireEvent(event.capture());
        assertSame(response, event.getValue().getOriginalMessage());
        assertSame(response, this.channel.readOutbound());
    }

    @Test
    void cancellationSuppressesTheResponse() {
        org.mockito.Mockito.when(this.pluginManager.isRegistered(
                OutgoingPacketEvent.class,
                false)).thenReturn(true);
        doAnswer(invocation -> {
            OutgoingPacketEvent event = invocation.getArgument(0);
            event.setCancelled(true);
            return event;
        }).when(this.pluginManager).fireEvent(
                any(OutgoingPacketEvent.class));

        this.client.sendResponse(new ServerMessage(100));

        assertNull(this.channel.readOutbound());
    }

    @Test
    void batchResponsesApplyReplacementAndCancellationPerPacket() {
        org.mockito.Mockito.when(this.pluginManager.isRegistered(
                OutgoingPacketEvent.class,
                false)).thenReturn(true);
        ServerMessage first = new ServerMessage(100);
        ServerMessage second = new ServerMessage(101);
        ServerMessage replacement = new ServerMessage(102);
        doAnswer(invocation -> {
            OutgoingPacketEvent event = invocation.getArgument(0);
            if (event.getOriginalMessage() == first) {
                event.setCustomMessage(replacement);
            } else {
                event.setCancelled(true);
            }
            return event;
        }).when(this.pluginManager).fireEvent(
                any(OutgoingPacketEvent.class));

        this.client.sendResponses(new ArrayList<>(
                java.util.List.of(first, second)));

        verify(this.pluginManager, times(2)).fireEvent(
                any(OutgoingPacketEvent.class));
        assertSame(replacement, this.channel.readOutbound());
        assertNull(this.channel.readOutbound());
    }

    @Test
    void noSubscriberSkipsEventDispatchAndWritesTheResponse() {
        ServerMessage response = new ServerMessage(100);

        this.client.sendResponse(response);

        verify(this.pluginManager).isRegistered(
                OutgoingPacketEvent.class,
                false);
        verify(this.pluginManager, never()).fireEvent(
                any(OutgoingPacketEvent.class));
        assertSame(response, this.channel.readOutbound());
    }

    @Test
    void noSubscriberBatchChecksRegistrationOnceAndWritesEveryResponse() {
        ServerMessage first = new ServerMessage(100);
        ServerMessage second = new ServerMessage(101);

        this.client.sendResponses(new ArrayList<>(
                java.util.List.of(first, second)));

        verify(this.pluginManager).isRegistered(
                OutgoingPacketEvent.class,
                false);
        verify(this.pluginManager, never()).fireEvent(
                any(OutgoingPacketEvent.class));
        assertSame(first, this.channel.readOutbound());
        assertSame(second, this.channel.readOutbound());
        assertNull(this.channel.readOutbound());
    }

    @Test
    void singleResponseFlushesImmediately() {
        this.client.sendResponse(
                new ServerMessage(100));

        assertEquals(1, this.flushCounter.flushes);
    }

    @Test
    void responseBatchUsesOneFlush() {
        this.client.sendResponses(new ArrayList<>(
                java.util.List.of(
                        new ServerMessage(100),
                        new ServerMessage(101))));

        assertEquals(1, this.flushCounter.flushes);
    }

    @Test
    void flushBatchWritesAndPublishesEventsBeforeOneClosingFlush() {
        org.mockito.Mockito.when(this.pluginManager.isRegistered(
                OutgoingPacketEvent.class,
                false)).thenReturn(true);
        ServerMessage first = new ServerMessage(100);
        ServerMessage second = new ServerMessage(101);

        try (GameClientFlushBatch ignored =
                     GameClientFlushBatch.open()) {
            this.client.sendResponse(first);
            this.client.sendResponse(second);

            assertEquals(0, this.flushCounter.flushes);
            verify(this.pluginManager, times(2)).fireEvent(
                    any(OutgoingPacketEvent.class));
        }

        assertEquals(1, this.flushCounter.flushes);
        assertSame(first, this.channel.readOutbound());
        assertSame(second, this.channel.readOutbound());
    }

    @Test
    void flushBatchClosesAndFlushesWhenSendingFails() {
        assertThrows(
                IllegalStateException.class,
                () -> {
                    try (GameClientFlushBatch ignored =
                                 GameClientFlushBatch.open()) {
                        this.client.sendResponse(
                                new ServerMessage(100));
                        throw new IllegalStateException(
                                "expected");
                    }
                });

        assertEquals(1, this.flushCounter.flushes);
    }

    @Test
    void preparedBroadcastWritesTheSharedFrameWithoutSubscribers() {
        ServerMessage response =
                new ServerMessage(100);
        response.appendInt(77);
        ServerMessageFrame.prepareBroadcast(response);
        ByteBuf expected = response.get();

        this.client.sendResponse(response);

        ByteBuf actual = this.channel.readOutbound();
        try {
            assertEquals(expected, actual);
        } finally {
            expected.release();
            actual.release();
        }
    }

    @Test
    void preparedBroadcastStillUsesPacketEventsWhenRegistered() {
        org.mockito.Mockito.when(this.pluginManager.isRegistered(
                OutgoingPacketEvent.class,
                false)).thenReturn(true);
        ServerMessage response =
                new ServerMessage(100);
        ServerMessageFrame.prepareBroadcast(response);

        this.client.sendResponse(response);

        verify(this.pluginManager).fireEvent(
                any(OutgoingPacketEvent.class));
        assertSame(response, this.channel.readOutbound());
    }

    @Test
    void preparedBroadcastBatchWritesEachSharedFrame() {
        ServerMessage first =
                new ServerMessage(100);
        first.appendInt(1);
        ServerMessage second =
                new ServerMessage(101);
        second.appendInt(2);
        ServerMessageFrame.prepareBroadcast(first);
        ServerMessageFrame.prepareBroadcast(second);
        ByteBuf expectedFirst = first.get();
        ByteBuf expectedSecond = second.get();

        this.client.sendResponses(new ArrayList<>(
                java.util.List.of(first, second)));

        ByteBuf actualFirst = this.channel.readOutbound();
        ByteBuf actualSecond = this.channel.readOutbound();
        try {
            assertEquals(expectedFirst, actualFirst);
            assertEquals(expectedSecond, actualSecond);
        } finally {
            expectedFirst.release();
            expectedSecond.release();
            actualFirst.release();
            actualSecond.release();
        }
    }

    private static Field field(String name) throws Exception {
        Field field = Emulator.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static final class FlushCountingHandler
            extends ChannelOutboundHandlerAdapter {
        private int flushes;

        @Override
        public void flush(ChannelHandlerContext context)
                throws Exception {
            this.flushes++;
            context.flush();
        }
    }
}
