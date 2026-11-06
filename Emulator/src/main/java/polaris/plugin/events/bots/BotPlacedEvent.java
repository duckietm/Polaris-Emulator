package polaris.plugin.events.bots;

import polaris.hotel.bots.Bot;
import polaris.hotel.rooms.RoomTile;
import polaris.hotel.users.User;

public class BotPlacedEvent extends BotEvent {

    public final RoomTile location;

    public final User placer;

    public BotPlacedEvent(Bot bot, RoomTile location, User placer) {
        super(bot);

        this.location = location;
        this.placer = placer;
    }
}
