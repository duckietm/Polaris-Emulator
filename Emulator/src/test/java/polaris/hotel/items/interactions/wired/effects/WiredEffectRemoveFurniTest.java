package polaris.hotel.items.interactions.wired.effects;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import polaris.Emulator;
import polaris.hotel.GameEnvironment;
import polaris.hotel.items.Item;
import polaris.hotel.items.ItemManager;
import polaris.hotel.rooms.BuildersClubRoomSupport;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.UserItem;
import polaris.hotel.users.UserManager;
import polaris.hotel.wired.core.WiredContext;
import polaris.hotel.wired.core.WiredEvent;
import polaris.hotel.wired.core.WiredServices;
import polaris.hotel.wired.core.WiredState;

/**
 * Habbo's remove furni never destroys anything. Even the old DELETE mode hands every furni back to
 * whoever owns it, so a wired builder with modify rights cannot wipe the room owner's furni for good.
 */
class WiredEffectRemoveFurniTest {

    @Test
    void deleteModeReturnsFurniToTheirOwnersInsteadOfDestroyingThem() throws Exception {
        Room room = mock(Room.class);
        when(room.getId()).thenReturn(1);
        when(room.getOwnerId()).thenReturn(10);
        UserItem owners = furni(room, 301, 10);
        UserItem guests = furni(room, 302, 20);

        WiredEffectRemoveFurni box = new WiredEffectRemoveFurni(1, 10, mock(Item.class), "", 0, 0);
        ResultSet set = mock(ResultSet.class);
        when(set.getString("wired_data"))
                .thenReturn("{\"items\":[301,302],\"mode\":1,\"furniSource\":100,\"delay\":0}");
        box.loadWiredData(set, room);

        ItemManager items = mock(ItemManager.class);
        GameEnvironment environment = mock(GameEnvironment.class);
        when(environment.getItemManager()).thenReturn(items);
        when(environment.getUserManager()).thenReturn(mock(UserManager.class));

        try (MockedStatic<Emulator> emulator = mockStatic(Emulator.class);
                MockedStatic<BuildersClubRoomSupport> buildersClub = mockStatic(BuildersClubRoomSupport.class)) {
            emulator.when(Emulator::getGameEnvironment).thenReturn(environment);
            buildersClub
                    .when(() -> BuildersClubRoomSupport.isTrackedItem(anyInt()))
                    .thenReturn(false);

            box.execute(context(room));
        }

        verify(room).pickUpItem(owners, null);
        verify(room).pickUpItem(guests, null);
        verify(items, never()).deleteItem(any());
    }

    private static UserItem furni(Room room, int id, int userId) {
        UserItem item = mock(UserItem.class);
        when(item.getId()).thenReturn(id);
        when(item.getUserId()).thenReturn(userId);
        when(item.getRoomId()).thenReturn(1);
        when(room.getUserItem(id)).thenReturn(item);
        return item;
    }

    private static WiredContext context(Room room) {
        return new WiredContext(
                WiredEvent.builder(WiredEvent.Type.CUSTOM, room).build(),
                null,
                mock(WiredServices.class),
                new WiredState(20));
    }
}
