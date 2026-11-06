package polaris.plugin.events.guilds;

import polaris.hotel.guilds.Guild;
import polaris.hotel.users.User;

public class GuildAcceptedMembershipEvent extends GuildEvent {

    public final int userId;

    public final User user;

    public GuildAcceptedMembershipEvent(Guild guild, int userId, User user) {
        super(guild);

        this.userId = userId;
        this.user = user;
    }
}
