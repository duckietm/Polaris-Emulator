package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.GameEnvironment;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomFurniVariableManager;
import com.eu.habbo.habbohotel.rooms.RoomManager;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.rooms.RoomUserVariableManager;
import com.eu.habbo.habbohotel.rooms.RoomVariableManager;
import com.eu.habbo.habbohotel.rooms.RoomWiredRuntime;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredInternalVariableSupport;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import com.eu.habbo.habbohotel.wired.core.WiredValueVariable;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

/** The official remove variable on a global and on @handitem. */
class WiredEffectRemoveVariableOfficialTest {
    private final Room room = mock(Room.class);
    private final RoomUserVariableManager userVariables = mock(RoomUserVariableManager.class);
    private final RoomFurniVariableManager furniVariables = mock(RoomFurniVariableManager.class);
    private final RoomVariableManager roomVariables = mock(RoomVariableManager.class);

    WiredEffectRemoveVariableOfficialTest() {
        when(room.getUserVariableManager()).thenReturn(userVariables);
        when(room.getFurniVariableManager()).thenReturn(furniVariables);
        when(room.getRoomVariableManager()).thenReturn(roomVariables);
    }

    private WiredEffectRemoveVariable saved(int[] params, String text) throws Exception {
        WiredEffectRemoveVariable box = new WiredEffectRemoveVariable(10, 1, null, "", 0, 0);
        box.setRoomId(7);
        GameEnvironment environment = mock(GameEnvironment.class);
        RoomManager rooms = mock(RoomManager.class);
        when(environment.getRoomManager()).thenReturn(rooms);
        when(rooms.getRoom(7)).thenReturn(room);
        WiredSettings settings = new WiredSettings(params, text, new int[0], 0);
        try (MockedStatic<Emulator> emulator = mockStatic(Emulator.class, Mockito.CALLS_REAL_METHODS)) {
            emulator.when(Emulator::getGameEnvironment).thenReturn(environment);
            assertTrue(box.saveData(settings, null));
        }
        return box;
    }

    @Test
    void aGlobalSavesAndRemovesNothing() throws Exception {
        WiredEffectRemoveVariable box = saved(new int[] {WiredValueVariable.TARGET_GLOBAL}, "42");
        WiredContext ctx = mock(WiredContext.class);
        when(ctx.room()).thenReturn(room);

        box.execute(ctx);

        Mockito.verify(userVariables, never()).removeVariable(anyInt(), anyInt());
        Mockito.verify(furniVariables, never()).removeVariable(anyInt(), anyInt());
        Mockito.verifyNoInteractions(roomVariables);
        assertTrue(box.getWiredData().contains("\"targetType\":3"));
    }

    @Test
    void handItemIsRemovedFromAnAvatarCarryingOne() throws Exception {
        WiredEffectRemoveVariable box = saved(new int[] {WiredValueVariable.TARGET_USER}, "internal:@handitem");
        RoomUnit carrying = mock(RoomUnit.class);
        when(carrying.getHandItem()).thenReturn(5);
        RoomUnit empty = mock(RoomUnit.class);
        WiredContext ctx = mock(WiredContext.class);
        when(ctx.room()).thenReturn(room);

        try (MockedStatic<WiredSourceUtil> sources = mockStatic(WiredSourceUtil.class, Mockito.CALLS_REAL_METHODS);
                MockedStatic<WiredInternalVariableSupport> internal =
                        mockStatic(WiredInternalVariableSupport.class, Mockito.CALLS_REAL_METHODS)) {
            sources.when(() -> WiredSourceUtil.resolveUsers(any(), anyInt())).thenReturn(List.of(carrying, empty));
            internal.when(() -> WiredInternalVariableSupport.writeUserValue(any(), any(), anyString(), anyInt()))
                    .thenReturn(true);

            box.execute(ctx);

            internal.verify(() -> WiredInternalVariableSupport.writeUserValue(room, carrying, "@handitem_id", 0));
            internal.verify(
                    () -> WiredInternalVariableSupport.writeUserValue(eq(room), eq(empty), anyString(), anyInt()),
                    never());
        }
    }

    @Test
    void canMoveFreelyIsRemovedFromTheFurniOnly() throws Exception {
        WiredEffectRemoveVariable box = saved(new int[] {WiredValueVariable.TARGET_FURNI}, "internal:@can_move_freely");
        RoomWiredRuntime runtime = mock(RoomWiredRuntime.class);
        when(room.getWiredRuntime()).thenReturn(runtime);
        HabboItem tile = mock(HabboItem.class);
        WiredContext ctx = mock(WiredContext.class);
        when(ctx.room()).thenReturn(room);

        try (MockedStatic<WiredSourceUtil> sources = mockStatic(WiredSourceUtil.class, Mockito.CALLS_REAL_METHODS)) {
            sources.when(() -> WiredSourceUtil.resolveItems(any(), anyInt(), any()))
                    .thenReturn(List.of(tile));
            box.execute(ctx);
        }

        Mockito.verify(runtime).setMovesFreely(tile, false);
    }

    @Test
    void canMoveFreelyCannotBeSavedOnAUser() {
        org.junit.jupiter.api.Assertions.assertThrows(
                com.eu.habbo.messages.incoming.wired.WiredSaveException.class,
                () -> saved(new int[] {WiredValueVariable.TARGET_USER}, "internal:@can_move_freely"));
    }
}
