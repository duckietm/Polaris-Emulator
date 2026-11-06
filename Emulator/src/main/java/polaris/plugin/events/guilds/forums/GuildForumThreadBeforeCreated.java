package polaris.plugin.events.guilds.forums;

import polaris.hotel.guilds.Guild;
import polaris.hotel.users.User;
import polaris.plugin.Event;

public class GuildForumThreadBeforeCreated extends Event {
    public final Guild guild;
    public final User opener;
    public final String subject;
    public final String message;

    public GuildForumThreadBeforeCreated(Guild guild, User opener, String subject, String message) {
        this.guild = guild;
        this.opener = opener;
        this.subject = subject;
        this.message = message;
    }
}
