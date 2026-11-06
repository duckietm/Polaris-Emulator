package polaris.plugin.events.bots;

import polaris.hotel.bots.Bot;

public class BotShoutEvent extends BotChatEvent {

    public BotShoutEvent(Bot bot, String message) {
        super(bot, message);
    }
}
