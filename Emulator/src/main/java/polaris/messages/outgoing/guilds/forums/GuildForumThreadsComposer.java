package polaris.messages.outgoing.guilds.forums;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import polaris.hotel.guilds.Guild;
import polaris.hotel.guilds.forums.ForumThread;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;
import polaris.messages.outgoing.handshake.ConnectionErrorComposer;

public class GuildForumThreadsComposer extends MessageComposer {
    public final Guild guild;
    public final int index;

    public GuildForumThreadsComposer(Guild guild, int index) {
        this.guild = guild;
        this.index = index;
    }

    @Override
    protected ServerMessage composeInternal() {
        ArrayList<ForumThread> threads;

        try {
            threads = new ArrayList<>(ForumThread.getByGuildId(guild.getId()));
        } catch (Exception e) {
            return new ConnectionErrorComposer(500).compose();
        }

        threads.sort(Comparator.comparingInt(o -> o.isPinned() ? Integer.MAX_VALUE : o.getUpdatedAt()));
        Collections.reverse(threads);

        Iterator<ForumThread> it = threads.iterator();
        int count = Math.min(threads.size(), 20);

        this.response.init(Outgoing.GuildForumThreadsComposer);
        this.response.appendInt(this.guild.getId());
        this.response.appendInt(this.index);
        this.response.appendInt(count);

        for (int i = 0; i < index; i++) {
            if (!it.hasNext()) break;

            it.next();
        }

        for (int i = 0; i < count; i++) {
            if (!it.hasNext()) break;

            it.next().serialize(this.response);
        }

        return this.response;
    }

    public Guild getGuild() {
        return guild;
    }

    public int getIndex() {
        return index;
    }
}
