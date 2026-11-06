package polaris.messages.outgoing.guilds;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class GuildConfirmRemoveMemberComposer extends MessageComposer {
    private int userId;
    private int furniCount;

    public GuildConfirmRemoveMemberComposer(int userId, int furniCount) {
        this.userId = userId;
        this.furniCount = furniCount;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.GuildConfirmRemoveMemberComposer);
        this.response.appendInt(this.userId);
        this.response.appendInt(this.furniCount);
        return this.response;
    }

    public int getUserId() {
        return userId;
    }

    public int getFurniCount() {
        return furniCount;
    }
}
