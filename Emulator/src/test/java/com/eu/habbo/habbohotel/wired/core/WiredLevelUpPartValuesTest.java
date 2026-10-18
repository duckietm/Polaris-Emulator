package com.eu.habbo.habbohotel.wired.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraUserVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraVariableLevelUpSystem;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomSpecialTypes;
import com.eu.habbo.habbohotel.rooms.WiredVariableDefinitionInfo;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** The level-up parts read, write and exist as in Habbo (linear 100 over three levels: 0, 100, 200). */
class WiredLevelUpPartValuesTest {
    private static final int DEFINITION_ID = 4_322;

    private static WiredExtraVariableLevelUpSystem levelUp() {
        WiredExtraVariableLevelUpSystem levelUp =
                new WiredExtraVariableLevelUpSystem(78, 1, mock(Item.class), "", 0, 0);
        levelUp.saveData(new WiredSettings(new int[] {0xFF, 1, 100, 3}, "", new int[0], 0), null);
        return levelUp;
    }

    private static Integer part(WiredExtraVariableLevelUpSystem levelUp, int sub, int xp) {
        return WiredVariableLevelSystemSupport.getDerivedValue(levelUp, sub, xp);
    }

    @Test
    void belowTheLastLevelThePartsCountProgressAndIsMaxedIsAbsent() {
        WiredExtraVariableLevelUpSystem levelUp = levelUp();
        assertEquals(1, part(levelUp, WiredExtraVariableLevelUpSystem.SUB_CURRENT_LEVEL, 50));
        assertEquals(50, part(levelUp, WiredExtraVariableLevelUpSystem.SUB_CURRENT_XP, 50));
        assertEquals(50, part(levelUp, WiredExtraVariableLevelUpSystem.SUB_LEVEL_PROGRESS, 50));
        assertEquals(50, part(levelUp, WiredExtraVariableLevelUpSystem.SUB_LEVEL_PROGRESS_PERCENT, 50));
        assertEquals(100, part(levelUp, WiredExtraVariableLevelUpSystem.SUB_TOTAL_XP_REQUIRED, 50));
        assertEquals(50, part(levelUp, WiredExtraVariableLevelUpSystem.SUB_XP_REMAINING, 50));
        assertNull(part(levelUp, WiredExtraVariableLevelUpSystem.SUB_IS_AT_MAX, 50));
        assertEquals(3, part(levelUp, WiredExtraVariableLevelUpSystem.SUB_MAX_LEVEL, 50));
    }

    @Test
    void atTheLastLevelXpStopsAndTheProgressPartsReadZero() {
        WiredExtraVariableLevelUpSystem levelUp = levelUp();
        for (int xp : new int[] {200, 1200}) {
            assertEquals(3, part(levelUp, WiredExtraVariableLevelUpSystem.SUB_CURRENT_LEVEL, xp));
            assertEquals(200, part(levelUp, WiredExtraVariableLevelUpSystem.SUB_CURRENT_XP, xp));
            assertEquals(0, part(levelUp, WiredExtraVariableLevelUpSystem.SUB_LEVEL_PROGRESS, xp));
            assertEquals(0, part(levelUp, WiredExtraVariableLevelUpSystem.SUB_LEVEL_PROGRESS_PERCENT, xp));
            assertEquals(0, part(levelUp, WiredExtraVariableLevelUpSystem.SUB_TOTAL_XP_REQUIRED, xp));
            assertEquals(0, part(levelUp, WiredExtraVariableLevelUpSystem.SUB_XP_REMAINING, xp));
            assertEquals(1, part(levelUp, WiredExtraVariableLevelUpSystem.SUB_IS_AT_MAX, xp));
        }
    }

    @Test
    void currentXpIsWritableCappedAtTheLastLevelAndIsMaxedHasNoValue() {
        WiredExtraUserVariable definition = mock(WiredExtraUserVariable.class);
        when(definition.getId()).thenReturn(DEFINITION_ID);
        when(definition.getVariableName()).thenReturn("xp");
        when(definition.hasValue()).thenReturn(true);
        WiredExtraVariableLevelUpSystem levelUp = levelUp();
        RoomSpecialTypes specialTypes = mock(RoomSpecialTypes.class);
        when(specialTypes.getExtras(0, 0))
                .thenReturn(new LinkedHashSet<InteractionWiredExtra>(List.of(levelUp, definition)));
        when(specialTypes.getExtra(DEFINITION_ID)).thenReturn(definition);
        Room room = mock(Room.class);
        when(room.getRoomSpecialTypes()).thenReturn(specialTypes);

        Map<String, WiredVariableDefinitionInfo> parts = WiredVariableLevelSystemSupport.getDerivedDefinitions(
                        room,
                        WiredVariableLevelSystemSupport.TARGET_USER,
                        definition,
                        new WiredVariableDefinitionInfo(DEFINITION_ID, "xp", true, 0, false, false))
                .stream()
                .collect(Collectors.toMap(WiredVariableDefinitionInfo::getName, info -> info));

        assertFalse(parts.get("xp.current_xp").isReadOnly());
        assertTrue(parts.get("xp.current_level").isReadOnly());
        assertFalse(parts.get("xp.is_maxed").hasValue());
        assertTrue(parts.get("xp.current_level").hasValue());

        WiredVariableLevelSystemSupport.DerivedDefinition currentXp =
                WiredVariableLevelSystemSupport.resolveDerivedDefinition(
                        room,
                        WiredVariableLevelSystemSupport.TARGET_USER,
                        parts.get("xp.current_xp").getItemId());
        assertTrue(currentXp.isWritable());
        assertEquals(200, WiredVariableLevelSystemSupport.writeDerivedValue(room, currentXp, 50, 250));
        assertEquals(150, WiredVariableLevelSystemSupport.writeDerivedValue(room, currentXp, 50, 150));
        assertEquals(0, WiredVariableLevelSystemSupport.writeDerivedValue(room, currentXp, 50, -5));
        assertTrue(
                WiredVariableLevelSystemSupport.isPresencePart(WiredVariableLevelSystemSupport.resolveDerivedDefinition(
                        room,
                        WiredVariableLevelSystemSupport.TARGET_USER,
                        parts.get("xp.is_maxed").getItemId())));
    }
}
