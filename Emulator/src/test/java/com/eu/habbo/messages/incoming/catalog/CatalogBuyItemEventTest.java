package com.eu.habbo.messages.incoming.catalog;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eu.habbo.Emulator;
import com.eu.habbo.core.ConfigurationManager;
import com.eu.habbo.habbohotel.GameEnvironment;
import com.eu.habbo.habbohotel.catalog.CatalogManager;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInventory;
import com.eu.habbo.habbohotel.users.HabboStats;
import com.eu.habbo.habbohotel.users.inventory.ItemsComponent;
import com.eu.habbo.messages.ClientMessage;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.threading.runnables.ShutdownEmulator;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class CatalogBuyItemEventTest {

    @Test
    void unknownSearchResultKeepsLegacyPurchaseFailureResponse() throws Exception {
        GameClient client = mock(GameClient.class);
        Habbo habbo = mock(Habbo.class);
        HabboStats stats = mock(HabboStats.class);
        HabboInventory inventory = mock(HabboInventory.class);
        ItemsComponent items = mock(ItemsComponent.class);

        when(client.getHabbo()).thenReturn(habbo);
        when(habbo.getHabboStats()).thenReturn(stats);
        when(habbo.getInventory()).thenReturn(inventory);
        when(inventory.getItemsComponent()).thenReturn(items);
        when(items.itemCount()).thenReturn(0);

        int previousShutdownTimestamp = ShutdownEmulator.timestamp;
        ShutdownEmulator.timestamp = 0;
        try (MockedStatic<Emulator> emulator = mockStatic(Emulator.class)) {
            ConfigurationManager config = mock(ConfigurationManager.class);
            when(config.getBoolean(anyString())).thenReturn(false);
            emulator.when(Emulator::getConfig).thenReturn(config);

            GameEnvironment environment = mock(GameEnvironment.class);
            CatalogManager catalog = mock(CatalogManager.class);
            when(environment.getCatalogManager()).thenReturn(catalog);
            emulator.when(Emulator::getIntUnixTimestamp).thenReturn(10);
            emulator.when(Emulator::getGameEnvironment).thenReturn(environment);

            CatalogBuyItemEvent event = new CatalogBuyItemEvent();
            event.client = client;
            event.packet = packet(-1, 404, "", 1);
            event.handle();
        } finally {
            ShutdownEmulator.timestamp = previousShutdownTimestamp;
        }

        verify(client).sendResponse(any(ServerMessage.class));
    }

    private static ClientMessage packet(int pageId, int itemId, String extraData, int count) {
        byte[] extraDataBytes = extraData.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        ByteBuf buffer = Unpooled.buffer();
        buffer.writeInt(pageId);
        buffer.writeInt(itemId);
        buffer.writeShort(extraDataBytes.length);
        buffer.writeBytes(extraDataBytes);
        buffer.writeInt(count);
        return new ClientMessage(0, buffer);
    }
}
