package polaris.messages.outgoing.guilds.forums;

import polaris.hotel.guilds.forums.ForumThreadComment;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class GuildForumAddCommentComposer extends MessageComposer {
    private final ForumThreadComment comment;

    public GuildForumAddCommentComposer(ForumThreadComment comment) {
        this.comment = comment;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.GuildForumAddCommentComposer);
        this.response.appendInt(this.comment.getThread().getGuildId()); // guild_id
        this.response.appendInt(this.comment.getThreadId()); // thread_id
        this.comment.serialize(this.response); // Comment
        return this.response;
    }

    public ForumThreadComment getComment() {
        return comment;
    }
}
