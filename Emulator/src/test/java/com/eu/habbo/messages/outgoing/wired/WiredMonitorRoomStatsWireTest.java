package com.eu.habbo.messages.outgoing.wired;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.GameEnvironment;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraFurniVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraRoomVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraUserVariable;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomFloorItemLimit;
import com.eu.habbo.habbohotel.rooms.RoomManager;
import com.eu.habbo.habbohotel.rooms.RoomSpecialTypes;
import com.eu.habbo.habbohotel.rooms.RoomWallItemLimit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredRoomDiagnostics;
import com.eu.habbo.habbohotel.wired.core.WiredRoomStats;
import com.eu.habbo.messages.incoming.Incoming;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;
import io.netty.buffer.ByteBuf;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/** AIR's own room stats packet (1957 asks, 1891 answers), the wall furni cap and the permanent variable caps. */
class WiredMonitorRoomStatsWireTest {

    @AfterEach
    void restoreLimits() {
        WiredRoomStats.configureLimits(
                WiredRoomStats.DEFAULT_MAX_PERMANENT_FURNI_VARIABLES,
                WiredRoomStats.DEFAULT_MAX_PERMANENT_USER_VARIABLES,
                WiredRoomStats.DEFAULT_MAX_PERMANENT_GLOBAL_VARIABLES);
        RoomWallItemLimit.configure(RoomWallItemLimit.DEFAULT_MAX_WALL_ITEMS);
        RoomFloorItemLimit.configure(RoomFloorItemLimit.DEFAULT_MAX_FLOOR_ITEMS);
    }

    @Test
    void theHeadersAreAirs() {
        assertEquals(1957, Incoming.WiredRoomStatsRequestEvent);
        assertEquals(1891, Outgoing.WiredRoomStatsComposer);
    }

    @Test
    void onlyPermanentVariableBoxesAreCountedAgainstTheirLimits() {
        WiredRoomStats stats = WiredRoomStats.from(
                12,
                3,
                2500,
                List.of(
                        furni(true),
                        furni(false),
                        user(true),
                        user(true),
                        global(true),
                        mock(InteractionWiredExtra.class)));

        assertEquals(12, stats.getFloorCount());
        assertEquals(2500, stats.getFloorLimit());
        assertEquals(3, stats.getWallCount());
        assertEquals(2500, stats.getWallLimit());
        assertEquals(1, stats.getPermanentFurniVariables());
        assertEquals(23, stats.getPermanentFurniVariablesLimit());
        assertEquals(2, stats.getPermanentUserVariables());
        assertEquals(93, stats.getPermanentUserVariablesLimit());
        assertEquals(1, stats.getPermanentGlobalVariables());
        assertEquals(70, stats.getPermanentGlobalVariablesLimit());
    }

    @Test
    void floorAndWallFurniHaveTheirOwnLimits() {
        WiredRoomStats stats = WiredRoomStats.from(40, 2500, 5, 4000, List.of());

        assertEquals(2500, stats.getFloorLimit());
        assertEquals(4000, stats.getWallLimit());
    }

    @Test
    void theFloorLineOfARoomUsesTheFloorCapBoundedByTheRoomFurniCap() {
        Room room = room(9, Set.of());
        when(room.getFloorItems()).thenReturn(Set.of(mock(HabboItem.class), mock(HabboItem.class)));
        when(room.getWallItems()).thenReturn(Set.of(mock(HabboItem.class)));
        int roomCap = Room.MAXIMUM_FURNI;

        try {
            Room.MAXIMUM_FURNI = 10000;
            WiredRoomStats stats = WiredRoomStats.of(room);

            assertEquals(2, stats.getFloorCount());
            assertEquals(4000, stats.getFloorLimit());
            assertEquals(1, stats.getWallCount());
            assertEquals(4000, stats.getWallLimit());

            RoomFloorItemLimit.configure(3000);
            assertEquals(3000, WiredRoomStats.of(room).getFloorLimit());

            Room.MAXIMUM_FURNI = 2500;
            assertEquals(2500, WiredRoomStats.of(room).getFloorLimit(), "the room furni cap still refuses first");
        } finally {
            Room.MAXIMUM_FURNI = roomCap;
        }
    }

