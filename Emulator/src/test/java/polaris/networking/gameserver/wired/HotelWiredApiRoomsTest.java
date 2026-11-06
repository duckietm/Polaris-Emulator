package polaris.networking.gameserver.wired;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntPredicate;
import org.junit.jupiter.api.Test;
import polaris.hotel.items.FurnitureType;
import polaris.hotel.items.Item;
import polaris.hotel.items.interactions.wired.extra.WiredExtraFurniVariable;
import polaris.hotel.items.interactions.wired.extra.WiredExtraRoomVariable;
import polaris.hotel.items.interactions.wired.extra.WiredExtraUserVariable;
import polaris.hotel.items.interactions.wired.extra.WiredExtraVariableReference;
import polaris.hotel.rooms.BuildersClubRoomSupport;
import polaris.hotel.rooms.variables.RoomUserVariableStore;
import polaris.hotel.users.UserItem;
import polaris.networking.gameserver.wired.WiredApiRooms.Scope;
import polaris.networking.gameserver.wired.WiredApiRooms.TargetKind;

class HotelWiredApiRoomsTest {

    @Test
    void onlyPermanentPlainCustomVariablesAreExposed() {
        WiredExtraUserVariable permanent = mock(WiredExtraUserVariable.class);
        when(permanent.isPermanentAvailability()).thenReturn(true);
        WiredExtraUserVariable roomOnly = mock(WiredExtraUserVariable.class);
        WiredExtraUserVariable array = mock(WiredExtraUserVariable.class);
        when(array.isPermanentAvailability()).thenReturn(true);
        when(array.isArray()).thenReturn(true);
        WiredExtraFurniVariable furni = mock(WiredExtraFurniVariable.class);
        when(furni.isPermanentAvailability()).thenReturn(true);
        WiredExtraRoomVariable global = mock(WiredExtraRoomVariable.class);
        when(global.isPermanentAvailability()).thenReturn(true);

        assertTrue(HotelWiredApiRooms.isApiDefinition(permanent, Scope.USER));
        assertFalse(HotelWiredApiRooms.isApiDefinition(roomOnly, Scope.USER));
        assertFalse(HotelWiredApiRooms.isApiDefinition(array, Scope.USER));
        assertFalse(HotelWiredApiRooms.isApiDefinition(permanent, Scope.FURNI));
        assertTrue(HotelWiredApiRooms.isApiDefinition(furni, Scope.FURNI));
        assertTrue(HotelWiredApiRooms.isApiDefinition(global, Scope.GLOBAL));
        assertFalse(HotelWiredApiRooms.isApiDefinition(mock(WiredExtraVariableReference.class), Scope.USER));
        assertFalse(HotelWiredApiRooms.isApiDefinition(null, Scope.GLOBAL));
    }

    @Test
    void furniKindsSplitFloorWallAndBuildersClub() {
        UserItem floor = item(10, 2, FurnitureType.FLOOR);
        UserItem wall = item(11, 2, FurnitureType.WALL);
        UserItem bcFloor = item(12, BuildersClubRoomSupport.VIRTUAL_OWNER_ID, FurnitureType.FLOOR);
        UserItem bcWall = item(13, BuildersClubRoomSupport.VIRTUAL_OWNER_ID, FurnitureType.WALL);
        // An item of user 1 that is not tracked as Builders Club stays a normal item.
        UserItem ownedByUserOne = item(14, BuildersClubRoomSupport.VIRTUAL_OWNER_ID, FurnitureType.FLOOR);
        Set<Integer> tracked = Set.of(12, 13);
        AtomicInteger lookups = new AtomicInteger();
        IntPredicate trackedCheck = id -> {
            lookups.incrementAndGet();
            return tracked.contains(id);
        };

        assertTrue(HotelWiredApiRooms.isOfKind(floor, TargetKind.FURNI, trackedCheck));
        assertFalse(HotelWiredApiRooms.isOfKind(floor, TargetKind.FURNI_BC, trackedCheck));
        assertFalse(HotelWiredApiRooms.isOfKind(floor, TargetKind.WALL_ITEMS, trackedCheck));
        assertTrue(HotelWiredApiRooms.isOfKind(wall, TargetKind.WALL_ITEMS, trackedCheck));
        assertEquals(0, lookups.get());
        assertTrue(HotelWiredApiRooms.isOfKind(bcFloor, TargetKind.FURNI_BC, trackedCheck));
        assertFalse(HotelWiredApiRooms.isOfKind(bcFloor, TargetKind.FURNI, trackedCheck));
        assertTrue(HotelWiredApiRooms.isOfKind(bcWall, TargetKind.WALL_ITEMS_BC, trackedCheck));
        assertTrue(HotelWiredApiRooms.isOfKind(ownedByUserOne, TargetKind.FURNI, trackedCheck));
        assertFalse(HotelWiredApiRooms.isOfKind(ownedByUserOne, TargetKind.FURNI_BC, trackedCheck));
        assertFalse(HotelWiredApiRooms.isOfKind(null, TargetKind.FURNI, trackedCheck));
        assertFalse(HotelWiredApiRooms.isOfKind(floor, TargetKind.USERS, trackedCheck));
    }

    @Test
    void habboAndOlderKindNames() {
        assertEquals(TargetKind.FURNI, TargetKind.fromPath("furni"));
        assertEquals(TargetKind.FURNI, TargetKind.fromPath("floor"));
        assertEquals(TargetKind.WALL_ITEMS, TargetKind.fromPath("wall-items"));
        assertEquals(TargetKind.WALL_ITEMS, TargetKind.fromPath("wall"));
        assertEquals(TargetKind.FURNI_BC, TargetKind.fromPath("furni-bc"));
        assertEquals(TargetKind.WALL_ITEMS_BC, TargetKind.fromPath("wall-items-bc"));
        assertEquals("wall_item_bc", TargetKind.WALL_ITEMS_BC.profileKey());
        assertNull(TargetKind.fromPath("Furni"));
    }

    private static UserItem item(int id, int ownerId, FurnitureType type) {
        Item base = mock(Item.class);
        when(base.getType()).thenReturn(type);
        UserItem item = mock(UserItem.class);
        when(item.getId()).thenReturn(id);
        when(item.getUserId()).thenReturn(ownerId);
        when(item.getBaseItem()).thenReturn(base);
        return item;
    }

    @Test
    void apiNamesAreTheRoomsVariableNames() {
        assertTrue(HotelWiredApiRooms.NAME.matcher("Points_2").matches());
        assertFalse(HotelWiredApiRooms.NAME.matcher("@altitude").matches());
        assertFalse(HotelWiredApiRooms.NAME.matcher("").matches());
        assertFalse(HotelWiredApiRooms.NAME.matcher("a".repeat(41)).matches());
    }

    @Test
    void aSavedRowWriteOfAVariableUsersDoNotKeepIsNotParticipating() {
        assertTrue(HotelWiredApiRooms.HotelRoom.written(RoomUserVariableStore.Write.WRITTEN));
        assertFalse(HotelWiredApiRooms.HotelRoom.written(RoomUserVariableStore.Write.NOT_HELD));
        WiredApiException refused = assertThrows(
                WiredApiException.class,
                () -> HotelWiredApiRooms.HotelRoom.written(RoomUserVariableStore.Write.NOT_SAVED));
        assertEquals(403, refused.status());
        assertEquals(WiredApiException.USER_NOT_PARTICIPATING, refused.code());
    }
}
