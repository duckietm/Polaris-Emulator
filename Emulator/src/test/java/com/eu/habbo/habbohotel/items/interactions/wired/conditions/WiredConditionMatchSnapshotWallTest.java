package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.eu.habbo.Emulator;
import com.eu.habbo.core.ConfigurationManager;
import com.eu.habbo.habbohotel.GameEnvironment;
import com.eu.habbo.habbohotel.items.FurnitureType;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredEvent;
import com.eu.habbo.habbohotel.wired.core.WiredServices;
import com.eu.habbo.habbohotel.wired.core.WiredState;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.MockedStatic;

/** The snapshot conditions take wall furni: state, wall position and altitude, and no direction. */
class WiredConditionMatchSnapshotWallTest {

    @Test
    void aWallItemMatchesOnItsWallPositionAndIgnoresDirection() {
        Room room = mock(Room.class);
        HabboItem torch = mock(HabboItem.class);
        Item base = mock(Item.class);
        when(base.getType()).thenReturn(FurnitureType.WALL);
        when(torch.getBaseItem()).thenReturn(base);
        when(torch.getId()).thenReturn(55);
        when(torch.getExtradata()).thenReturn("1");
        AtomicReference<String> wall = new AtomicReference<>(":w=2,3 l=10,20 r");
        when(torch.getWallPosition()).thenAnswer(invocation -> wall.get());
        when(torch.getRotation()).thenReturn(0);
        when(room.getHabboItem(55)).thenReturn(torch);
        GameEnvironment environment = mock(GameEnvironment.class, Answers.RETURNS_DEEP_STUBS);
        when(environment.getRoomManager().getRoom(anyInt())).thenReturn(room);
        ConfigurationManager config = mock(ConfigurationManager.class);
        when(config.getInt(anyString())).thenReturn(20);
        when(config.getInt(anyString(), anyInt())).thenAnswer(invocation -> invocation.getArgument(1));

        WiredConditionMatchStatePosition condition =
                new WiredConditionMatchStatePosition(1, 1, mock(Item.class), "", 0, 0);
        WiredContext ctx = new WiredContext(
                WiredEvent.builder(WiredEvent.Type.CUSTOM, room).build(),
                null,
                mock(WiredServices.class),
                new WiredState(20));

        try (MockedStatic<Emulator> emulator = mockStatic(Emulator.class)) {
            emulator.when(Emulator::getGameEnvironment).thenReturn(environment);
            emulator.when(Emulator::getConfig).thenReturn(config);
            // state, direction, position, altitude, picked furni
            condition.saveData(new WiredSettings(new int[] {1, 1, 1, 0, 100}, "", new int[] {55}, 0));

            when(torch.getRotation()).thenReturn(4); // a wall item has no direction
            assertTrue(condition.evaluate(ctx));

            wall.set(":w=2,4 l=10,20 r");
            assertFalse(condition.evaluate(ctx));
        }
    }
}
