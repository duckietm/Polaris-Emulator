package polaris.plugin.events.bots;

import polaris.hotel.bots.Bot;

public class BotSavedNameEvent extends BotEvent {

    public String name;

    public BotSavedNameEvent(Bot bot, String name) {
        super(bot);

        this.name = name;
    }
}
