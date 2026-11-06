package polaris.plugin.events.guilds;

import polaris.hotel.guilds.Guild;

public class GuildChangedColorsEvent extends GuildEvent {

    public int colorOne;

    public int colorTwo;

    public GuildChangedColorsEvent(Guild guild, int colorOne, int colorTwo) {
        super(guild);

        this.colorOne = colorOne;
        this.colorTwo = colorTwo;
    }
}
