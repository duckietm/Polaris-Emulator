package polaris.messages.incoming.guilds;

import java.util.HashSet;
import java.util.Set;
import polaris.Emulator;
import polaris.hotel.guilds.Guild;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.guilds.GuildListComposer;

public class RequestOwnGuildsEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() throws Exception {
        Set<Guild> guilds = new HashSet<Guild>();

        for (int i : this.client.getUser().getUserStats().guilds) {
            if (i == 0) continue;

            Guild g = Emulator.getGameEnvironment().getGuildManager().getGuild(i);

            if (g != null) {
                guilds.add(g);
            }
        }

        this.client.sendResponse(new GuildListComposer(guilds, this.client.getUser()));
    }
}
