package polaris.messages.outgoing.guilds;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class GroupDetailsChangedComposer extends MessageComposer {
    private final int guildId;

    public GroupDetailsChangedComposer(int guildId) {
        this.guildId = guildId;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.GroupDetailsChangedComposer);
        this.response.appendInt(this.guildId);
        return this.response;
    }

    public int getGuildId() {
        return guildId;
    }
}
