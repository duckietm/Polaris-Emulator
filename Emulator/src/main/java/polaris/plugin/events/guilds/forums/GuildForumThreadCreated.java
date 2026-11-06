package polaris.plugin.events.guilds.forums;

import polaris.hotel.guilds.forums.ForumThread;
import polaris.plugin.Event;

public class GuildForumThreadCreated extends Event {
    public final ForumThread thread;

    public GuildForumThreadCreated(ForumThread thread) {
        this.thread = thread;
    }
}
