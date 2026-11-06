package polaris.hotel.items.interactions.wired.effects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import static polaris.hotel.items.interactions.wired.effects.WiredEffectTestFixtures.base;
import static polaris.hotel.items.interactions.wired.effects.WiredEffectTestFixtures.installHotel;
import static polaris.hotel.items.interactions.wired.effects.WiredEffectTestFixtures.json;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import polaris.Emulator;
import polaris.hotel.items.interactions.games.InteractionGameUpCounter;
import polaris.hotel.items.interactions.wired.WiredSettings;
import polaris.hotel.rooms.Room;
import polaris.hotel.wired.core.WiredSourceUtil;

/**
 * "Adjust clock" validated the picked counters and stored them, then with the dropdown left on
 * "triggering furni" resolved the trigger item at run time and adjusted nothing.
 */
class WiredEffectAdjustClockTest {

    @Test
    void pickedClocksPromoteTheTriggerSourceToSelected() throws Exception {
        Room room = mock(Room.class);
        InteractionGameUpCounter clock = mock(InteractionGameUpCounter.class);
        when(clock.getId()).thenReturn(601);
        when(room.getUserItem(601)).thenReturn(clock);
        WiredEffectAdjustClock box = new WiredEffectAdjustClock(1, 1, base(), "", 0, 0);

        try (MockedStatic<Emulator> emulator = mockStatic(Emulator.class)) {
            installHotel(emulator, room);

            box.saveData(
                    new WiredSettings(new int[] {2, WiredSourceUtil.SOURCE_TRIGGER, 1, 0}, "", new int[] {601}, 0),
                    null);
        }

        assertEquals(
                WiredSourceUtil.SOURCE_SELECTED, json(box).get("furniSource").getAsInt());
        assertEquals(601, json(box).getAsJsonArray("itemIds").get(0).getAsInt());
    }
}
