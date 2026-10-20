package com.eu.habbo.habbohotel.rooms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraFurniVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraUserVariable;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredVariableHolderLimit;
import java.sql.ResultSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/**
 * Habbo's cap on the different variables one holder may hold: past it a new variable is not given and the monitor
 * counts TOO_MANY_VARIABLES (error 6); a value the holder already has still changes.
 */
class RoomVariableHolderLimitTest {

    private static final int ROOM_ID = 44;

    @AfterEach
    void resetTheCap() {
        WiredVariableHolderLimit.configure(WiredVariableHolderLimit.DEFAULT_MAX_VARIABLES_PER_HOLDER);
    }

    @Test
    void theCapIsHighAndZeroTurnsItOff() {
        assertEquals(200, WiredVariableHolderLimit.DEFAULT_MAX_VARIABLES_PER_HOLDER);
        assertTrue(WiredVariableHolderLimit.hasRoomFor(199));
        assertFalse(WiredVariableHolderLimit.hasRoomFor(200));

        WiredVariableHolderLimit.configure(0);
        assertTrue(WiredVariableHolderLimit.hasRoomFor(100_000));
    }

    @Test
    void aUserAtTheCapIsNotGivenAnotherVariableAndTheMonitorCountsIt() throws Exception {
        WiredVariableHolderLimit.configure(2);
        Set<InteractionWiredExtra> extras = new LinkedHashSet<>();
        for (int id = 10; id <= 12; id++) extras.add(userVariable(id, "var" + id));
        RoomUserVariableManager manager = new RoomUserVariableManager(
                room(extras),
                new RoomUserVariableRepository(new RoomJdbcTestSupport.RecordingDataSource()),
                () -> 1000);

        try (MockedStatic<WiredManager> wired = mockStatic(WiredManager.class)) {
            assertTrue(manager.assignVariable(7, 10, 1, true));
            assertTrue(manager.assignVariable(7, 11, 1, true));

            assertFalse(manager.assignVariable(7, 12, 1, true));
            assertFalse(manager.hasVariable(7, 12));
            wired.verify(() -> WiredManager.noteTooManyVariables(
                    eq(ROOM_ID), contains("user #7 already holds 2 variables"), eq("var12"), eq(12)));

            // What the user holds keeps changing, and another user has a cap of their own.
            assertTrue(manager.assignVariable(7, 11, 5, true));
            assertEquals(5, manager.getCurrentValue(7, 11));
            assertTrue(manager.assignVariable(8, 12, 3, true));
        }
    }

    @Test
    void aPetAtTheCapIsNamedAsAPet() throws Exception {
        WiredVariableHolderLimit.configure(1);
        Set<InteractionWiredExtra> extras = new LinkedHashSet<>(List.of(userVariable(10, "a"), userVariable(11, "b")));
        RoomUserVariableManager manager = new RoomUserVariableManager(
                room(extras),
                new RoomUserVariableRepository(new RoomJdbcTestSupport.RecordingDataSource()),
                () -> 1000);
        int pet = UserVariableHolders.ofPet(7);

        try (MockedStatic<WiredManager> wired = mockStatic(WiredManager.class)) {
            assertTrue(manager.assignVariable(pet, 10, 1, true));
            assertFalse(manager.assignVariable(pet, 11, 1, true));

            wired.verify(() -> WiredManager.noteTooManyVariables(eq(ROOM_ID), contains("pet #7"), eq("b"), eq(11)));
        }
    }

    @Test
    void aFurniAtTheCapIsNotGivenAnotherVariable() throws Exception {
        WiredVariableHolderLimit.configure(1);
        Set<InteractionWiredExtra> extras =
                new LinkedHashSet<>(List.of(furniVariable(20, "colour"), furniVariable(21, "size")));
        RoomFurniVariableManager manager = new RoomFurniVariableManager(room(extras));
        java.lang.reflect.Field loaded = RoomFurniVariableManager.class.getDeclaredField("permanentAssignmentsLoaded");
        loaded.setAccessible(true);
        loaded.set(manager, true);
        // A broadcast already pending: the test has no threading to schedule one on.
        java.lang.reflect.Field broadcast = RoomFurniVariableManager.class.getDeclaredField("broadcastRequested");
        broadcast.setAccessible(true);
        ((java.util.concurrent.atomic.AtomicBoolean) broadcast.get(manager)).set(true);
        HabboItem furni = mock(HabboItem.class);
        when(furni.getId()).thenReturn(300);

        try (MockedStatic<WiredManager> wired = mockStatic(WiredManager.class)) {
            assertTrue(manager.assignVariable(furni, 20, 1, true));
            assertFalse(manager.assignVariable(furni, 21, 1, true));
            assertFalse(manager.hasVariable(300, 21));
            wired.verify(() -> WiredManager.noteTooManyVariables(
                    eq(ROOM_ID), contains("furni #300 already holds 1 variables"), eq("size"), eq(21)));

            WiredVariableHolderLimit.configure(0);
            assertTrue(manager.assignVariable(furni, 21, 1, true), "no cap");
        }
    }

    @Test
    void underTheCapNothingIsCounted() throws Exception {
        Set<InteractionWiredExtra> extras = new LinkedHashSet<>(List.of(userVariable(10, "a")));
        RoomUserVariableManager manager = new RoomUserVariableManager(
                room(extras),
                new RoomUserVariableRepository(new RoomJdbcTestSupport.RecordingDataSource()),
                () -> 1000);

        try (MockedStatic<WiredManager> wired = mockStatic(WiredManager.class)) {
            assertTrue(manager.assignVariable(7, 10, 1, true));
            wired.verify(
                    () -> WiredManager.noteTooManyVariables(anyInt(), anyString(), anyString(), anyInt()), never());
        }
    }

    private static WiredExtraUserVariable userVariable(int id, String name) throws Exception {
        WiredExtraUserVariable definition = new WiredExtraUserVariable(id, 1, mock(Item.class), "", 0, 0);
        ResultSet set = mock(ResultSet.class);
        when(set.getString("wired_data"))
                .thenReturn("{\"variableName\":\"" + name + "\",\"hasValue\":true,\"availability\":"
                        + WiredExtraUserVariable.AVAILABILITY_ROOM + "}");
        definition.loadWiredData(set, null);
        return definition;
    }

    private static WiredExtraFurniVariable furniVariable(int id, String name) throws Exception {
        WiredExtraFurniVariable definition = new WiredExtraFurniVariable(id, 1, mock(Item.class), "", 0, 0);
        ResultSet set = mock(ResultSet.class);
        when(set.getString("wired_data"))
                .thenReturn("{\"variableName\":\"" + name + "\",\"hasValue\":true,\"availability\":"
                        + WiredExtraFurniVariable.AVAILABILITY_ROOM_ACTIVE + "}");
        definition.loadWiredData(set, null);
        return definition;
    }

    private static Room room(Set<InteractionWiredExtra> extras) {
        Room room = mock(Room.class);
        RoomSpecialTypes special = mock(RoomSpecialTypes.class);
        when(room.getId()).thenReturn(ROOM_ID);
        when(room.getWiredTimezone()).thenReturn("UTC");
        when(room.getRoomSpecialTypes()).thenReturn(special);
        when(special.getExtras()).thenReturn(extras);
        when(special.getExtras(0, 0)).thenReturn(extras);
        for (InteractionWiredExtra extra : extras) {
            when(special.getExtra(extra.getId())).thenReturn(extra);
        }
        when(room.getArrayVariableManager()).thenReturn(mock(RoomArrayVariableManager.class));
        return room;
    }
}
