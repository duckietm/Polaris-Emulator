package polaris.plugin.events.guilds;

import polaris.hotel.guilds.Guild;
import polaris.hotel.users.User;

public class GuildRemovedFavoriteEvent extends GuildEvent {

    public final User user;

    public GuildRemovedFavoriteEvent(Guild guild, User user) {
        super(guild);
        this.user = user;
    }
}
