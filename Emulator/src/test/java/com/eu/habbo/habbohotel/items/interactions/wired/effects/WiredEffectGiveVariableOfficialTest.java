package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.GameEnvironment;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomFurniVariableManager;
import com.eu.habbo.habbohotel.rooms.RoomManager;
import com.eu.habbo.habbohotel.rooms.RoomSpecialTypes;
import com.eu.habbo.habbohotel.rooms.WiredVariableDefinitionInfo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import com.eu.habbo.habbohotel.wired.core.WiredValueVariable;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

/** The official give variable: its layout, and an initial value read from another variable. */
class WiredEffectGiveVariableOfficialTest {
    private static final int VARIABLE = 40;
    private static final int REFERENCE = 41;

    private final Room room = mock(Room.class);
    private final RoomFurniVariableManager furniVariables = mock(RoomFurniVariableManager.class);
    private final HabboItem first = item(1);
    private final HabboItem second = item(2);

    WiredEffectGiveVariableOfficialTest() {
        WiredVariableDefinitionInfo info = mock(WiredVariableDefinitionInfo.class);
        when(info.hasValue()).thenReturn(true);
        when(furniVariables.getDefinitionInfo(anyInt())).thenReturn(info);
        when(room.getFurniVariableManager()).thenReturn(furniVariables);
        when(room.getHabboItem(anyInt())).thenAnswer(call -> item(call.getArgument(0)));
        // The reference variable: furni 1 holds 7, furni 2 holds 9.
        when(furniVariables.hasVariable(eq(1), eq(REFERENCE))).thenReturn(true);
        when(furniVariables.hasVariable(eq(2), eq(REFERENCE))).thenReturn(true);
        when(furniVariables.getCurrentValue(1, REFERENCE)).thenReturn(7);
        when(furniVariables.getCurrentValue(2, REFERENCE)).thenReturn(9);
    }

    private WiredEffectGiveVariable saved(int[] params, String text) throws Exception {
        WiredEffectGiveVariable box = new WiredEffectGiveVariable(10, 1, null, "", 0, 0);
        box.setRoomId(7);
        GameEnvironment environment = mock(GameEnvironment.class);
        RoomManager rooms = mock(RoomManager.class);
        when(environment.getRoomManager()).thenReturn(rooms);
        when(rooms.getRoom(7)).thenReturn(room);
        WiredSettings settings = new WiredSettings(params, text, new int[0], 0);
        settings.setFurniSourceTypes(new int[] {WiredSourceUtil.SOURCE_SELECTOR});
        try (MockedStatic<Emulator> emulator = mockStatic(Emulator.class, Mockito.CALLS_REAL_METHODS)) {
            emulator.when(Emulator::getGameEnvironment).thenReturn(environment);
            assertTrue(box.saveData(settings, null));
        }
        return box;
    }

    private void fire(WiredEffectGiveVariable box) {
        WiredContext ctx = mock(WiredContext.class);
        when(ctx.room()).thenReturn(room);
        try (MockedStatic<WiredSourceUtil> sources = mockStatic(WiredSourceUtil.class)) {
            sources.when(() -> WiredSourceUtil.resolveItems(any(), eq(WiredSourceUtil.SOURCE_SELECTOR), any()))
                    .thenReturn(List.of(this.first, this.second));
            box.execute(ctx);
        }
    }

    @Test
    void theInitialValueIsALongAndTheTargetOfficial() throws Exception {
        WiredEffectGiveVariable box = saved(new int[] {WiredValueVariable.TARGET_FURNI, -1, -5, 1, 0, -10, 0}, "40");

        assertEquals(WiredEffectGiveVariable.TARGET_FURNI, box.getTargetType());
        assertEquals(-5, box.getInitialValue());
        assertArrayEquals(new int[] {WiredSourceUtil.SOURCE_SELECTOR}, box.officialFurniSources());

        fire(box);
        verify(furniVariables).assignVariable(this.first, VARIABLE, -5, true);
    }

