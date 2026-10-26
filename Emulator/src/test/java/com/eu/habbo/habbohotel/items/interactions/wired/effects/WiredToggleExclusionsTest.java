package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import static com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectTestFixtures.base;
import static com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectTestFixtures.context;
import static com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectTestFixtures.row;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionSwitch;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomLayout;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomTileState;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class WiredToggleExclusionsTest {

    @Test
    void toggleStateFlashesAPickedWiredBoxAndKeepsIt() throws Exception {
        Room room = mock(Room.class);
        InteractionWiredEffect box = mock(InteractionWiredEffect.class);
        when(box.getId()).thenReturn(501);
        when(box.getRoomId()).thenReturn(1);
        when(room.getHabboItem(501)).thenReturn(box);
        WiredEffectToggleFurni toggle = toggle(room);

        toggle.execute(context(room));

        verify(box).flashBox(room);
        verify(box, never()).setExtradata(anyString());
        assertTrue(toggle.getWiredData().contains("501"));
    }

    @Test
    void toggleStateLeavesAIRsGameFurniAlone() throws Exception {
        for (String name : WiredEffectToggleFurni.UNTOUCHED_NAMES) {
            Room room = mock(Room.class);
            HabboItem item = furni(room, name, 4, "0", HabboItem.class);
            WiredEffectToggleFurni toggle = toggle(room);

            toggle.execute(context(room));

            verify(item, never()).setExtradata(anyString());
            assertTrue(toggle.getWiredData().contains("501"), name);
        }
    }

    @Test
    void toggleStateChangesAPlainFurni() throws Exception {
        Room room = mock(Room.class);
        HabboItem item = furni(room, "rare_lamp", 2, "0", HabboItem.class);
        WiredEffectToggleFurni toggle = toggle(room);

        try (MockedStatic<WiredManager> manager = mockStatic(WiredManager.class, CALLS_REAL_METHODS)) {
            manager.when(() -> WiredManager.triggerFurniStateUpdated(any(), any(), any(), anyBoolean()))
                    .thenAnswer(invocation -> null);
            toggle.execute(context(room));
        }

        verify(item).setExtradata("1");
    }

    @Test
    void aFurniWhoseHeightFollowsItsStateIsHeldByFurniStandingOnIt() {
        Room room = mock(Room.class);
        RoomLayout layout = mock(RoomLayout.class);
        when(room.getLayout()).thenReturn(layout);
        RoomTile tile = new RoomTile((short) 2, (short) 2, (short) 0, RoomTileState.OPEN, true);
        when(layout.getTile((short) 2, (short) 2)).thenReturn(tile);
        when(layout.getTilesAt(tile, 1, 1, 0)).thenReturn(Set.of(tile));
        HabboItem block = furni(room, "wf_block", 5, "0", HabboItem.class);
        when(block.getBaseItem().getMultiHeights()).thenReturn(new double[] {0, 1, 2, 3, 4});
        when(block.getBaseItem().getWidth()).thenReturn(1);
        when(block.getBaseItem().getLength()).thenReturn(1);
        when(block.getX()).thenReturn((short) 2);
        when(block.getY()).thenReturn((short) 2);
        HabboItem above = mock(HabboItem.class);
        when(above.getZ()).thenReturn(1.0);
        when(room.getItemsAt(tile)).thenReturn(Set.of(block));

        assertFalse(WiredEffectToggleFurni.isHeldByFurniOnTop(room, block));

        when(room.getItemsAt(tile)).thenReturn(Set.of(block, above));
        assertTrue(WiredEffectToggleFurni.isHeldByFurniOnTop(room, block));
    }

    @Test
    void toggleRandomSkipsAIRsGameFurniAndKeepsThemPicked() throws Exception {
        Room room = mock(Room.class);
        HabboItem pyramid = furni(room, "wf_pyramid", 2, "0", HabboItem.class);
        WiredEffectToggleRandom random = random(room);

        try (MockedStatic<Emulator> emulator = mockStatic(Emulator.class)) {
            emulator.when(Emulator::getRandom).thenReturn(new Random(1));
            random.execute(context(room));
        }

        verify(pyramid, never()).setExtradata(anyString());
        assertTrue(random.getWiredData().contains("501"));
    }

    @Test
    void toggleRandomRandomisesAFloorSwitch() throws Exception {
        Room room = mock(Room.class);
        HabboItem floorSwitch = furni(room, "wf_floor_switch1", 2, "0", InteractionSwitch.class);
        WiredEffectToggleRandom random = random(room);

        try (MockedStatic<Emulator> emulator = mockStatic(Emulator.class);
                MockedStatic<WiredManager> manager = mockStatic(WiredManager.class, CALLS_REAL_METHODS)) {
            emulator.when(Emulator::getRandom).thenReturn(new Highest());
            manager.when(() -> WiredManager.triggerFurniStateUpdated(any(), any(), any(), anyBoolean()))
                    .thenAnswer(invocation -> null);
            random.execute(context(room));
        }

        verify(floorSwitch).setExtradata("1");
    }

    @Test
    void toggleRandomWritesNothingWhenThePickIsTheCurrentState() throws Exception {
        Room room = mock(Room.class);
        HabboItem lamp = furni(room, "rare_lamp", 2, "1", HabboItem.class);
        WiredEffectToggleRandom random = random(room);

        try (MockedStatic<Emulator> emulator = mockStatic(Emulator.class)) {
            emulator.when(Emulator::getRandom).thenReturn(new Highest());
            random.execute(context(room));
        }

        verify(lamp, never()).setExtradata(anyString());
        verify(room, never()).updateItem(any());
    }

    private static final class Highest extends Random {
        @Override
        public int nextInt(int bound) {
            return bound - 1;
        }
    }

    private static <T extends HabboItem> T furni(Room room, String name, int states, String state, Class<T> type) {
        Item base = base();
        when(base.getName()).thenReturn(name);
        when(base.getStateCount()).thenReturn(states);
        T item = mock(type);
        when(item.getId()).thenReturn(501);
        when(item.getRoomId()).thenReturn(1);
        when(item.getBaseItem()).thenReturn(base);
        when(item.getExtradata()).thenReturn(state);
        when(room.getHabboItem(501)).thenReturn(item);
        return item;
    }

    private static WiredEffectToggleFurni toggle(Room room) throws Exception {
        WiredEffectToggleFurni toggle = new WiredEffectToggleFurni(1, 1, base(), "", 0, 0);
        toggle.loadWiredData(row("{\"delay\":0,\"itemIds\":[501],\"toggleType\":0,\"furniSource\":100}"), room);
        return toggle;
    }

    private static WiredEffectToggleRandom random(Room room) throws Exception {
        WiredEffectToggleRandom random = new WiredEffectToggleRandom(1, 1, base(), "", 0, 0);
        random.loadWiredData(row("{\"delay\":0,\"itemIds\":[501],\"furniSource\":100}"), room);
        return random;
    }
}
