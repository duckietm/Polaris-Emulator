package polaris.plugin.events.bots;

import polaris.hotel.bots.Bot;
import polaris.hotel.users.User;

public class BotPickUpEvent extends BotEvent {

    public final User picker;

    public BotPickUpEvent(Bot bot, User picker) {
        super(bot);

        this.picker = picker;
    }
}
