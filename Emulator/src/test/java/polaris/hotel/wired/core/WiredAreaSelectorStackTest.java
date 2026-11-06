package polaris.hotel.wired.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.MockedStatic;
import polaris.Emulator;
import polaris.core.ConfigurationManager;
import polaris.hotel.GameEnvironment;
import polaris.hotel.items.FurnitureType;
import polaris.hotel.items.Item;
import polaris.hotel.items.interactions.wired.WiredSettings;
import polaris.hotel.items.interactions.wired.selector.WiredEffectFurniArea;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.UserItem;
import polaris.hotel.wired.api.IWiredEffect;
import polaris.hotel.wired.api.IWiredTrigger;
import polaris.hotel.wired.api.WiredStack;

/** "Says something" + "furni in area" selector + an action whose furni source is the selector. */
class WiredAreaSelectorStackTest {

    @Test
    void anActionOnTheSelectorSourceGetsTheFurniInTheArea() throws Exception {
        Room room = mock(Room.class, Answers.RETURNS_DEEP_STUBS);
        when(room.getId()).thenReturn(5);
        when(room.isLoaded()).thenReturn(true);

        UserItem inside = mock(UserItem.class);
        when(inside.getId()).thenReturn(901);
        when(inside.getRoomId()).thenReturn(5);
        UserItem outside = mock(UserItem.class);
        when(outside.getId()).thenReturn(902);
        when(outside.getRoomId()).thenReturn(5);
        when(room.getItemsAt(anyInt(), anyInt())).thenReturn(Set.of());
        when(room.getItemsAt(2, 2)).thenReturn(Set.of(inside));
        when(room.getItemsAt(8, 8)).thenReturn(Set.of(outside));
        when(room.getFloorItems()).thenReturn(Set.of(inside, outside));

        Item base = mock(Item.class);
        when(base.getType()).thenReturn(FurnitureType.FLOOR);
        WiredEffectFurniArea area = new WiredEffectFurniArea(3, 1, base, "", 0, 0);
        area.setRoomId(5);

        GameEnvironment environment = mock(GameEnvironment.class, Answers.RETURNS_DEEP_STUBS);
        when(environment.getRoomManager().getRoom(5)).thenReturn(room);
        ConfigurationManager config = mock(ConfigurationManager.class);
        when(config.getInt(anyString())).thenReturn(5);
        when(config.getInt(anyString(), anyInt())).thenAnswer(invocation -> invocation.getArgument(1));

        List<Integer> toggled = new ArrayList<>();
        try (MockedStatic<Emulator> emulator = mockStatic(Emulator.class)) {
            emulator.when(Emulator::getGameEnvironment).thenReturn(environment);
            emulator.when(Emulator::getConfig).thenReturn(config);

            assertTrue(area.saveData(new WiredSettings(new int[] {1, 1, 3, 3, 0, 0}, "", new int[0], -1), null));

            IWiredTrigger says = new IWiredTrigger() {
                @Override
                public WiredEvent.Type listensTo() {
                    return WiredEvent.Type.CUSTOM;
                }

                @Override
                public boolean matches(UserItem triggerItem, WiredEvent event) {
                    return true;
                }
            };
            IWiredEffect toggle =
                    context -> WiredSourceUtil.resolveItems(context, WiredSourceUtil.SOURCE_SELECTOR, List.of())
                            .forEach(item -> toggled.add(item.getId()));
            WiredStack stack = new WiredStack(mock(UserItem.class), says, List.of(), List.of(area, toggle));
            WiredEngine engine = new WiredEngine(
                    mock(WiredServices.class),
                    (candidate, type) -> candidate == room ? List.of(stack) : List.of(),
                    100);

            engine.handleEvent(WiredEvent.builder(WiredEvent.Type.CUSTOM, room)
                    .createdAtMs(System.currentTimeMillis())
                    .build());
        }

        assertEquals(List.of(901), toggled);
    }
}
