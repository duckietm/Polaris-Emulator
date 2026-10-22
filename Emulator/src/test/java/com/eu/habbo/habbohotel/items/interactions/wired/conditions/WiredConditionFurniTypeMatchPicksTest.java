package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

import static com.eu.habbo.habbohotel.items.interactions.wired.conditions.WiredConditionTestSupport.boxBase;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.eu.habbo.Emulator;
import com.eu.habbo.core.ConfigurationManager;
import com.eu.habbo.habbohotel.GameEnvironment;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.HabboItem;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.MockedStatic;

/**
 * AIR's StuffTypeMatches: the furni checked are the picks (stuffIds, source 100) and the furni whose types they
 * must have are the other picks (stuffIds2, source 101), both sources in the source slots and no int params. A
 * save from an older client (the type source as its int, the checked furni in the string) still loads the same.
 */
class WiredConditionFurniTypeMatchPicksTest {
    @Test
    void theOfficialSaveKeepsBothPickListsAndSources() {
        WiredConditionFurniTypeMatch box = new WiredConditionFurniTypeMatch(1, 1, boxBase(), "", 0, 0);
        WiredSettings settings = new WiredSettings(new int[0], "", new int[] {5}, -1);
        settings.setFurniSourceTypes(new int[] {100, 101});
        settings.setFurniIds2(new int[] {9});

        withRoom(() -> assertTrue(box.saveData(settings)));

        assertArrayEquals(new int[] {100, 101}, box.officialFurniSources());
        assertArrayEquals(new int[] {9}, box.officialFurniIds2());
        assertTrue(box.getWiredData().contains("\"primaryItemIds\":[5]"));
    }

    @Test
    void anOlderSaveLandsOnTheSameLists() {
        WiredConditionFurniTypeMatch box = new WiredConditionFurniTypeMatch(1, 1, boxBase(), "", 0, 0);
        WiredSettings settings = new WiredSettings(new int[] {101}, "5", new int[] {9}, -1);
        settings.setFurniSourceTypes(new int[] {100});

        withRoom(() -> assertTrue(box.saveData(settings)));

        assertArrayEquals(new int[] {100, 101}, box.officialFurniSources());
        assertArrayEquals(new int[] {9}, box.officialFurniIds2());
        assertTrue(box.getWiredData().contains("\"primaryItemIds\":[5]"));
    }

    private static void withRoom(Runnable body) {
        ConfigurationManager config = mock(ConfigurationManager.class);
        when(config.getInt(anyString())).thenReturn(20);
        Room room = mock(Room.class);
        when(room.getHabboItem(anyInt())).thenAnswer(call -> item(call.getArgument(0)));
        GameEnvironment environment = mock(GameEnvironment.class, Answers.RETURNS_DEEP_STUBS);
        when(environment.getRoomManager().getRoom(anyInt())).thenReturn(room);

        try (MockedStatic<Emulator> emulator = mockStatic(Emulator.class)) {
            emulator.when(Emulator::getConfig).thenReturn(config);
            emulator.when(Emulator::getGameEnvironment).thenReturn(environment);
            body.run();
        }
    }

    private static HabboItem item(int id) {
        HabboItem item = mock(HabboItem.class);
        when(item.getId()).thenReturn(id);
        return item;
    }
}
