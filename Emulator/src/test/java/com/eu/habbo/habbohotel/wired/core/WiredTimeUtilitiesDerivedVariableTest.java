package com.eu.habbo.habbohotel.wired.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraQuest;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraTimeUtilities;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraUserVariable;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomSpecialTypes;
import com.eu.habbo.habbohotel.rooms.WiredVariableDefinitionInfo;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** A time-utilities box on a variable's tile shows up as {@code name.mode.part} variables. */
class WiredTimeUtilitiesDerivedVariableTest {
    private static final int DEFINITION_ID = 4_321;
    private static final int INSTANT =
            (int) ZonedDateTime.of(2024, 3, 10, 23, 30, 15, 0, ZoneOffset.UTC).toEpochSecond();

    private static WiredExtraUserVariable definition(boolean hasValue) {
        WiredExtraUserVariable definition = mock(WiredExtraUserVariable.class);
        when(definition.getId()).thenReturn(DEFINITION_ID);
        when(definition.getVariableName()).thenReturn("joined");
        when(definition.hasValue()).thenReturn(hasValue);
        return definition;
    }

    private static WiredExtraTimeUtilities timeUtilities(int mask, int mode) {
        WiredExtraTimeUtilities box = new WiredExtraTimeUtilities(77, 1, mock(Item.class), "", 0, 0);
        box.saveData(new WiredSettings(new int[] {mask, mode}, "", new int[0], 0), null);
        return box;
    }

    private static Room room(InteractionWiredExtra definition, InteractionWiredExtra... boxes) {
        Set<InteractionWiredExtra> stack = new LinkedHashSet<>(List.of(boxes));
        stack.add(definition);
        RoomSpecialTypes specialTypes = mock(RoomSpecialTypes.class);
        when(specialTypes.getExtras(0, 0)).thenReturn(stack);
        when(specialTypes.getExtra(DEFINITION_ID)).thenReturn(definition);
        Room room = mock(Room.class);
        when(room.getRoomSpecialTypes()).thenReturn(specialTypes);
        when(room.getWiredTimezone()).thenReturn("Asia/Tokyo");
        return room;
    }

    private static WiredVariableDefinitionInfo info(boolean hasValue) {
        return new WiredVariableDefinitionInfo(DEFINITION_ID, "joined", hasValue, 0, false, false);
    }

    @Test
    void selectedPartsBecomeDottedVariables() {
        WiredExtraUserVariable definition = definition(true);
        WiredExtraTimeUtilities box = timeUtilities((1 << 4) | (1 << 10) | (1 << 24), 0);
        Room room = room(definition, box);

        List<WiredVariableDefinitionInfo> derived = WiredVariableLevelSystemSupport.getDerivedDefinitions(
                room, WiredVariableLevelSystemSupport.TARGET_USER, definition, info(true));

        assertEquals(
                List.of("joined.value.day", "joined.value.hour_of_day", "joined.value.year"),
                derived.stream().map(WiredVariableDefinitionInfo::getName).collect(Collectors.toList()));
        for (WiredVariableDefinitionInfo entry : derived) {
            assertTrue(entry.hasValue());
            // Value-mode parts are writable (they move the parent).
            assertFalse(entry.isReadOnly());

            WiredVariableLevelSystemSupport.DerivedDefinition resolved =
                    WiredVariableLevelSystemSupport.resolveDerivedDefinition(
                            room, WiredVariableLevelSystemSupport.TARGET_USER, entry.getItemId());
            assertNotNull(resolved);
            assertSame(box, resolved.getLevelSystem());
            assertEquals(DEFINITION_ID, resolved.getBaseDefinitionItemId());
            assertNull(WiredVariableLevelSystemSupport.resolveDerivedDefinition(
                    room, WiredVariableLevelSystemSupport.TARGET_FURNI, entry.getItemId()));
        }

        WiredVariableLevelSystemSupport.DerivedDefinition hour =
                WiredVariableLevelSystemSupport.resolveDerivedDefinition(
                        room,
                        WiredVariableLevelSystemSupport.TARGET_USER,
                        derived.get(1).getItemId());
        assertEquals(
                8,
                WiredVariableLevelSystemSupport.getDerivedValue(
                        room, hour.getLevelSystem(), hour.getSubvariableType(), INSTANT, 0, 0));
    }

    @Test
    void unselectedPartsDoNotResolve() {
        WiredExtraUserVariable definition = definition(true);
        WiredExtraTimeUtilities box = timeUtilities(1 << 4, 0);
        Room room = room(definition, box);
        int hourId = WiredVariableLevelSystemSupport.getDerivedDefinitions(
                        room, WiredVariableLevelSystemSupport.TARGET_USER, definition, info(true))
                .get(0)
                .getItemId();

        box.saveData(new WiredSettings(new int[] {1 << 5, 0}, "", new int[0], 0), null);

        assertNull(WiredVariableLevelSystemSupport.resolveDerivedDefinition(
                room, WiredVariableLevelSystemSupport.TARGET_USER, hourId));
    }

