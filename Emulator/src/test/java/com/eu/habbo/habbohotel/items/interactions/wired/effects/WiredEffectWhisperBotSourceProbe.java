package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.habbohotel.bots.Bot;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import java.util.List;

/** Test access to the package-private bot source lookup of Show Message. */
public final class WiredEffectWhisperBotSourceProbe {
    private WiredEffectWhisperBotSourceProbe() {}

    public static Bot sourceBot(Room room, List<RoomUnit> sourceUsers) {
        return WiredEffectWhisper.sourceBot(room, sourceUsers);
    }
}
