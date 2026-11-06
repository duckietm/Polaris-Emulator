package polaris.plugin.events.guilds.forums;

import polaris.hotel.guilds.forums.ForumThreadComment;
import polaris.plugin.Event;

public class GuildForumThreadCommentCreated extends Event {
    public final ForumThreadComment comment;

    public GuildForumThreadCommentCreated(ForumThreadComment comment) {
        this.comment = comment;
    }
}
