package polaris.hotel.wired.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import polaris.hotel.items.Item;
import polaris.hotel.pets.RideablePet;
import polaris.hotel.rooms.FurnitureMovementError;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomLayout;
import polaris.hotel.rooms.RoomTile;
import polaris.hotel.rooms.RoomTileState;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.rooms.RoomUnitStatus;
import polaris.hotel.rooms.RoomUnitType;
import polaris.hotel.rooms.RoomUserRotation;
import polaris.hotel.rooms.RoomWiredRuntime;
import polaris.hotel.users.User;
import polaris.hotel.users.UserInfo;
import polaris.hotel.users.UserItem;
import polaris.messages.ServerMessage;

/**
 * A stack without a movement extra that raises a furni on its own tile lifts everyone the furni holds up in the same
 * movements packet: the furni, then a standing user, a walker whose step ends on it (a walk entry from the tile he
 * left), and a rider followed by his horse, one unit lower.
 */
class WiredLiftMovementTest {
    private static final int USER_ENTRY = 0;
    private static final int FURNI_ENTRY = 1;

    private record Entry(int type, int fromX, int toX, String fromZ, String toZ, int id, int movementType) {}

    @Test
    void theFurniLiftsEveryoneItHoldsUpInItsOwnMovement() {
        Room room = mock(Room.class);
        RoomLayout layout = mock(RoomLayout.class);
        RoomTile furniTile = new RoomTile((short) 4, (short) 8, (short) 0, RoomTileState.OPEN, true);
        RoomTile leftTile = new RoomTile((short) 3, (short) 8, (short) 0, RoomTileState.OPEN, true);
        AtomicReference<Double> furniZ = new AtomicReference<>(1.0);

        Item base = mock(Item.class);
        when(base.getWidth()).thenReturn(1);
        when(base.getLength()).thenReturn(1);
        when(base.getHeight()).thenReturn(1.0);
        UserItem furni = mock(UserItem.class);
        when(furni.getId()).thenReturn(50);
        when(furni.getBaseItem()).thenReturn(base);
        when(furni.getX()).thenReturn((short) 4);
        when(furni.getY()).thenReturn((short) 8);
        when(furni.getZ()).thenAnswer(invocation -> furniZ.get());

        Set<RoomUnit> units = new LinkedHashSet<>();
        when(room.getLayout()).thenReturn(layout);
        when(room.getRoomUnits()).thenReturn(units);
        when(room.getTopItemAt(anyInt(), anyInt())).thenReturn(furni);
        when(room.getWiredRuntime()).thenReturn(mock(RoomWiredRuntime.class));
        when(layout.getTile((short) 4, (short) 8)).thenReturn(furniTile);
        when(layout.getTilesAt(furniTile, 1, 1, 0)).thenReturn(Set.of(furniTile));
        when(room.moveFurniTo(eq(furni), eq(furniTile), anyInt(), anyDouble(), any(), anyBoolean(), anyBoolean()))
                .thenAnswer(invocation -> {
                    furniZ.set(invocation.getArgument(3));
                    return FurnitureMovementError.NONE;
                });

        RoomUnit standing = unit(units, 1, RoomUnitType.USER, furniTile, 2.0);
        RoomUnit walker = unit(units, 2, RoomUnitType.USER, furniTile, 2.0);
        ConcurrentHashMap<RoomUnitStatus, String> walkerStatus = new ConcurrentHashMap<>();
        walkerStatus.put(RoomUnitStatus.MOVE, "4,8,2.0");
        when(walker.hasStatus(RoomUnitStatus.MOVE)).thenReturn(true);
        when(walker.isWalking()).thenReturn(true);
        when(walker.getPreviousLocation()).thenReturn(leftTile);
        when(walker.getStatusMap()).thenReturn(walkerStatus);
        RoomUnit horse = unit(units, 7, RoomUnitType.PET, furniTile, 2.0);
        RoomUnit rider = unit(units, 3, RoomUnitType.USER, furniTile, 3.0);
        rides(room, rider, horse);
        // A user next to the furni stays out of it.
        unit(units, 9, RoomUnitType.USER, leftTile, 0.0);

        WiredMoveCarryHelper.beginMovementCollection();
        FurnitureMovementError result;
        ServerMessage message;
        try {
            result = WiredMoveCarryHelper.moveFurni(room, null, furni, furniTile, 0, 3.0, null, false, null);
        } finally {
            message = WiredMoveCarryHelper.finishMovementCollection();
        }

        assertEquals(FurnitureMovementError.NONE, result);

        List<Entry> entries = read(message);

        assertEquals(
                List.of(
                        new Entry(FURNI_ENTRY, 4, 4, "1.0", "3.0", 50, -1),
                        new Entry(USER_ENTRY, 4, 4, "2.0", "4.0", 1, 1),
                        // The walker keeps walking: from the tile he left, at its height, to his step's tile.
                        new Entry(USER_ENTRY, 3, 4, "0.0", "4.0", 2, 0),
                        new Entry(USER_ENTRY, 4, 4, "3.0", "5.0", 3, 1),
                        new Entry(USER_ENTRY, 4, 4, "2.0", "4.0", 7, 1)),
                entries);

        verify(standing).setZ(4.0);
        verify(rider).setZ(5.0);
        verify(horse).setZ(4.0);
        verify(walker).setZ(4.0);
        // The walker stays in his step: only its height changes.
        assertEquals("4,8,4.0", walkerStatus.get(RoomUnitStatus.MOVE));
        verify(walker, never()).setLocation(any());

        WiredMoveCarryHelper.clearStatusComposerSuppression(standing);
        WiredMoveCarryHelper.clearStatusComposerSuppression(rider);
        WiredMoveCarryHelper.clearStatusComposerSuppression(horse);
    }

