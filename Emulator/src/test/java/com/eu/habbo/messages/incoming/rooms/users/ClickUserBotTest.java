package com.eu.habbo.messages.incoming.rooms.users;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.bots.Bot;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectWhisperBotSourceProbe;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import java.util.List;
import org.junit.jupiter.api.Test;

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
