package polaris.messages.outgoing.guilds;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class RemoveGuildFromRoomComposer extends MessageComposer {
    private int guildId;

    public RemoveGuildFromRoomComposer(int guildId) {
        this.guildId = guildId;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.RemoveGuildFromRoomComposer);
        this.response.appendInt(this.guildId);
        return this.response;
    }

    public int getGuildId() {
        return guildId;
    }
}
