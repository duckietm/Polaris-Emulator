package polaris.messages.incoming.rooms.users;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import polaris.hotel.bots.Bot;
import polaris.hotel.items.interactions.wired.effects.WiredEffectWhisperBotSourceProbe;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomUnit;

/** Click User also fires for bots, and a Show Message to everyone with a bot as source is said by that bot. */
class ClickUserBotTest {

    @Test
    void aClickedBotIsTheClickedUnit() {
        Room room = mock(Room.class);
        Bot bot = mock(Bot.class);
        RoomUnit botUnit = mock(RoomUnit.class);
        when(bot.getRoomUnit()).thenReturn(botUnit);
        when(room.getBotByRoomUnitId(9)).thenReturn(bot);

        assertSame(botUnit, ClickUserEvent.clickedUnit(room, 9));
        assertNull(ClickUserEvent.clickedUnit(room, 10));
    }

    @Test
    void aBotSourceSpeaksTheMessage() {
        Room room = mock(Room.class);
        Bot bot = mock(Bot.class);
        RoomUnit botUnit = mock(RoomUnit.class);
        when(room.getBot(botUnit)).thenReturn(bot);

        assertSame(bot, WiredEffectWhisperBotSourceProbe.sourceBot(room, List.of(botUnit)));
    }
}
