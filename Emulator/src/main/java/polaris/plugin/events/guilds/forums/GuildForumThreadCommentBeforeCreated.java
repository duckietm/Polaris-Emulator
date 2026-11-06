package polaris.plugin.events.guilds.forums;

import polaris.hotel.guilds.forums.ForumThread;
import polaris.hotel.users.User;
import polaris.plugin.Event;

public class GuildForumThreadCommentBeforeCreated extends Event {
    public final ForumThread thread;
    public final User poster;
    public final String message;

    public GuildForumThreadCommentBeforeCreated(ForumThread thread, User poster, String message) {
        this.thread = thread;
        this.poster = poster;
        this.message = message;
    }
}
