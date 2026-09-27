package com.eu.habbo.habbohotel.rooms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraUserVariable;
import java.sql.ResultSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RoomUserVariableHoldersTest {
    private static final int DEFINITION_ID = 10;

    @Test
    void keysKeepUsersPetsAndBotsApart() {
        assertEquals(7, UserVariableHolders.ofUser(7));
        assertEquals(-14, UserVariableHolders.ofPet(7));
        assertEquals(-15, UserVariableHolders.ofBot(7));

        assertEquals(UserVariableHolders.Kind.USER, UserVariableHolders.kindOf(7));
        assertEquals(UserVariableHolders.Kind.PET, UserVariableHolders.kindOf(-14));
        assertEquals(UserVariableHolders.Kind.BOT, UserVariableHolders.kindOf(-15));
        assertEquals(7, UserVariableHolders.idOf(-14));
        assertEquals(7, UserVariableHolders.idOf(-15));

        int max = UserVariableHolders.MAX_UNIT_ID;
        assertEquals(max, UserVariableHolders.idOf(UserVariableHolders.ofBot(max)));
        assertEquals(max, UserVariableHolders.idOf(UserVariableHolders.ofPet(max)));
        assertEquals(0, UserVariableHolders.ofPet(max + 1));
        assertEquals(0, UserVariableHolders.ofBot(0));
        assertEquals(0, UserVariableHolders.ofUser(-3));
        assertNull(UserVariableHolders.kindOf(0));
        assertFalse(UserVariableHolders.isValid(Integer.MIN_VALUE));
    }

    @Test
    void aPetAndAUserWithTheSameIdHoldTheirOwnValues() throws Exception {
        RoomJdbcTestSupport.RecordingDataSource database = new RoomJdbcTestSupport.RecordingDataSource();
        RoomUserVariableManager manager = manager(database);
        int pet = UserVariableHolders.ofPet(7);

        assertTrue(manager.assignVariable(pet, DEFINITION_ID, 5, true));
        assertTrue(manager.assignVariable(7, DEFINITION_ID, 9, true));

        assertEquals(5, manager.getCurrentValue(pet, DEFINITION_ID));
        assertEquals(9, manager.getCurrentValue(7, DEFINITION_ID));
        assertFalse(manager.hasVariable(UserVariableHolders.ofBot(7), DEFINITION_ID));
        assertEquals(-14, database.calls().getFirst().parameters().get(2));

        assertTrue(manager.removeVariable(pet, DEFINITION_ID));
        assertFalse(manager.hasVariable(pet, DEFINITION_ID));
        assertTrue(manager.hasVariable(7, DEFINITION_ID));
    }

    @Test
    void aPetGetsItsPermanentValueBackAndLosesItWhenItLeaves() throws Exception {
        RoomJdbcTestSupport.RecordingDataSource database = new RoomJdbcTestSupport.RecordingDataSource();
        database.rows(sql -> sql.contains("SELECT variable_item_id")
                ? List.of(Map.of("variable_item_id", DEFINITION_ID, "value", 4, "created_at", 1, "updated_at", 2))
                : List.of());
        RoomUserVariableManager manager = manager(database);
        int pet = UserVariableHolders.ofPet(7);

        manager.restorePermanentAssignments(pet);

        assertEquals(4, manager.getCurrentValue(pet, DEFINITION_ID));
        assertEquals(-14, database.calls().getFirst().parameters().get(2));

        manager.clearAssignmentsForUser(pet);
        assertFalse(manager.hasVariable(pet, DEFINITION_ID));
    }

    @Test
    void aLoadingRoomRestoresAllItsPetsAndBotsWithOneQuery() throws Exception {
        RoomJdbcTestSupport.RecordingDataSource database = new RoomJdbcTestSupport.RecordingDataSource();
        database.rows(sql -> sql.contains("user_id < 0")
                ? List.of(
                        Map.of(
                                "user_id",
                                -14,
                                "variable_item_id",
                                DEFINITION_ID,
                                "value",
                                4,
                                "created_at",
                                1,
                                "updated_at",
                                2),
                        Map.of(
                                "user_id",
                                -15,
                                "variable_item_id",
                                DEFINITION_ID,
                                "value",
                                6,
                                "created_at",
                                1,
                                "updated_at",
                                2),
                        Map.of(
                                "user_id",
                                -40,
                                "variable_item_id",
                                DEFINITION_ID,
                                "value",
                                8,
                                "created_at",
                                1,
                                "updated_at",
                                2))
                : List.of());
        RoomUserVariableManager manager = manager(database);
        Room room = roomOf(manager);
        com.eu.habbo.habbohotel.pets.Pet pet = mock(com.eu.habbo.habbohotel.pets.Pet.class);
        when(pet.getId()).thenReturn(7);
        com.eu.habbo.habbohotel.bots.Bot bot = mock(com.eu.habbo.habbohotel.bots.Bot.class);
        when(bot.getId()).thenReturn(7);
        when(room.getCurrentPets()).thenReturn(new it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<>(Map.of(7, pet)));
        when(room.getCurrentBots()).thenReturn(new it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<>(Map.of(7, bot)));

        manager.restoreUnitHolders();

        assertEquals(1, database.calls().size());
        assertEquals(4, manager.getCurrentValue(UserVariableHolders.ofPet(7), DEFINITION_ID));
        assertEquals(6, manager.getCurrentValue(UserVariableHolders.ofBot(7), DEFINITION_ID));
        assertFalse(manager.hasVariable(UserVariableHolders.ofPet(20), DEFINITION_ID));
    }

    private static Room roomOf(RoomUserVariableManager manager) throws Exception {
        java.lang.reflect.Field field = RoomUserVariableManager.class.getDeclaredField("room");
        field.setAccessible(true);
        return (Room) field.get(manager);
    }

    private static RoomUserVariableManager manager(RoomJdbcTestSupport.RecordingDataSource database) throws Exception {
        WiredExtraUserVariable definition = new WiredExtraUserVariable(DEFINITION_ID, 1, mock(Item.class), "", 0, 0);
        ResultSet set = mock(ResultSet.class);
        when(set.getString("wired_data"))
                .thenReturn("{\"variableName\":\"points\",\"hasValue\":true,\"availability\":"
                        + WiredExtraUserVariable.AVAILABILITY_PERMANENT + "}");
        definition.loadWiredData(set, null);

        Room room = mock(Room.class);
        RoomSpecialTypes special = mock(RoomSpecialTypes.class);
        Set<InteractionWiredExtra> extras = new LinkedHashSet<>(List.of(definition));
        when(room.getId()).thenReturn(44);
        when(room.getWiredTimezone()).thenReturn("UTC");
        when(room.getRoomSpecialTypes()).thenReturn(special);
        when(special.getExtras()).thenReturn(extras);
        when(special.getExtras(0, 0)).thenReturn(extras);
        when(special.getExtra(DEFINITION_ID)).thenReturn(definition);
        when(room.getArrayVariableManager()).thenReturn(mock(RoomArrayVariableManager.class));
        return new RoomUserVariableManager(room, new RoomUserVariableRepository(database), () -> 1000);
    }
}
