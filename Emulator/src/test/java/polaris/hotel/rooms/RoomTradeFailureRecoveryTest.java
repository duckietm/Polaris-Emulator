package polaris.hotel.rooms;

import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import polaris.hotel.users.User;
import polaris.hotel.users.UserItem;

/** An exception while exchanging the items ends the trade and hands the offers back. */
class RoomTradeFailureRecoveryTest {

    @Test
    void anExceptionBeforeTheCommitReturnsTheItemsAndStopsTheTrade() {
        User one = mock(User.class, RETURNS_DEEP_STUBS);
        User two = mock(User.class, RETURNS_DEEP_STUBS);
        Room room = mock(Room.class);
        RoomTrade trade = spy(new RoomTrade(one, two, room));
        trade.getRoomTradeUsers().get(0).addItem(mock(UserItem.class));
        doThrow(new IllegalStateException("boom")).when(trade).tradeItems();

        trade.finishTrade();

        verify(one.getInventory().getItemsComponent()).addItems(anyCollection());
        verify(two.getInventory().getItemsComponent()).addItems(anyCollection());
        verify(room).stopTrade(trade);
    }
}
