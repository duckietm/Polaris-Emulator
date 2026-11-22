package polaris.hotel.wired.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import polaris.hotel.items.interactions.wired.extra.WiredExtraTextOutputVariable;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.WiredVariableDefinitionInfo;
import polaris.hotel.rooms.variables.RoomFurniVariableManager;
import polaris.hotel.users.UserItem;

/** A Text Output (variable) placeholder prints "null" for a holder without the variable and for a flag. */
class WiredTextOutputNoValueTest {
    private static final int VARIABLE = 12;
    private static final int FURNI = 40;

    private final Room room = mock(Room.class);
    private final RoomFurniVariableManager variables = mock(RoomFurniVariableManager.class);
    private final UserItem item = mock(UserItem.class);
    private final WiredExtraTextOutputVariable output = mock(WiredExtraTextOutputVariable.class);

    private String shown() {
        when(this.room.getFurniVariableManager()).thenReturn(this.variables);
        when(this.item.getId()).thenReturn(FURNI);
        when(this.output.getVariableToken()).thenReturn("custom:" + VARIABLE);
        when(this.output.getVariableItemId()).thenReturn(VARIABLE);
        when(this.output.getDisplayType(this.room)).thenReturn(WiredExtraTextOutputVariable.DISPLAY_NUMERIC);
        return WiredTextPlaceholderUtil.resolveFurniVariableValue(this.room, this.item, this.output);
    }

    @Test
    void aFurniWithoutTheVariableShowsNull() {
        when(this.variables.hasVariable(FURNI, VARIABLE)).thenReturn(false);

        assertEquals("null", this.shown());
    }

    @Test
    void aHeldFlagShowsNull() {
        WiredVariableDefinitionInfo flag = mock(WiredVariableDefinitionInfo.class);
        when(flag.hasValue()).thenReturn(false);
        when(this.variables.hasVariable(FURNI, VARIABLE)).thenReturn(true);
        when(this.variables.getDefinitionInfo(VARIABLE)).thenReturn(flag);
        when(this.variables.getCurrentValue(FURNI, VARIABLE)).thenReturn(0);

        assertEquals("null", this.shown());
    }

    @Test
    void aHeldValueIsShown() {
        WiredVariableDefinitionInfo valued = mock(WiredVariableDefinitionInfo.class);
        when(valued.hasValue()).thenReturn(true);
        when(this.variables.hasVariable(FURNI, VARIABLE)).thenReturn(true);
        when(this.variables.getDefinitionInfo(VARIABLE)).thenReturn(valued);
        when(this.variables.getCurrentValue(FURNI, VARIABLE)).thenReturn(7);

        assertEquals("7", this.shown());
    }
}
