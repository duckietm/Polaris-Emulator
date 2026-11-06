package polaris.plugin.events.guilds;

import polaris.hotel.guilds.Guild;

public class GuildChangedBadgeEvent extends GuildEvent {

    public String badge;

    public GuildChangedBadgeEvent(Guild guild, String badge) {
        super(guild);

        this.badge = badge;
    }
}
