package polaris.plugin.events.bots;

import polaris.hotel.bots.Bot;
import polaris.hotel.users.User;

public class BotServerItemEvent extends BotEvent {

    public User user;

    public int itemId;

    public BotServerItemEvent(Bot bot, User user, int itemId) {
        super(bot);

        this.user = user;
        this.itemId = itemId;
    }
}
