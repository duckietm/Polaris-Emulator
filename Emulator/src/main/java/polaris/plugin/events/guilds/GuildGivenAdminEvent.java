package polaris.plugin.events.guilds;

import polaris.hotel.guilds.Guild;
import polaris.hotel.users.User;

public class GuildGivenAdminEvent extends GuildEvent {

    public final int userId;

    public final User user;

    public final User admin;

    public GuildGivenAdminEvent(Guild guild, int userId, User user, User admin) {
        super(guild);
        this.userId = userId;
        this.user = user;
        this.admin = admin;
    }
}
