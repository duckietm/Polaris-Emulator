package com.eu.habbo.habbohotel.rooms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectSendSignal;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import java.util.Set;
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

    @Test
    void wallPlacementAtTheWallCapIsRejectedBeforePluginEvents() {
        Room room = mock(Room.class);
        Habbo owner = mock(Habbo.class);
        when(room.hasRights(owner)).thenReturn(true);
        when(room.getWallItems()).thenReturn(Set.of(mock(HabboItem.class), mock(HabboItem.class)));

        try {
            RoomWallItemLimit.configure(2);

            assertEquals(
                    FurnitureMovementError.MAX_ITEMS,
                    new RoomItemManager(room).placeWallFurniAt(mock(HabboItem.class), ":w=1,2 l=3,4", owner));
        } finally {
            RoomWallItemLimit.configure(RoomWallItemLimit.DEFAULT_MAX_WALL_ITEMS);
        }
    }

    @Test
    void floorPlacementAtTheFloorCapIsRejectedBeforeAnythingElse() {
        Room room = mock(Room.class);
        when(room.getFloorItems()).thenReturn(Set.of(mock(HabboItem.class), mock(HabboItem.class)));

        try {
            RoomFloorItemLimit.configure(2);

            // Refused before the signal limits, the plugin events and the tile checks (none of them is set up here).
            assertEquals(
                    FurnitureMovementError.MAX_ITEMS,
                    new RoomItemManager(room).placeFloorFurniAt(mock(HabboItem.class), new RoomTile(), 0, null));
        } finally {
            RoomFloorItemLimit.configure(RoomFloorItemLimit.DEFAULT_MAX_FLOOR_ITEMS);
        }
    }

    @Test
    void aBoxThatArrivesPermanentOverThePermanentCapIsNotPlaced() {
        Room room = mock(Room.class);
        RoomSpecialTypes specialTypes = mock(RoomSpecialTypes.class);
        com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraUserVariable placed =
                mock(com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraUserVariable.class);
        com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraUserVariable inRoom =
                mock(com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraUserVariable.class);
        when(placed.isPermanentAvailability()).thenReturn(true);
        when(inRoom.isPermanentAvailability()).thenReturn(true);
        when(room.getFloorItems()).thenReturn(Set.of());
        when(room.getRoomSpecialTypes()).thenReturn(specialTypes);
        when(specialTypes.getExtras())
                .thenReturn(Set.of((com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra) inRoom));

        try {
            com.eu.habbo.habbohotel.wired.core.WiredRoomStats.configureLimits(23, 1, 70);

            // Refused before the plugin events and the tile checks (none of them is set up here); the box keeps its
            // settings where it came from.
            assertEquals(
                    FurnitureMovementError.MAX_PERMANENT_VARIABLES,
                    new RoomItemManager(room).placeFloorFurniAt(placed, new RoomTile(), 0, mock(Habbo.class)));
            assertEquals(
                    "${wiredfurni.error.variables.permanent_limit}",
                    FurnitureMovementError.MAX_PERMANENT_VARIABLES.errorCode);
        } finally {
            com.eu.habbo.habbohotel.wired.core.WiredRoomStats.configureLimits(
                    com.eu.habbo.habbohotel.wired.core.WiredRoomStats.DEFAULT_MAX_PERMANENT_FURNI_VARIABLES,
                    com.eu.habbo.habbohotel.wired.core.WiredRoomStats.DEFAULT_MAX_PERMANENT_USER_VARIABLES,
                    com.eu.habbo.habbohotel.wired.core.WiredRoomStats.DEFAULT_MAX_PERMANENT_GLOBAL_VARIABLES);
        }
    }

    @Test
    void aNewWiredBoxAtTheFloorCapIsRejectedWhoeverPlacesIt() {
        Room room = mock(Room.class);
        Habbo owner = mock(Habbo.class);
        when(room.getFloorItems()).thenReturn(Set.of(mock(HabboItem.class)));

        try {
            RoomFloorItemLimit.configure(1);

            assertEquals(
                    FurnitureMovementError.MAX_ITEMS,
                    new RoomItemManager(room)
                            .placeFloorFurniAt(mock(WiredEffectSendSignal.class), new RoomTile(), 0, owner));
            assertEquals("${room.error.max_furniture}", FurnitureMovementError.MAX_ITEMS.errorCode);
        } finally {
            RoomFloorItemLimit.configure(RoomFloorItemLimit.DEFAULT_MAX_FLOOR_ITEMS);
        }
    }

    @Test
    void theFloorCapIsHabbosAndTheMonitorShowsItBoundedByTheRoomFurniCap() {
        int roomCap = Room.MAXIMUM_FURNI;

        try {
            assertEquals(4000, RoomFloorItemLimit.DEFAULT_MAX_FLOOR_ITEMS);

            RoomFloorItemLimit.configure(4000);
            Room.MAXIMUM_FURNI = 2500;
            assertEquals(2500, RoomFloorItemLimit.effectiveMaxFloorItems());

            Room.MAXIMUM_FURNI = 8000;
            assertEquals(4000, RoomFloorItemLimit.effectiveMaxFloorItems());
            assertEquals(true, RoomFloorItemLimit.hasRoomFor(3999));
            assertEquals(false, RoomFloorItemLimit.hasRoomFor(4000));

            RoomFloorItemLimit.configure(-3);
            assertEquals(0, RoomFloorItemLimit.maxFloorItems());
        } finally {
            Room.MAXIMUM_FURNI = roomCap;
            RoomFloorItemLimit.configure(RoomFloorItemLimit.DEFAULT_MAX_FLOOR_ITEMS);
        }
    }
}
