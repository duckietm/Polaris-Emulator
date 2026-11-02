package com.eu.habbo.networking.gameserver.decoders;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.eu.habbo.Emulator;
import com.eu.habbo.core.ConfigurationManager;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.messages.ClientMessage;
import com.eu.habbo.networking.gameserver.GameServerAttributes;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/** A client flooding past the packet limits was only ever dropped; past the drop limit its connection is closed. */
class GameMessageRateLimitDisconnectTest {

    private static boolean floodStaysOpen(int disconnectAfter, int packets) {
        ConfigurationManager config = mock(ConfigurationManager.class);
        when(config.getInt(anyString(), anyInt())).thenAnswer(i -> i.getArgument(1));
        when(config.getInt("packet.rate.limit.disconnect_after", 200)).thenReturn(disconnectAfter);

        try (MockedStatic<Emulator> emulator = mockStatic(Emulator.class, RETURNS_DEEP_STUBS)) {
            emulator.when(Emulator::getConfig).thenReturn(config);
            emulator.when(Emulator::getIntUnixTimestamp).thenReturn(1_000);

            EmbeddedChannel channel = new EmbeddedChannel(new GameMessageRateLimit());
            GameClient client = new GameClient(channel);
            client.lastPacketCounterCleared = 1_000;
            channel.attr(GameServerAttributes.CLIENT).set(client);

            for (int i = 0; i < packets && channel.isOpen(); i++) {
                channel.writeInbound(new ClientMessage(1, Unpooled.buffer(0)));
            }
            boolean open = channel.isOpen();
            channel.finishAndReleaseAll();
            return open;
        }
    }

    @Test
    void aFloodPastTheDropLimitClosesTheConnection() {
        assertFalse(floodStaysOpen(20, 100));
    }

    @Test
    void dropsBelowTheLimitKeepTheConnection() {
        assertTrue(floodStaysOpen(200, 100));
    }

    @Test
    void zeroNeverCloses() {
        assertTrue(floodStaysOpen(0, 300));
    }
}
