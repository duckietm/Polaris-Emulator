package polaris.plugin.events.guilds;

import polaris.hotel.guilds.Guild;

public class GuildChangedNameEvent extends GuildEvent {

    public String name;

    public String description;

    public GuildChangedNameEvent(Guild guild, String name, String description) {
        super(guild);
        this.name = name;
        this.description = description;
    }
}