    @Test
    void fromAVariableOnTheSelectionEveryTargetGetsTheFirstHoldersValue() throws Exception {
        WiredEffectGiveVariable box = saved(
                new int[] {WiredValueVariable.TARGET_FURNI, 0, 0, 0, 1, WiredValueVariable.TARGET_FURNI, 0}, "40\t41");

        fire(box);
        verify(furniVariables).assignVariable(this.first, VARIABLE, 7, false);
        verify(furniVariables).assignVariable(this.second, VARIABLE, 7, false);
    }

    @Test
    void withSameHolderEachTargetReadsItsOwnValue() throws Exception {
        WiredEffectGiveVariable box = saved(
                new int[] {WiredValueVariable.TARGET_FURNI, 0, 0, 0, 1, WiredValueVariable.TARGET_FURNI, 1}, "40\t41");
        when(furniVariables.hasVariable(eq(2), eq(REFERENCE))).thenReturn(false);

        fire(box);
        verify(furniVariables).assignVariable(this.first, VARIABLE, 7, false);
        verify(furniVariables, never()).assignVariable(eq(this.second), anyInt(), any(), Mockito.anyBoolean());
    }

    @Test
    void fromAVariableNeedsTheReferenceVariable() {
        assertThrows(
                WiredSaveException.class,
                () -> saved(new int[] {WiredValueVariable.TARGET_FURNI, 0, 0, 0, 1, -10, 0}, "40"));
    }

    private static HabboItem item(int id) {
        HabboItem item = mock(HabboItem.class);
        when(item.getId()).thenReturn(id);
        return item;
    }

    @Test
    void withoutAVariableItSavesAndGivesNothing() throws Exception {
        WiredEffectGiveVariable box = saved(new int[] {WiredValueVariable.TARGET_FURNI, 0, 5, 0, 0, -10, 0}, "");

        fire(box);
        verify(furniVariables, never()).assignVariable(any(HabboItem.class), anyInt(), any(), Mockito.anyBoolean());
    }

    @Test
    void aGlobalDestinationSavesAndChangesNothing() throws Exception {
        WiredEffectGiveVariable box = saved(new int[] {WiredValueVariable.TARGET_GLOBAL, 0, 5, 0, 0, -10, 0}, "40");

        fire(box);
        verify(furniVariables, never()).assignVariable(any(HabboItem.class), anyInt(), any(), Mockito.anyBoolean());
    }

    @Test
    void aGiveThatGaveSomethingFlashesTheDefinitionBox() throws Exception {
        RoomSpecialTypes specialTypes = mock(RoomSpecialTypes.class);
        InteractionWiredExtra definition = mock(InteractionWiredExtra.class);
        InteractionWiredExtra timeUtilities = mock(InteractionWiredExtra.class);
        when(room.getRoomSpecialTypes()).thenReturn(specialTypes);
        when(specialTypes.getExtra(VARIABLE)).thenReturn(definition);
        when(specialTypes.getExtras(anyInt(), anyInt()))
                .thenReturn(new java.util.LinkedHashSet<>(List.of(definition, timeUtilities)));
        when(furniVariables.assignVariable(any(HabboItem.class), eq(VARIABLE), any(), Mockito.anyBoolean()))
                .thenReturn(true);
        WiredEffectGiveVariable box = saved(new int[] {WiredValueVariable.TARGET_FURNI, 0, 5, 0, 0, -10, 0}, "40");

        fire(box);
        verify(definition).flashBox(room);
        verify(timeUtilities).flashBox(room);
    }

    @Test
    void aGiveThatGaveNothingDoesNotFlash() throws Exception {
        RoomSpecialTypes specialTypes = mock(RoomSpecialTypes.class);
        InteractionWiredExtra definition = mock(InteractionWiredExtra.class);
        when(room.getRoomSpecialTypes()).thenReturn(specialTypes);
        when(specialTypes.getExtra(VARIABLE)).thenReturn(definition);
        WiredEffectGiveVariable box = saved(new int[] {WiredValueVariable.TARGET_FURNI, 0, 5, 0, 0, -10, 0}, "40");

        fire(box);
        verify(definition, never()).flashBox(any());
    }
}
