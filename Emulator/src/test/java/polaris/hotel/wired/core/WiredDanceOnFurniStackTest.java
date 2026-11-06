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
import polaris.hotel.items.interactions.wired.conditions.WiredConditionTriggerOnFurni;
import polaris.hotel.items.interactions.wired.triggers.WiredTriggerUserPerformsAction;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomTile;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.rooms.RoomUnitType;
import polaris.hotel.users.UserItem;
import polaris.hotel.wired.WiredUserActionType;
import polaris.hotel.wired.api.IWiredEffect;
import polaris.hotel.wired.api.WiredStack;
import polaris.hotel.wired.migrate.WiredEvents;

/** "User performs action: dance" + "triggerer is on furni" + an action, as a player builds it. */
class WiredDanceOnFurniStackTest {

    @Test
    void dancingOnThePickedFurniRunsTheAction() throws Exception {
        Room room = mock(Room.class);
        when(room.getId()).thenReturn(5);
        when(room.isLoaded()).thenReturn(true);

        UserItem rug = mock(UserItem.class);
        when(rug.getId()).thenReturn(900);
        when(rug.getRoomId()).thenReturn(5);
        when(room.getUserItem(900)).thenReturn(rug);

        RoomTile tile = mock(RoomTile.class);
        when(room.getItemsAt(tile)).thenReturn(Set.of(rug));

        RoomUnit dancer = mock(RoomUnit.class);
        when(dancer.getId()).thenReturn(1);
        when(dancer.getRoomUnitType()).thenReturn(RoomUnitType.USER);
        when(dancer.getCurrentLocation()).thenReturn(tile);

        Item base = mock(Item.class);
        when(base.getType()).thenReturn(FurnitureType.FLOOR);

        WiredTriggerUserPerformsAction trigger = new WiredTriggerUserPerformsAction(1, 1, base, "", 0, 0);
        WiredConditionTriggerOnFurni condition = new WiredConditionTriggerOnFurni(2, 1, base, "", 0, 0);
        condition.setRoomId(5);

        GameEnvironment environment = mock(GameEnvironment.class, Answers.RETURNS_DEEP_STUBS);
        when(environment.getRoomManager().getRoom(5)).thenReturn(room);
        ConfigurationManager config = mock(ConfigurationManager.class);
        when(config.getInt(anyString())).thenReturn(5);
        when(config.getInt(anyString(), anyInt())).thenAnswer(invocation -> invocation.getArgument(1));

        try (MockedStatic<Emulator> emulator = mockStatic(Emulator.class)) {
            emulator.when(Emulator::getGameEnvironment).thenReturn(environment);
            emulator.when(Emulator::getConfig).thenReturn(config);

            // What the editors send: dance, no filters; the rug picked, user source "trigger".
            assertTrue(trigger.saveData(
                    new WiredSettings(new int[] {WiredUserActionType.DANCE, 0, 0, 0, 1}, "", new int[0], -1)));
            WiredSettings onRug = new WiredSettings(new int[] {0}, "", new int[] {900}, -1);
            onRug.setFurniSourceTypes(new int[] {WiredSourceUtil.SOURCE_SELECTED});
            onRug.setUserSourceTypes(new int[] {WiredSourceUtil.SOURCE_TRIGGER});
            assertTrue(condition.saveData(onRug));
        }

        List<String> calls = new ArrayList<>();
        IWiredEffect lamp = context -> calls.add("toggle");
        WiredStack stack = new WiredStack(mock(UserItem.class), trigger, List.of(condition), List.of(lamp));
        WiredEngine engine = new WiredEngine(
                mock(WiredServices.class), (candidate, type) -> candidate == room ? List.of(stack) : List.of(), 100);

        engine.handleEvent(WiredEvents.userPerformsAction(room, dancer, WiredUserActionType.DANCE, 1));

        assertEquals(List.of("toggle"), calls);
    }
}