    private static RoomUnit unit(Set<RoomUnit> units, int id, RoomUnitType type, RoomTile tile, double z) {
        RoomUnit unit = mock(RoomUnit.class);
        when(unit.getId()).thenReturn(id);
        when(unit.getRoomUnitType()).thenReturn(type);
        when(unit.getCurrentLocation()).thenReturn(tile);
        when(unit.isInRoom()).thenReturn(true);
        when(unit.getX()).thenReturn(tile.x);
        when(unit.getY()).thenReturn(tile.y);
        when(unit.getZ()).thenReturn(z);
        when(unit.getBodyRotation()).thenReturn(RoomUserRotation.NORTH);
        when(unit.getHeadRotation()).thenReturn(RoomUserRotation.NORTH);
        units.add(unit);
        return unit;
    }

    private static void rides(Room room, RoomUnit rider, RoomUnit horse) {
        User user = mock(User.class);
        UserInfo info = mock(UserInfo.class);
        RideablePet pet = mock(RideablePet.class);
        when(pet.getRoomUnit()).thenReturn(horse);
        when(info.getRiding()).thenReturn(pet);
        when(user.getUserInfo()).thenReturn(info);
        when(room.getUser(rider)).thenReturn(user);
    }

    private static List<Entry> read(ServerMessage message) {
        ByteBuf packet = message.get();
        packet.skipBytes(6);
        int count = packet.readInt();
        List<Entry> entries = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            int type = packet.readInt();
            int fromX = packet.readInt();
            packet.readInt();
            int toX = packet.readInt();
            packet.readInt();
            String fromZ = string(packet);
            String toZ = string(packet);
            int id = packet.readInt();

            if (type == USER_ENTRY) {
                int movementType = packet.readInt();
                packet.skipBytes(12);
                entries.add(new Entry(type, fromX, toX, fromZ, toZ, id, movementType));
            } else {
                packet.skipBytes(20);
                entries.add(new Entry(type, fromX, toX, fromZ, toZ, id, -1));
            }
        }

        return entries;
    }

    private static String string(ByteBuf packet) {
        byte[] bytes = new byte[packet.readUnsignedShort()];
        packet.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
