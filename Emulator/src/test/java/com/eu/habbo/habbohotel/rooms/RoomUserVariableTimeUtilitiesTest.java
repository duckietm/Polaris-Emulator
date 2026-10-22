package com.eu.habbo.habbohotel.rooms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraTimeUtilities;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraUserVariable;
import com.eu.habbo.habbohotel.wired.core.WiredEvent;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/** Writing a value-mode Time Utilities part moves its parent; every part reports its own change. */
class RoomUserVariableTimeUtilitiesTest {
    private static final int USER_ID = 7;
    private static final int DEFINITION_ID = 10;
    private static final int JOINED = seconds("2024-03-10T23:30:15Z");
    private static final int WRITTEN_AT = seconds("2026-10-17T12:00:00Z");

    private final AtomicInteger clock = new AtomicInteger(WRITTEN_AT);

    private static int seconds(String instant) {
        return (int) Instant.parse(instant).getEpochSecond();
    }

    private static int partId(int subType) {
        return 1_500_000_000 + DEFINITION_ID * 32 + subType;
    }

    private static WiredExtraUserVariable joined() throws Exception {
        WiredExtraUserVariable definition = new WiredExtraUserVariable(DEFINITION_ID, 1, mock(Item.class), "", 0, 0);
        ResultSet set = mock(ResultSet.class);
        when(set.getString("wired_data"))
                .thenReturn("{\"variableName\":\"joined\",\"hasValue\":true,\"availability\":0}");
        definition.loadWiredData(set, null);
        return definition;
    }

    private static WiredExtraTimeUtilities timeUtilities(int mask, int mode) {
        WiredExtraTimeUtilities box = new WiredExtraTimeUtilities(11, 1, mock(Item.class), "", 0, 0);
        box.saveData(new WiredSettings(new int[] {mask, mode}, "", new int[0], 0), null);
        return box;
    }

    private static Room room(InteractionWiredExtra... stack) {
        Room room = mock(Room.class);
        RoomSpecialTypes special = mock(RoomSpecialTypes.class);
        Set<InteractionWiredExtra> extras = new LinkedHashSet<>(List.of(stack));
        when(room.getId()).thenReturn(44);
        when(room.getWiredTimezone()).thenReturn("UTC");
        when(room.getRoomSpecialTypes()).thenReturn(special);
        when(special.getExtras()).thenReturn(extras);
        when(special.getExtras(0, 0)).thenReturn(extras);
        for (InteractionWiredExtra extra : stack)
            when(special.getExtra(extra.getId())).thenReturn(extra);
        return room;
    }

    private RoomUserVariableManager manager(Room room, int value, int updatedAt) throws Exception {
        RoomUserVariableManager manager = new RoomUserVariableManager(
                room, new RoomUserVariableRepository(mock(DataSource.class)), this.clock::get);

        Class<?> assignmentType = RoomVariableAssignment.class;
        Constructor<?> constructor = assignmentType.getDeclaredConstructor(Integer.class, int.class, int.class);
        constructor.setAccessible(true);
        Field field = RoomUserVariableManager.class.getDeclaredField("activeAssignmentsByUserId");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<Integer, ConcurrentHashMap<Integer, Object>> assignments =
                (Map<Integer, ConcurrentHashMap<Integer, Object>>) field.get(manager);
        ConcurrentHashMap<Integer, Object> own = new ConcurrentHashMap<>();
        own.put(DEFINITION_ID, constructor.newInstance(value, updatedAt, updatedAt));
        assignments.put(USER_ID, own);
        return manager;
    }

    private static void verifyChange(
            MockedStatic<WiredManager> wired, Room room, int id, WiredEvent.VariableChangeKind kind) {
        wired.verify(() -> WiredManager.triggerUserVariableChanged(
                eq(room), eq(USER_ID), eq(id), eq(false), eq(false), eq(kind), anyLong(), anyLong()));
    }

