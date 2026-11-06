package polaris.plugin.events.guilds;

import polaris.hotel.guilds.Guild;
import polaris.hotel.users.User;

public class GuildDeclinedMembershipEvent extends GuildEvent {

    public final int userId;

    public final User user;

    public final User admin;

    public GuildDeclinedMembershipEvent(Guild guild, int userId, User user, User admin) {
        super(guild);

        this.userId = userId;
        this.user = user;
        this.admin = admin;
    }
}
