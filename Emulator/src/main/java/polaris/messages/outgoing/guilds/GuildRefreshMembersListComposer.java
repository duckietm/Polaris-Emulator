package polaris.messages.outgoing.guilds;

import polaris.hotel.guilds.Guild;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class GuildRefreshMembersListComposer extends MessageComposer {
    private final Guild guild;

    public GuildRefreshMembersListComposer(Guild guild) {
        this.guild = guild;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.GuildRefreshMembersListComposer);
        this.response.appendInt(this.guild.getId());
        this.response.appendInt(0);
        return this.response;
    }

    public Guild getGuild() {
        return guild;
    }
}