    @Test
    void writingHourOfDayMovesTheParentAndEachPartReportsItsOwnChange() throws Exception {
        int hour = WiredExtraTimeUtilities.SUB_HOUR_OF_DAY;
        int year = WiredExtraTimeUtilities.SUB_YEAR;
        WiredExtraTimeUtilities box = timeUtilities((1 << hour) | (1 << year), WiredExtraTimeUtilities.MODE_VALUE);
        Room room = room(joined(), box);
        RoomUserVariableManager manager = manager(room, JOINED, JOINED);

        try (MockedStatic<WiredManager> wired = mockStatic(WiredManager.class)) {
            assertTrue(manager.updateVariableValue(USER_ID, partId(hour), 5));

            assertEquals(seconds("2024-03-10T05:30:15Z"), manager.getCurrentValue(USER_ID, DEFINITION_ID));
            assertEquals(5, manager.getCurrentValue(USER_ID, partId(hour)));
            verifyChange(wired, room, DEFINITION_ID, WiredEvent.VariableChangeKind.DECREASED);
            verifyChange(wired, room, partId(hour), WiredEvent.VariableChangeKind.DECREASED);
            verifyChange(wired, room, partId(year), WiredEvent.VariableChangeKind.UNCHANGED);
        }

        try (MockedStatic<WiredManager> wired = mockStatic(WiredManager.class)) {
            // A Give with override writes the part too; without override nothing changes.
            assertFalse(manager.assignVariable(USER_ID, partId(year), 2030, false));
            assertTrue(manager.assignVariable(USER_ID, partId(year), 2030, true));
            assertEquals(seconds("2030-03-10T05:30:15Z"), manager.getCurrentValue(USER_ID, DEFINITION_ID));
            verifyChange(wired, room, partId(year), WiredEvent.VariableChangeKind.INCREASED);
        }
    }

    @Test
    void partsCannotBeRemovedOrWrittenWithoutTheParent() throws Exception {
        int hour = WiredExtraTimeUtilities.SUB_HOUR_OF_DAY;
        Room room = room(joined(), timeUtilities(1 << hour, WiredExtraTimeUtilities.MODE_VALUE));
        RoomUserVariableManager manager = manager(room, JOINED, JOINED);

        try (MockedStatic<WiredManager> wired = mockStatic(WiredManager.class)) {
            assertFalse(manager.removeVariable(USER_ID, partId(hour)));
            assertTrue(manager.hasVariable(USER_ID, DEFINITION_ID));
            assertFalse(manager.updateVariableValue(USER_ID + 1, partId(hour), 5));
            wired.verify(
                    () -> WiredManager.triggerUserVariableChanged(
                            eq(room),
                            anyInt(),
                            anyInt(),
                            anyBoolean(),
                            anyBoolean(),
                            eq(WiredEvent.VariableChangeKind.DECREASED),
                            anyLong(),
                            anyLong()),
                    never());
        }
    }

    @Test
    void timestampPartsAreReadOnlyAndCompareAgainstTheOldUpdateTime() throws Exception {
        int year = WiredExtraTimeUtilities.SUB_YEAR;
        Room room = room(joined(), timeUtilities(1 << year, WiredExtraTimeUtilities.MODE_LAST_UPDATE_TIME));
        RoomUserVariableManager manager = manager(room, 3, JOINED);
        int stamp = partId(WiredExtraTimeUtilities.SUB_TIMESTAMP);

        assertEquals(JOINED, manager.getCurrentValue(USER_ID, stamp));
        assertEquals(2024, manager.getCurrentValue(USER_ID, partId(year)));

        try (MockedStatic<WiredManager> wired = mockStatic(WiredManager.class)) {
            assertFalse(manager.updateVariableValue(USER_ID, partId(year), 2030));
            assertFalse(manager.updateVariableValue(USER_ID, stamp, 5));

            assertTrue(manager.updateVariableValue(USER_ID, DEFINITION_ID, 4));
            assertEquals(WRITTEN_AT, manager.getCurrentValue(USER_ID, stamp));
            verifyChange(wired, room, partId(year), WiredEvent.VariableChangeKind.INCREASED);
            verifyChange(wired, room, stamp, WiredEvent.VariableChangeKind.INCREASED);
        }
    }
}
