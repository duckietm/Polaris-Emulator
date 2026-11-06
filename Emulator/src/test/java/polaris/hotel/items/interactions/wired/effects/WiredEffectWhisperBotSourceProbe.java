package polaris.hotel.items.interactions.wired.effects;

import java.util.List;
import polaris.hotel.bots.Bot;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomUnit;

/** Test access to the package-private bot source lookup of Show Message. */
public final class WiredEffectWhisperBotSourceProbe {
    private WiredEffectWhisperBotSourceProbe() {}

    public static Bot sourceBot(Room room, List<RoomUnit> sourceUsers) {
        return WiredEffectWhisper.sourceBot(room, sourceUsers);
    }
}
