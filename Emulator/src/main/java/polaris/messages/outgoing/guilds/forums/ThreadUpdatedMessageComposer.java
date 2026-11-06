package polaris.messages.outgoing.guilds.forums;

import polaris.hotel.guilds.Guild;
import polaris.hotel.guilds.forums.ForumThread;
import polaris.hotel.users.User;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class ThreadUpdatedMessageComposer extends MessageComposer {

    public final Guild guild;

    public final ForumThread thread;

    private final User user;

    private final boolean isPinned;

    private final boolean isLocked;

    public ThreadUpdatedMessageComposer(
            Guild guild, ForumThread thread, User user, boolean isPinned, boolean isLocked) {
        this.guild = guild;
        this.user = user;
        this.thread = thread;
        this.isPinned = isPinned;
        this.isLocked = isLocked;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.ThreadUpdateMessageComposer);
        this.response.appendInt(this.thread.getGuildId());
        this.thread.serialize(this.response);

        return this.response;
    }

    public Guild getGuild() {
        return guild;
    }

    public ForumThread getThread() {
        return thread;
    }

    public User getUser() {
        return user;
    }

    public boolean isPinned() {
        return isPinned;
    }

    public boolean isLocked() {
        return isLocked;
    }
}
