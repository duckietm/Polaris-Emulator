package polaris.hotel.items.interactions.wired.effects;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static polaris.hotel.items.interactions.wired.effects.WiredEffectTestFixtures.base;
import static polaris.hotel.items.interactions.wired.effects.WiredEffectTestFixtures.context;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import polaris.Emulator;
import polaris.core.ConfigurationManager;
import polaris.hotel.bots.Bot;
import polaris.hotel.items.interactions.wired.WiredSettings;
import polaris.hotel.rooms.Room;

/** AIR saves Bot Clothes with no figure ("name\t"); it is stored empty and dresses nobody. */
class WiredEffectBotClothesEmptyFigureTest {
    @Test
    void aSaveWithNoFigureIsKept() throws Exception {
        ConfigurationManager config = mock(ConfigurationManager.class);
        when(config.getInt(anyString(), anyInt())).thenAnswer(invocation -> invocation.getArgument(1));
        WiredEffectBotClothes box = new WiredEffectBotClothes(1, 1, base(), "", 0, 0);

        try (MockedStatic<Emulator> emulator = mockStatic(Emulator.class)) {
            emulator.when(Emulator::getConfig).thenReturn(config);
            assertTrue(box.saveData(new WiredSettings(new int[0], "Robbie\t", new int[0], 0), null));
            assertTrue(box.saveData(new WiredSettings(new int[0], "\t", new int[0], 0), null));
        }

        assertTrue(box.getWiredData().contains("\"\""), box.getWiredData());
    }

    /** The new look is saved with the bot: it used to rewrite, for good, the bots visitors had placed. */
    @Test
    void onlyTheRoomOwnersBotsAreDressed() throws Exception {
        ConfigurationManager config = mock(ConfigurationManager.class);
        when(config.getInt(anyString(), anyInt())).thenAnswer(invocation -> invocation.getArgument(1));
        WiredEffectBotClothes box = new WiredEffectBotClothes(1, 1, base(), "", 0, 0);
        String look = "hd-180-1.ch-210-66.lg-270-82";

        Room room = mock(Room.class);
        when(room.getOwnerId()).thenReturn(7);
        Bot ownersBot = mock(Bot.class);
        when(ownersBot.getOwnerId()).thenReturn(7);
        Bot visitorsBot = mock(Bot.class);
        when(visitorsBot.getOwnerId()).thenReturn(8);
        when(room.getBots("Robbie")).thenReturn(List.of(ownersBot, visitorsBot));

        try (MockedStatic<Emulator> emulator = mockStatic(Emulator.class)) {
            emulator.when(Emulator::getConfig).thenReturn(config);
            assertTrue(box.saveData(new WiredSettings(new int[0], "Robbie\t" + look, new int[0], 0), null));
            box.execute(context(room));
        }

        verify(ownersBot).setFigure(look);
        verify(visitorsBot, never()).setFigure(anyString());
    }
}
