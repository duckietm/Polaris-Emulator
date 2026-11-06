package polaris.plugin.events.bots;

import polaris.hotel.bots.Bot;
import polaris.plugin.Event;

public abstract class BotEvent extends Event {

    public final Bot bot;

    public BotEvent(Bot bot) {
        this.bot = bot;
    }
}
