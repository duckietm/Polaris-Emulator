package polaris.plugin.events.bots;

import polaris.hotel.bots.Bot;
import polaris.hotel.users.User;

public class BotWhisperEvent extends BotChatEvent {

    public User target;

    public BotWhisperEvent(Bot bot, String message, User target) {
        super(bot, message);

        this.target = target;
    }
}
