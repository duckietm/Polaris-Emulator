package polaris.messages.outgoing.guilds.forums;

import polaris.hotel.guilds.forums.ForumThreadComment;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class PostUpdateMessageComposer extends MessageComposer {
    public final int guildId;
    public final int threadId;
    public final ForumThreadComment comment;

    public PostUpdateMessageComposer(int guildId, int threadId, ForumThreadComment comment) {
        this.guildId = guildId;
        this.threadId = threadId;
        this.comment = comment;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.PostUpdateMessageComposer);

        this.response.appendInt(this.guildId); // guild_id
        this.response.appendInt(this.threadId); // thread_id
        this.comment.serialize(this.response); // Comment

        return this.response;
    }

    public int getGuildId() {
        return guildId;
    }

    public int getThreadId() {
        return threadId;
    }

    public ForumThreadComment getComment() {
        return comment;
    }
}
