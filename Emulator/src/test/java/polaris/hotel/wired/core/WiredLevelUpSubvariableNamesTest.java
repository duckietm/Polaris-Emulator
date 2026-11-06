package polaris.hotel.wired.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import polaris.hotel.items.Item;
import polaris.hotel.items.interactions.InteractionWiredExtra;
import polaris.hotel.items.interactions.wired.WiredSettings;
import polaris.hotel.items.interactions.wired.extra.WiredExtraUserVariable;
import polaris.hotel.items.interactions.wired.extra.WiredExtraVariableLevelUpSystem;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomSpecialTypes;
import polaris.hotel.rooms.WiredVariableDefinitionInfo;

/** The Level-up add-on's sub-variables carry AIR's names (VariableLvlUpAddon). */
class WiredLevelUpSubvariableNamesTest {
    private static final int DEFINITION_ID = 4_322;

    @Test
    void allEightPartsUseTheOfficialNames() {
        WiredExtraUserVariable definition = mock(WiredExtraUserVariable.class);
        when(definition.getId()).thenReturn(DEFINITION_ID);
        when(definition.getVariableName()).thenReturn("xp");
        when(definition.hasValue()).thenReturn(true);
        WiredExtraVariableLevelUpSystem levelUp =
                new WiredExtraVariableLevelUpSystem(78, 1, mock(Item.class), "", 0, 0);
        levelUp.saveData(new WiredSettings(new int[] {0xFF, 1, 100, 10}, "", new int[0], 0), null);
        RoomSpecialTypes specialTypes = mock(RoomSpecialTypes.class);
        when(specialTypes.getExtras(0, 0))
                .thenReturn(new LinkedHashSet<InteractionWiredExtra>(List.of(levelUp, definition)));
        when(specialTypes.getExtra(DEFINITION_ID)).thenReturn(definition);
        Room room = mock(Room.class);
        when(room.getRoomSpecialTypes()).thenReturn(specialTypes);

        List<String> names = WiredVariableLevelSystemSupport.getDerivedDefinitions(
                        room,
                        WiredVariableLevelSystemSupport.TARGET_USER,
                        definition,
                        new WiredVariableDefinitionInfo(DEFINITION_ID, "xp", true, 0, false, false))
                .stream()
                .map(WiredVariableDefinitionInfo::getName)
                .collect(Collectors.toList());

        assertEquals(
                List.of(
                        "xp.current_level",
                        "xp.current_xp",
                        "xp.is_maxed",
                        "xp.max_level",
                        "xp.progress",
                        "xp.progress_percentage",
                        "xp.xp_remaining",
                        "xp.xp_required"),
                names);
    }
}
