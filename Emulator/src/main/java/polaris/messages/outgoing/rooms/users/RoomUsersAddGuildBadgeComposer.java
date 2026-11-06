package polaris.messages.outgoing.rooms.users;

import polaris.hotel.guilds.Guild;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class RoomUsersAddGuildBadgeComposer extends MessageComposer {
    private final Guild guild;

    public RoomUsersAddGuildBadgeComposer(Guild guild) {
        this.guild = guild;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.RoomUsersGuildBadgesComposer);
        this.response.appendInt(1);
        this.response.appendInt(this.guild.getId());
        this.response.appendString(this.guild.getBadge());
        return this.response;
    }

    public Guild getGuild() {
        return guild;
    }
}
