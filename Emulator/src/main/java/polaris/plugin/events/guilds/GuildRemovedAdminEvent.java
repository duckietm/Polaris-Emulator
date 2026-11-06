package polaris.plugin.events.guilds;

import polaris.hotel.guilds.Guild;
import polaris.hotel.users.User;

public class GuildRemovedAdminEvent extends GuildEvent {

    public final int userId;

    public final User admin;

    public GuildRemovedAdminEvent(Guild guild, int userId, User admin) {
        super(guild);
        this.userId = userId;
        this.admin = admin;
    }
}
