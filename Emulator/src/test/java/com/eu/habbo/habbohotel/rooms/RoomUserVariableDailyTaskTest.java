package com.eu.habbo.habbohotel.rooms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraDailyTask;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraQuest;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraUserVariable;
import com.eu.habbo.habbohotel.wired.core.WiredDailyTaskSupport;
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

class RoomUserVariableDailyTaskTest {
    private static final int USER_ID = 7;
    private static final int DEFINITION_ID = 10;
    private static final int YESTERDAY = seconds("2026-09-22T10:00:00Z");

    private final AtomicInteger clock = new AtomicInteger(YESTERDAY);

    private static int seconds(String instant) {
        return (int) Instant.parse(instant).getEpochSecond();
    }

    private static WiredExtraUserVariable counter() throws Exception {
        WiredExtraUserVariable definition = new WiredExtraUserVariable(DEFINITION_ID, 1, mock(Item.class), "", 0, 0);
        ResultSet set = mock(ResultSet.class);
        when(set.getString("wired_data"))
                .thenReturn("{\"variableName\":\"laps\",\"hasValue\":true,\"availability\":0}");
        definition.loadWiredData(set, null);
        return definition;
    }

    private static WiredExtraDailyTask daily(int target) {
        WiredExtraDailyTask box = new WiredExtraDailyTask(11, 1, mock(Item.class), "", 0, 0);
        box.saveData(new WiredSettings(new int[] {target}, "Laps", new int[0], 0), null);
        return box;
    }

    private static Room room(String timezone, InteractionWiredExtra... stack) {
        Room room = mock(Room.class);
        RoomSpecialTypes special = mock(RoomSpecialTypes.class);
        Set<InteractionWiredExtra> extras = new LinkedHashSet<>(List.of(stack));
        when(room.getId()).thenReturn(44);
        when(room.getWiredTimezone()).thenReturn(timezone);
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

    private static int derivedId(int subType) {
        return 700_000_000 + DEFINITION_ID * 16 + subType + 1;
    }

    @Test
    void aCounterWrittenYesterdayReadsAsZeroOnceTheDayTurns() throws Exception {
        RoomUserVariableManager manager = manager(room("UTC", counter(), daily(8)), 5, YESTERDAY);

        this.clock.set(seconds("2026-09-22T23:59:59Z"));
        assertEquals(5, manager.getCurrentValue(USER_ID, DEFINITION_ID));

        this.clock.set(seconds("2026-09-23T00:00:01Z"));
        assertEquals(0, manager.getCurrentValue(USER_ID, DEFINITION_ID));
        assertEquals(YESTERDAY, manager.getCreatedAt(USER_ID, DEFINITION_ID));
    }

    @Test
    void theDerivedProgressFollowsTheDailyReset() throws Exception {
        RoomUserVariableManager manager = manager(room("UTC", counter(), daily(8)), 8, YESTERDAY);

        assertEquals(8, manager.getCurrentValue(USER_ID, derivedId(WiredExtraQuest.SUB_PROGRESS)));
        assertEquals(1, manager.getCurrentValue(USER_ID, derivedId(WiredExtraQuest.SUB_IS_COMPLETE)));

        this.clock.set(seconds("2026-09-23T08:00:00Z"));
        assertEquals(0, manager.getCurrentValue(USER_ID, derivedId(WiredExtraQuest.SUB_PROGRESS)));
        assertEquals(0, manager.getCurrentValue(USER_ID, derivedId(WiredExtraQuest.SUB_IS_COMPLETE)));
        assertEquals(8, manager.getCurrentValue(USER_ID, derivedId(WiredExtraQuest.SUB_REMAINING)));
    }

    @Test
    void writingTheResetValueAgainChangesNothing() throws Exception {
        RoomUserVariableManager manager = manager(room("UTC", counter(), daily(8)), 5, YESTERDAY);
        this.clock.set(seconds("2026-09-23T08:00:00Z"));

        assertFalse(manager.updateVariableValue(USER_ID, DEFINITION_ID, 0));
        assertEquals(0, manager.getCurrentValue(USER_ID, DEFINITION_ID));
    }

    @Test
    void aCounterWithoutADailyTaskKeepsItsValue() throws Exception {
        RoomUserVariableManager manager = manager(room("UTC", counter()), 5, YESTERDAY);

        this.clock.set(seconds("2026-09-25T08:00:00Z"));
        assertEquals(5, manager.getCurrentValue(USER_ID, DEFINITION_ID));
    }

    @Test
    void theDayTurnsOnTheRoomsWiredClock() throws Exception {
        Room auckland = room("Pacific/Auckland", counter(), daily(8));

        assertEquals(
                seconds("2026-09-22T12:00:00Z"),
                WiredDailyTaskSupport.dayStart(auckland, seconds("2026-09-22T13:00:00Z")));

        RoomUserVariableManager manager = manager(auckland, 5, seconds("2026-09-22T11:30:00Z"));
        this.clock.set(seconds("2026-09-22T12:30:00Z"));
        assertEquals(0, manager.getCurrentValue(USER_ID, DEFINITION_ID));
    }
}
