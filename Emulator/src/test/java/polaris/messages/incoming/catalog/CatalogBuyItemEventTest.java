package polaris.messages.incoming.catalog;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import polaris.Emulator;
import polaris.core.ConfigurationManager;
import polaris.hotel.GameEnvironment;
import polaris.hotel.catalog.CatalogManager;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.users.User;
import polaris.hotel.users.UserInventory;
import polaris.hotel.users.UserStats;
import polaris.hotel.users.inventory.ItemsComponent;
import polaris.messages.ClientMessage;
import polaris.messages.ServerMessage;
import polaris.threading.runnables.ShutdownEmulator;

class CatalogBuyItemEventTest {

    @Test
    void unknownSearchResultKeepsLegacyPurchaseFailureResponse() throws Exception {
        GameClient client = mock(GameClient.class);
        User user = mock(User.class);
        UserStats stats = mock(UserStats.class);
        UserInventory inventory = mock(UserInventory.class);
        ItemsComponent items = mock(ItemsComponent.class);

        when(client.getUser()).thenReturn(user);
        when(user.getUserStats()).thenReturn(stats);
        when(user.getInventory()).thenReturn(inventory);
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
