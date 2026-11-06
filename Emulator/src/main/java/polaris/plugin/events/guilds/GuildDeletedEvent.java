package polaris.plugin.events.guilds;

import polaris.hotel.guilds.Guild;
import polaris.hotel.users.User;

public class GuildDeletedEvent extends GuildEvent {

    public final User deleter;

    public GuildDeletedEvent(Guild guild, User deleter) {
        super(guild);

        this.deleter = deleter;
    }
}
