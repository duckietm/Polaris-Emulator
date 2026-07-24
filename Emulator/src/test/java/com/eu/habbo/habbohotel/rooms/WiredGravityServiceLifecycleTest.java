package com.eu.habbo.habbohotel.rooms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.FurnitureType;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.rooms.WiredMovementsComposer;
import io.netty.buffer.ByteBuf;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class WiredGravityServiceLifecycleTest {

    @Test
    void enablingCoalescesOneRoomTaskAndDisposeCancelsAndClearsSessionState() {
        Room room = room(71);
        FakeScheduler scheduler = new FakeScheduler();
        WiredGravityService service = service(room, scheduler, new AtomicLong(1_000));
        HabboItem item = floorItem(7001, 71, 5.0);

        assertTrue(service.setEnabled(item, true));
        assertTrue(service.setEnabled(item, true));
        assertEquals(1, scheduler.size());
        assertEquals(1, service.enabledCount());
        long generation = service.generation();

        service.dispose();

        assertEquals(0, service.enabledCount());
        assertFalse(service.hasPendingTask());
        assertTrue(service.generation() > generation);
        assertEquals(1, scheduler.cancelledCount);
    }

    @Test
    void itemLimitFailsClosedWithoutScheduling() {
        Room room = room(72);
        FakeScheduler scheduler = new FakeScheduler();
        WiredGravityService limited = new WiredGravityService(room, scheduler, System::currentTimeMillis, 1, 75, 50);
        assertTrue(limited.setEnabled(floorItem(7201, 72, 1.0), true));
        assertFalse(limited.setEnabled(floorItem(7202, 72, 1.0), true));
    }

    @Test
    void scheduledFallReResolvesTheItemAndUsesTheAuthoritativeRoomMoveOnce() {
        Room room = room(73);
        RoomLayout layout = mock(RoomLayout.class);
        RoomTile tile = new RoomTile((short) 2, (short) 3, (short) 0, RoomTileState.OPEN, true);
        HabboItem item = floorItem(7301, 73, 5.0);
        when(item.getX()).thenReturn((short) 2);
        when(item.getY()).thenReturn((short) 3);
        when(room.getLayout()).thenReturn(layout);
        when(room.getFloorItems()).thenReturn(Set.of(item));
        when(room.getHabboItem(7301)).thenReturn(item);
        when(room.getRoomUnits(tile)).thenReturn(Set.of());
        when(layout.getTile((short) 2, (short) 3)).thenReturn(tile);
        when(layout.getTilesAt(tile, 1, 1, 0)).thenReturn(Set.of(tile));
        when(layout.getHeightAtSquare((short) 2, (short) 3)).thenReturn((short) 0);
        doAnswer(invocation -> {
                    when(item.getZ()).thenReturn(invocation.getArgument(3));
                    return FurnitureMovementError.NONE;
                })
                .when(room)
                .moveFurniTo(eq(item), eq(tile), eq(0), anyDouble(), eq(null), eq(false), eq(false));
        FakeScheduler scheduler = new FakeScheduler();
        WiredGravityService service = service(room, scheduler, new AtomicLong(2_000));

        assertTrue(service.setEnabled(item, true));
        scheduler.runNext();

        verify(room).moveFurniTo(eq(item), eq(tile), eq(0), eq(0.0), eq(null), eq(false), eq(false));
        ArgumentCaptor<ServerMessage> packet = ArgumentCaptor.forClass(ServerMessage.class);
        verify(room).sendComposer(packet.capture());
        ServerMessage expected = new WiredMovementsComposer(
                        List.of(WiredMovementsComposer.furniMovement(7301, 2, 3, 2, 3, 5.0, 0.0, 0, 493)))
                .compose();
        assertEquals(hex(expected), hex(packet.getValue()));
        assertTrue(service.hasPendingTask(), "a stacked/recheck pass waits until the fall animation ends");
    }

    @Test
    void recycledItemIdIsRejectedBeforeCommit() {
        Room room = room(74);
        RoomLayout layout = mock(RoomLayout.class);
        RoomTile tile = new RoomTile((short) 4, (short) 4, (short) 0, RoomTileState.OPEN, true);
        HabboItem original = floorItem(7401, 74, 5.0);
        when(original.getX()).thenReturn((short) 4);
        when(original.getY()).thenReturn((short) 4);
        HabboItem replacement = floorItem(7401, 74, 5.0);
        when(replacement.getX()).thenReturn((short) 8);
        when(replacement.getY()).thenReturn((short) 8);
        when(room.getLayout()).thenReturn(layout);
        when(room.getFloorItems()).thenReturn(Set.of(original));
        when(room.getHabboItem(7401)).thenReturn(replacement);
        when(layout.getTile((short) 4, (short) 4)).thenReturn(tile);
        when(layout.getTilesAt(tile, 1, 1, 0)).thenReturn(Set.of(tile));
        when(layout.getHeightAtSquare((short) 4, (short) 4)).thenReturn((short) 0);
        FakeScheduler scheduler = new FakeScheduler();
        WiredGravityService service = service(room, scheduler, new AtomicLong(3_000));

        service.setEnabled(original, true);
        scheduler.runNext();

        verify(room, never()).moveFurniTo(any(), any(), anyInt(), anyDouble(), any(), anyBoolean(), anyBoolean());
        assertTrue(service.hasPendingTask());
    }

    @Test
    void animatedWiredMovementDefersGravityUntilItsDeadline() {
        Room room = room(75);
        AtomicLong clock = new AtomicLong(10_000);
        FakeScheduler scheduler = new FakeScheduler();
        WiredGravityService service = service(room, scheduler, clock);
        HabboItem item = floorItem(7501, 75, 5.0);
        RoomLayout layout = mock(RoomLayout.class);
        RoomTile tile = new RoomTile((short) 1, (short) 1, (short) 0, RoomTileState.OPEN, true);
        when(item.getX()).thenReturn((short) 1);
        when(item.getY()).thenReturn((short) 1);
        when(room.getLayout()).thenReturn(layout);
        when(room.getFloorItems()).thenReturn(Set.of(item));
        when(room.getHabboItem(7501)).thenReturn(item);
        when(layout.getTile((short) 1, (short) 1)).thenReturn(tile);
        when(layout.getTilesAt(tile, 1, 1, 0)).thenReturn(Set.of(tile));
        when(layout.getHeightAtSquare((short) 1, (short) 1)).thenReturn((short) 0);

        service.setEnabled(item, true);
        service.markMoving(item, 500);
        scheduler.runNext();

        verify(room, never()).moveFurniTo(any(), any(), anyInt(), anyDouble(), any(), anyBoolean(), anyBoolean());
        assertEquals(500, scheduler.peekDelay());
    }

    @Test
    void usersBotsAndPetsOnTheTopSurfaceDescendWithTheFurniture() {
        Room room = room(76);
        RoomLayout layout = mock(RoomLayout.class);
        RoomTile tile = new RoomTile((short) 6, (short) 7, (short) 0, RoomTileState.OPEN, true);
        HabboItem item = floorItem(7601, 76, 4.0);
        when(item.getX()).thenReturn((short) 6);
        when(item.getY()).thenReturn((short) 7);
        RoomUnit user = rider(1, RoomUnitType.USER, tile, 5.0);
        RoomUnit bot = rider(2, RoomUnitType.BOT, tile, 5.0);
        RoomUnit pet = rider(3, RoomUnitType.PET, tile, 5.0);
        when(room.getLayout()).thenReturn(layout);
        when(room.getFloorItems()).thenReturn(Set.of(item));
        when(room.getHabboItem(7601)).thenReturn(item);
        when(room.getRoomUnits(tile)).thenReturn(Set.of(user, bot, pet));
        when(room.getTopItemAt(6, 7)).thenReturn(item);
        when(layout.getTile((short) 6, (short) 7)).thenReturn(tile);
        when(layout.getTilesAt(tile, 1, 1, 0)).thenReturn(Set.of(tile));
        when(layout.getHeightAtSquare((short) 6, (short) 7)).thenReturn((short) 0);
        doAnswer(invocation -> {
                    when(item.getZ()).thenReturn(invocation.getArgument(3));
                    return FurnitureMovementError.NONE;
                })
                .when(room)
                .moveFurniTo(eq(item), eq(tile), eq(0), anyDouble(), eq(null), eq(false), eq(false));
        FakeScheduler scheduler = new FakeScheduler();
        WiredGravityService service = service(room, scheduler, new AtomicLong(4_000));

        service.setEnabled(item, true);
        scheduler.runNext();

        verify(user).setZ(1.0);
        verify(bot).setZ(1.0);
        verify(pet).setZ(1.0);
        verify(user).removeStatus(RoomUnitStatus.MOVE);
        verify(bot).removeStatus(RoomUnitStatus.MOVE);
        verify(pet).removeStatus(RoomUnitStatus.MOVE);
    }

    @Test
    void roomSchedulersAndDisposalAreIsolated() {
        FakeScheduler firstScheduler = new FakeScheduler();
        FakeScheduler secondScheduler = new FakeScheduler();
        WiredGravityService first = service(room(77), firstScheduler, new AtomicLong(1));
        WiredGravityService second = service(room(78), secondScheduler, new AtomicLong(1));

        first.setEnabled(floorItem(7701, 77, 1.0), true);
        second.setEnabled(floorItem(7801, 78, 1.0), true);
        first.dispose();

        assertEquals(1, firstScheduler.cancelledCount);
        assertEquals(0, secondScheduler.cancelledCount);
        assertTrue(second.hasPendingTask());
        assertEquals(1, second.enabledCount());
    }

    private static WiredGravityService service(Room room, FakeScheduler scheduler, AtomicLong clock) {
        return new WiredGravityService(room, scheduler, clock::get, 1000, 75, 50);
    }

    private static Room room(int id) {
        Room room = mock(Room.class);
        when(room.getId()).thenReturn(id);
        when(room.isLoaded()).thenReturn(true);
        return room;
    }

    private static HabboItem floorItem(int id, int roomId, double z) {
        HabboItem item = mock(HabboItem.class);
        Item baseItem = mock(Item.class);
        when(item.getId()).thenReturn(id);
        when(item.getRoomId()).thenReturn(roomId);
        when(item.getBaseItem()).thenReturn(baseItem);
        when(item.getZ()).thenReturn(z);
        when(item.getRotation()).thenReturn(0);
        when(baseItem.getType()).thenReturn(FurnitureType.FLOOR);
        when(baseItem.getWidth()).thenReturn(1);
        when(baseItem.getLength()).thenReturn(1);
        when(baseItem.getHeight()).thenReturn(1.0);
        return item;
    }

    private static RoomUnit rider(int id, RoomUnitType type, RoomTile tile, double z) {
        RoomUnit unit = mock(RoomUnit.class);
        when(unit.getId()).thenReturn(id);
        when(unit.getRoomUnitType()).thenReturn(type);
        when(unit.isInRoom()).thenReturn(true);
        when(unit.getCurrentLocation()).thenReturn(tile);
        when(unit.getZ()).thenReturn(z);
        when(unit.getBodyRotation()).thenReturn(RoomUserRotation.SOUTH);
        when(unit.getHeadRotation()).thenReturn(RoomUserRotation.SOUTH);
        return unit;
    }

    private static String hex(ServerMessage message) {
        ByteBuf packet = message.get();
        try {
            byte[] bytes = new byte[packet.readableBytes()];
            packet.getBytes(packet.readerIndex(), bytes);
            return HexFormat.of().formatHex(bytes);
        } finally {
            packet.release();
        }
    }

    private static final class FakeScheduler implements WiredGravityService.Scheduler {
        private final Deque<Entry> entries = new ArrayDeque<>();
        private int cancelledCount;

        @Override
        public WiredGravityService.Cancellable schedule(Runnable task, long delayMs) {
            Entry entry = new Entry(task, delayMs);
            entries.add(entry);
            return () -> {
                if (entry.cancelled) {
                    return false;
                }
                entry.cancelled = true;
                cancelledCount++;
                return true;
            };
        }

        int size() {
            return entries.size();
        }

        long peekDelay() {
            return entries.getFirst().delayMs;
        }

        void runNext() {
            Entry entry = entries.removeFirst();
            if (!entry.cancelled) {
                entry.task.run();
            }
        }

        private static final class Entry {
            private final Runnable task;
            private final long delayMs;
            private boolean cancelled;

            private Entry(Runnable task, long delayMs) {
                this.task = task;
                this.delayMs = delayMs;
            }
        }
    }
}
