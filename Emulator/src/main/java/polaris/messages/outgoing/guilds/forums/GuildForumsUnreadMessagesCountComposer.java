package polaris.messages.outgoing.guilds.forums;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class GuildForumsUnreadMessagesCountComposer extends MessageComposer {
    public final int count;

    public GuildForumsUnreadMessagesCountComposer(int count) {
        this.count = count;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.GuildForumsUnreadMessagesCountComposer);
        this.response.appendInt(this.count);
        return this.response;
    }

    public int getCount() {
        return count;
    }
}
