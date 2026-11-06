package polaris.plugin.events.guilds;

import polaris.hotel.guilds.Guild;
import polaris.hotel.users.User;

public class GuildRemovedMemberEvent extends GuildEvent {

    public final int userId;

    public final User guildMember;

    public GuildRemovedMemberEvent(Guild guild, int userId, User guildMember) {
        super(guild);
        this.guildMember = guildMember;
        this.userId = userId;
    }
}
