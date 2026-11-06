package polaris.messages.outgoing.guilds;

import polaris.Emulator;
import polaris.hotel.guilds.Guild;
import polaris.hotel.rooms.BuildersClubItemIds;
import polaris.hotel.users.User;
import polaris.hotel.users.UserItem;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class GuildFurniWidgetComposer extends MessageComposer {
    private final UserItem item;
    private final Guild guild;
    private final User user;

    public GuildFurniWidgetComposer(User user, Guild guild, UserItem item) {
        this.user = user;
        this.item = item;
        this.guild = guild;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.GuildFurniWidgetComposer);
        this.response.appendInt(BuildersClubItemIds.toWire(item.getId()));
        this.response.appendInt(this.guild.getId());
        this.response.appendString(this.guild.getName());
        this.response.appendInt(this.guild.getRoomId());
        this.response.appendBoolean(
                Emulator.getGameEnvironment().getGuildManager().getGuildMember(this.guild, this.user)
                        != null); // User Joined.
        this.response.appendBoolean(this.guild.hasForum()); // Has Forum.
        return this.response;
    }

    public UserItem getItem() {
        return item;
    }

    public Guild getGuild() {
        return guild;
    }

    public User getUser() {
        return user;
    }
}