    @Test
    void theConfiguredLimitsAreUsedAndNegativeOnesCountAsZero() {
        WiredRoomStats.configureLimits(12, -1, 37);

        WiredRoomStats stats = WiredRoomStats.from(0, 0, 4000, List.of());

        assertEquals(12, stats.getPermanentFurniVariablesLimit());
        assertEquals(0, stats.getPermanentUserVariablesLimit());
        assertEquals(37, stats.getPermanentGlobalVariablesLimit());
    }

    @Test
    void aNewPermanentVariableNeedsAFreeSlotOfItsKind() {
        WiredRoomStats.configureLimits(2, 1, 1);
        InteractionWiredExtra newFurni = furni(false);
        List<InteractionWiredExtra> extras = new ArrayList<>(List.of(furni(true), newFurni, user(true), global(false)));

        assertTrue(WiredRoomStats.canBecomePermanent(extras, newFurni), "one furni slot left");

        extras.add(furni(true));

        assertFalse(WiredRoomStats.canBecomePermanent(extras, newFurni), "furni cap reached");
        assertFalse(WiredRoomStats.canBecomePermanent(extras, user(false)), "user cap reached");
        assertTrue(WiredRoomStats.canBecomePermanent(extras, global(false)), "the global cap is apart");
    }

    @Test
    void aBoxThatAlreadyIsPermanentKeepsItsSlotOverTheCap() {
        WiredRoomStats.configureLimits(1, 1, 1);
        InteractionWiredExtra kept = user(true);
        List<InteractionWiredExtra> extras = List.of(kept, user(true), user(true));

        assertTrue(WiredRoomStats.canBecomePermanent(extras, kept));
        assertTrue(WiredRoomStats.canBecomePermanent(extras, mock(InteractionWiredExtra.class)));
    }

    @Test
    void aBoxPlacedAlreadyPermanentNeedsAFreeSlotOfItsKind() {
        WiredRoomStats.configureLimits(1, 2, 1);
        InteractionWiredExtra arriving = user(true);
        List<InteractionWiredExtra> extras = new ArrayList<>(List.of(user(true), furni(true)));

        assertTrue(WiredRoomStats.hasPermanentSlotFor(extras, arriving), "one user slot left");

        extras.add(user(true));

        assertFalse(WiredRoomStats.hasPermanentSlotFor(extras, arriving), "user cap reached");
        assertTrue(
                WiredRoomStats.canBecomePermanent(extras, arriving),
                "saving keeps a permanent box's slot, placing does not give one");
        assertTrue(WiredRoomStats.hasPermanentSlotFor(extras, user(false)), "a temporary box always fits");
        assertTrue(WiredRoomStats.hasPermanentSlotFor(extras, global(true)), "the global cap is apart");
        assertFalse(WiredRoomStats.hasPermanentSlotFor(extras, furni(true)), "furni cap reached");
        assertTrue(WiredRoomStats.hasPermanentSlotFor(extras, mock(InteractionWiredExtra.class)));
    }

    @Test
    void savingANewPermanentUserVariableOverTheCapIsRefusedWithHabbosKey() {
        WiredRoomStats.configureLimits(23, 1, 70);
        WiredExtraUserVariable box = new WiredExtraUserVariable(501, 1, null, "", 0, 0);
        box.setRoomId(9);
        Set<InteractionWiredExtra> extras = new LinkedHashSet<>(List.of(user(true), box));
        Room room = room(9, extras);
        RoomManager roomManager = mock(RoomManager.class);
        when(roomManager.getRoom(9)).thenReturn(room);
        GameEnvironment environment = mock(GameEnvironment.class);
        when(environment.getRoomManager()).thenReturn(roomManager);

        try (MockedStatic<Emulator> emulator = mockStatic(Emulator.class)) {
            emulator.when(Emulator::getGameEnvironment).thenReturn(environment);

            WiredSaveException refused = assertThrows(
                    WiredSaveException.class,
                    () -> box.saveData(
                            new WiredSettings(
                                    new int[] {WiredExtraUserVariable.AVAILABILITY_SHARED, 0}, "points", new int[0], 0),
                            null));

            assertEquals("wiredfurni.error.variables.permanent_limit", refused.getMessage());
            assertFalse(box.isPermanentAvailability());
        }
    }

