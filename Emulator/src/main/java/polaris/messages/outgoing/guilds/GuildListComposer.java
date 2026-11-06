package polaris.messages.outgoing.guilds;

import java.util.Set;
import polaris.Emulator;
import polaris.hotel.guilds.Guild;
import polaris.hotel.users.User;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class GuildListComposer extends MessageComposer {
    private final Set<Guild> guilds;
    private final User user;

    public GuildListComposer(Set<Guild> guilds, User user) {
        this.guilds = guilds;
        this.user = user;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.GuildListComposer);
        this.response.appendInt(this.guilds.size());
        for (Guild guild : this.guilds) {
            this.response.appendInt(guild.getId());
            this.response.appendString(guild.getName());
            this.response.appendString(guild.getBadge());
            this.response.appendString(
                    Emulator.getGameEnvironment().getGuildManager().getSymbolColor(guild.getColorOne()).valueA);
            this.response.appendString(
                    Emulator.getGameEnvironment().getGuildManager().getBackgroundColor(guild.getColorTwo()).valueA);
            this.response.appendBoolean(this.user.getUserStats().guild == guild.getId());
            this.response.appendInt(guild.getOwnerId());
            this.response.appendBoolean(guild.hasForum());
        }
        return this.response;
    }

    public Set<Guild> getGuilds() {
        return guilds;
    }

    public User getUser() {
        return user;
    }
}