    @Test
    void timestampModesWorkOnVariablesWithoutAValue() {
        WiredExtraUserVariable definition = definition(false);
        Room valueRoom = room(definition, timeUtilities(1 << 10, WiredExtraTimeUtilities.MODE_VALUE));
        assertTrue(WiredVariableLevelSystemSupport.getDerivedDefinitions(
                        valueRoom, WiredVariableLevelSystemSupport.TARGET_USER, definition, info(false))
                .isEmpty());

        Room createdRoom = room(definition, timeUtilities(1 << 10, WiredExtraTimeUtilities.MODE_CREATION_TIME));
        List<WiredVariableDefinitionInfo> derived = WiredVariableLevelSystemSupport.getDerivedDefinitions(
                createdRoom, WiredVariableLevelSystemSupport.TARGET_USER, definition, info(false));
        assertEquals(
                List.of("joined.creation_time", "joined.creation_time.year"),
                derived.stream().map(WiredVariableDefinitionInfo::getName).collect(Collectors.toList()));

        WiredVariableLevelSystemSupport.DerivedDefinition year =
                WiredVariableLevelSystemSupport.resolveDerivedDefinition(
                        createdRoom,
                        WiredVariableLevelSystemSupport.TARGET_USER,
                        derived.get(1).getItemId());
        assertEquals(
                2024,
                WiredVariableLevelSystemSupport.getDerivedValue(
                        createdRoom, year.getLevelSystem(), year.getSubvariableType(), null, INSTANT, 0));
    }

    @Test
    void valuePartsAreWritableUnlessTheParentIsReadOnly() {
        WiredExtraUserVariable definition = definition(true);
        Room room = room(definition, timeUtilities((1 << 4) | (1 << 24), WiredExtraTimeUtilities.MODE_VALUE));

        List<WiredVariableDefinitionInfo> derived = WiredVariableLevelSystemSupport.getDerivedDefinitions(
                room, WiredVariableLevelSystemSupport.TARGET_USER, definition, info(true));
        assertEquals(2, derived.size());
        for (WiredVariableDefinitionInfo entry : derived) {
            assertFalse(entry.isReadOnly());
            WiredVariableLevelSystemSupport.DerivedDefinition resolved =
                    WiredVariableLevelSystemSupport.resolveDerivedDefinition(
                            room, WiredVariableLevelSystemSupport.TARGET_USER, entry.getItemId());
            assertTrue(resolved.isWritable());
            assertFalse(WiredVariableLevelSystemSupport.getDerivedDefinitionInfo(
                            room, WiredVariableLevelSystemSupport.TARGET_USER, entry.getItemId())
                    .isReadOnly());
        }

        WiredVariableDefinitionInfo readOnlyParent =
                new WiredVariableDefinitionInfo(DEFINITION_ID, "joined", true, 0, false, true);
        assertTrue(
                WiredVariableLevelSystemSupport.getDerivedDefinitions(
                                room, WiredVariableLevelSystemSupport.TARGET_USER, definition, readOnlyParent)
                        .stream()
                        .allMatch(WiredVariableDefinitionInfo::isReadOnly));
    }

    @Test
    void timestampModesListTheTimestampAsAReadOnlyVariable() {
        WiredExtraUserVariable definition = definition(true);
        Room room = room(definition, timeUtilities(1 << 10, WiredExtraTimeUtilities.MODE_LAST_UPDATE_TIME));

        List<WiredVariableDefinitionInfo> derived = WiredVariableLevelSystemSupport.getDerivedDefinitions(
                room, WiredVariableLevelSystemSupport.TARGET_USER, definition, info(true));
        assertEquals(
                List.of("joined.last_update_time", "joined.last_update_time.year"),
                derived.stream().map(WiredVariableDefinitionInfo::getName).collect(Collectors.toList()));
        assertTrue(derived.stream().allMatch(WiredVariableDefinitionInfo::isReadOnly));

        WiredVariableLevelSystemSupport.DerivedDefinition stamp =
                WiredVariableLevelSystemSupport.resolveDerivedDefinition(
                        room,
                        WiredVariableLevelSystemSupport.TARGET_USER,
                        derived.get(0).getItemId());
        assertNotNull(stamp);
        assertFalse(stamp.isWritable());
        assertEquals(
                INSTANT,
                WiredVariableLevelSystemSupport.getDerivedValue(
                        room, stamp.getLevelSystem(), stamp.getSubvariableType(), 5, 1, INSTANT));
        assertNull(WiredVariableLevelSystemSupport.writeDerivedValue(room, stamp, 5, 10));
    }

    @Test
    void sitsBesideAQuestBox() {
        WiredExtraUserVariable definition = definition(true);
        WiredExtraQuest quest = new WiredExtraQuest(78, 1, mock(Item.class), "", 0, 0);
        WiredExtraTimeUtilities box = timeUtilities(1 << 10, 0);
        Room room = room(definition, box, quest);

        assertSame(quest, WiredVariableLevelSystemSupport.getDerivedBox(room, definition));
        List<String> names = WiredVariableLevelSystemSupport.getDerivedDefinitions(
                        room, WiredVariableLevelSystemSupport.TARGET_USER, definition, info(true))
                .stream()
                .map(WiredVariableDefinitionInfo::getName)
                .collect(Collectors.toList());
        assertTrue(names.contains("joined.value.year"));
        assertTrue(names.contains("joined.is_complete"));
    }
}
