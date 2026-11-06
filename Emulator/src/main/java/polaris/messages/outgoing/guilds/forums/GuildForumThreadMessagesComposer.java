package polaris.messages.outgoing.guilds.forums;

import polaris.hotel.guilds.forums.ForumThread;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class GuildForumThreadMessagesComposer extends MessageComposer {
    public final ForumThread thread;

    public GuildForumThreadMessagesComposer(ForumThread thread) {
        this.thread = thread;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.GuildForumThreadMessagesComposer);
        this.response.appendInt(this.thread.getGuildId());
        this.thread.serialize(this.response);
        return this.response;
    }

    public ForumThread getThread() {
        return thread;
    }
}
