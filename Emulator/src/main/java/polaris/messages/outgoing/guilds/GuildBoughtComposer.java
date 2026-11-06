package polaris.messages.outgoing.guilds;

import polaris.hotel.guilds.Guild;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class GuildBoughtComposer extends MessageComposer {
    private final Guild guild;

    public GuildBoughtComposer(Guild guild) {
        this.guild = guild;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.GuildBoughtComposer);
        this.response.appendInt(this.guild.getRoomId());
        this.response.appendInt(this.guild.getId());
        return this.response;
    }

    public Guild getGuild() {
        return guild;
    }
}