    @Test
    void theWallCapRefusesTheItemPastItAndCountsNegativeAsZero() {
        RoomWallItemLimit.configure(3);

        assertTrue(RoomWallItemLimit.hasRoomFor(2));
        assertFalse(RoomWallItemLimit.hasRoomFor(3));
        assertFalse(RoomWallItemLimit.hasRoomFor(5000));

        RoomWallItemLimit.configure(-4);

        assertEquals(0, RoomWallItemLimit.maxWallItems());
        assertEquals(4000, RoomWallItemLimit.DEFAULT_MAX_WALL_ITEMS);
    }

    @Test
    void theRoomStatsPacketFollowsAirsLayout() {
        WiredRoomDiagnostics.Snapshot snapshot = mock(WiredRoomDiagnostics.Snapshot.class);
        when(snapshot.getUsageCurrentWindow()).thenReturn(120);
        when(snapshot.getUsageLimitPerWindow()).thenReturn(500);
        when(snapshot.isHeavy()).thenReturn(true);

        ByteBuffer body = body(
                new WiredRoomStatsComposer(snapshot, new WiredRoomStats(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)),
                Outgoing.WiredRoomStatsComposer);

        assertEquals(120d, body.getDouble());
        assertEquals(500d, body.getDouble());
        assertEquals(1, body.get());
        for (int index = 1; index <= 10; index++) {
            assertEquals(index, body.getInt());
        }
        assertEquals(0, body.remaining());
    }

    @Test
    void withoutARoomEveryFieldIsZero() {
        ByteBuffer body = body(new WiredRoomStatsComposer(null, null), Outgoing.WiredRoomStatsComposer);

        assertEquals(0d, body.getDouble());
        assertEquals(0d, body.getDouble());
        assertEquals(0, body.get());
        for (int index = 0; index < 10; index++) {
            assertEquals(0, body.getInt());
        }
        assertEquals(0, body.remaining());
    }

    @Test
    void theMonitorPacketNoLongerCarriesTheRoomCounters() {
        ByteBuffer body = body(new WiredMonitorDataComposer(null), Outgoing.WiredMonitorDataComposer);

        // 18 ints and the heavy flag, as before the counters existed.
        assertEquals(18 * 4 + 1, body.remaining());
    }

    private static Room room(int id, Set<InteractionWiredExtra> extras) {
        Room room = mock(Room.class);
        RoomSpecialTypes specialTypes = mock(RoomSpecialTypes.class);
        when(room.getId()).thenReturn(id);
        when(room.getRoomSpecialTypes()).thenReturn(specialTypes);
        when(specialTypes.getExtras()).thenReturn(extras);
        return room;
    }

    private static WiredExtraFurniVariable furni(boolean permanent) {
        WiredExtraFurniVariable variable = mock(WiredExtraFurniVariable.class);
        when(variable.isPermanentAvailability()).thenReturn(permanent);
        return variable;
    }

    private static WiredExtraUserVariable user(boolean permanent) {
        WiredExtraUserVariable variable = mock(WiredExtraUserVariable.class);
        when(variable.isPermanentAvailability()).thenReturn(permanent);
        return variable;
    }

    private static WiredExtraRoomVariable global(boolean permanent) {
        WiredExtraRoomVariable variable = mock(WiredExtraRoomVariable.class);
        when(variable.isPermanentAvailability()).thenReturn(permanent);
        return variable;
    }

    /** The packet body after the frame length and the header, which must be {@code header}. */
    private static ByteBuffer body(MessageComposer composer, int header) {
        ByteBuf packet = composer.compose().get();
        try {
            byte[] bytes = new byte[packet.readableBytes()];
            packet.getBytes(packet.readerIndex(), bytes);
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            assertEquals(bytes.length - 4, buffer.getInt());
            assertEquals(header, buffer.getShort());
            return buffer.slice();
        } finally {
            packet.release();
        }
    }
}
