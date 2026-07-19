package com.eu.habbo.habbohotel.gameclients;

import com.eu.habbo.Emulator;
import com.eu.habbo.core.CryptoConfig;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.plugin.PluginManager;
import com.eu.habbo.plugin.events.emulator.OutgoingPacketEvent;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
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
        this.channel = new EmbeddedChannel();
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

    private static Field field(String name) throws Exception {
        Field field = Emulator.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}
