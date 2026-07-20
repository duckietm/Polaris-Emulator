package com.eu.habbo.habbohotel.rooms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class RoomItemPlacementBehaviorTest {

    @Test
    void nullPlacementTileIsRejectedBeforeRightsChecks() {
        RoomItemManager manager = new RoomItemManager(mock(Room.class));

        assertEquals(
                FurnitureMovementError.INVALID_MOVE,
                manager.canPlaceFurnitureAt(mock(HabboItem.class), mock(Habbo.class), null, 0));
    }

    @Test
    void furnitureOutsideTheLayoutIsRejected() {
        Room room = mock(Room.class);
        RoomLayout layout = mock(RoomLayout.class);
        when(room.getLayout()).thenReturn(layout);
        RoomTile tile = new RoomTile();
        HabboItem item = mock(HabboItem.class);
        Item baseItem = mock(Item.class);
        when(item.getBaseItem()).thenReturn(baseItem);
        when(baseItem.getWidth()).thenReturn(1);
        when(baseItem.getLength()).thenReturn(1);
        when(layout.fitsOnMap(tile, 1, 1, 0)).thenReturn(false);

        assertEquals(FurnitureMovementError.INVALID_MOVE, new RoomItemManager(room).furnitureFitsAt(tile, item, 0));
    }

    @Test
    void wallPlacementWithoutRightsIsRejectedBeforePluginEvents() {
        Room room = mock(Room.class);
        Habbo owner = mock(Habbo.class);
        when(room.hasRights(owner)).thenReturn(false);
        when(room.getGuildRightLevel(owner)).thenReturn(RoomRightLevels.NONE);

        try (MockedStatic<BuildersClubRoomSupport> buildersClub = mockStatic(BuildersClubRoomSupport.class)) {
            buildersClub
                    .when(() -> BuildersClubRoomSupport.canPlaceInRoom(owner, room))
                    .thenReturn(false);

            assertEquals(
                    FurnitureMovementError.NO_RIGHTS,
                    new RoomItemManager(room).placeWallFurniAt(mock(HabboItem.class), ":w=1,2 l=3,4", owner));
        }
    }
}
