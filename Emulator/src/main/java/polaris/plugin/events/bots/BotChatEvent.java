package polaris.plugin.events.bots;

import polaris.hotel.bots.Bot;

public abstract class BotChatEvent extends BotEvent {

    public String message;

    public BotChatEvent(Bot bot, String message) {
        super(bot);

        this.message = message;
    }
}
