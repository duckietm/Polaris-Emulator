package polaris.messages.outgoing.guilds.forums;

import java.util.Iterator;
import java.util.Set;
import polaris.hotel.guilds.Guild;
import polaris.hotel.users.User;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class GuildForumListComposer extends MessageComposer {
    private final Set<Guild> guilds;
    private final User user;
    private final int mode;
    private final int index;

    public GuildForumListComposer(Set<Guild> guilds, User user, int mode, int index) {
        this.guilds = guilds;
        this.user = user;
        this.mode = mode;
        this.index = index;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.GuildForumListComposer);
        this.response.appendInt(this.mode);
        this.response.appendInt(this.guilds.size());

        this.response.appendInt(this.index);

        Iterator<Guild> it = guilds.iterator();
        int count = Math.min(guilds.size(), 20);

        this.response.appendInt(count);

        for (int i = 0; i < index; i++) {
            if (!it.hasNext()) break;

            it.next();
        }

        for (int i = 0; i < count; i++) {
            if (!it.hasNext()) break;

            GuildForumDataComposer.serializeForumData(this.response, it.next(), user);
        }

        return this.response;
    }

    public Set<Guild> getGuilds() {
        return guilds;
    }

    public User getUser() {
        return user;
    }

    public int getMode() {
        return mode;
    }

    public int getIndex() {
        return index;
    }
}
