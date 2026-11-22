package polaris.messages.incoming.catalog.marketplace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import polaris.hotel.catalog.marketplace.MarketPlace;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.users.User;
import polaris.hotel.users.UserStats;
import polaris.messages.ClientMessage;
import polaris.messages.incoming.Incoming;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.catalog.marketplace.MarketplaceItemPostedComposer;

/** A trade-locked user cannot move furni or credits through the marketplace either. */
class MarketplaceTradeLockTest {
    @Test
    void sellingIsRefusedBeforeThePacketIsRead() throws Exception {
        UserStats stats = mock(UserStats.class);
        when(stats.isTradeLocked()).thenReturn(true);
        User user = mock(User.class);
        when(user.getUserStats()).thenReturn(stats);
        GameClient client = mock(GameClient.class);
        when(client.getUser()).thenReturn(user);

        boolean enabled = MarketPlace.MARKETPLACE_ENABLED;
        MarketPlace.MARKETPLACE_ENABLED = true;
        try {
            SellItemEvent handler = new SellItemEvent();
            handler.client = client;
            handler.packet = new ClientMessage(Incoming.SellItemEvent, Unpooled.buffer());
            handler.handle();
        } finally {
            MarketPlace.MARKETPLACE_ENABLED = enabled;
        }

        ArgumentCaptor<MessageComposer> sent = ArgumentCaptor.forClass(MessageComposer.class);
        verify(client).sendResponse(sent.capture());
        assertEquals(MarketplaceItemPostedComposer.class, sent.getValue().getClass());
        verify(user, org.mockito.Mockito.never()).getInventory();
    }
}
