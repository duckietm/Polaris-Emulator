package polaris.plugin.events.bots;

import polaris.hotel.bots.Bot;
import polaris.hotel.users.UserGender;

public class BotSavedLookEvent extends BotEvent {

    public UserGender gender;

    public String newLook;

    public int effect;

    public BotSavedLookEvent(Bot bot, UserGender gender, String newLook, int effect) {
        super(bot);

        this.gender = gender;
        this.newLook = newLook;
        this.effect = effect;
    }
}
