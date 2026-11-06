package polaris.messages.outgoing.guilds.forums;

import java.util.Collection;
import polaris.hotel.guilds.forums.ForumThreadComment;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class GuildForumCommentsComposer extends MessageComposer {
    private final int guildId;
    private final int threadId;
    private final int index;
    private final Collection<ForumThreadComment> guildForumCommentList;

    public GuildForumCommentsComposer(
            int guildId, int threadId, int index, Collection<ForumThreadComment> guildForumCommentList) {
        this.guildId = guildId;
        this.threadId = threadId;
        this.index = index;
        this.guildForumCommentList = guildForumCommentList;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.GuildForumCommentsComposer);

        this.response.appendInt(this.guildId); // guild_id
        this.response.appendInt(this.threadId); // thread_id
        this.response.appendInt(this.index); // start_index
        this.response.appendInt(this.guildForumCommentList.size());

        for (ForumThreadComment comment : this.guildForumCommentList) {
            comment.serialize(this.response);
        }
        return this.response;
    }

    public int getGuildId() {
        return guildId;
    }

    public int getThreadId() {
        return threadId;
    }

    public int getIndex() {
        return index;
    }

    public Collection<ForumThreadComment> getGuildForumCommentList() {
        return guildForumCommentList;
    }
}
