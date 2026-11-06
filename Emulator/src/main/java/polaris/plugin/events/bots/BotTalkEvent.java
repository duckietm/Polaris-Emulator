package polaris.plugin.events.bots;

import polaris.hotel.bots.Bot;

public class BotTalkEvent extends BotChatEvent {

    public BotTalkEvent(Bot bot, String message) {
        super(bot, message);
    }
}
