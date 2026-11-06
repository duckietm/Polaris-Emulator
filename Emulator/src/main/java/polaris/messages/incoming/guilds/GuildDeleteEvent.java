package polaris.messages.incoming.guilds;

import java.util.Set;
import polaris.Emulator;
import polaris.hotel.guilds.Guild;
import polaris.hotel.guilds.GuildMember;
import polaris.hotel.permissions.Permission;
import polaris.hotel.users.User;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.guilds.GuildFavoriteRoomUserUpdateComposer;
import polaris.messages.outgoing.guilds.RemoveGuildFromRoomComposer;
import polaris.messages.outgoing.rooms.RoomDataComposer;
import polaris.plugin.events.guilds.GuildDeletedEvent;

public class GuildDeleteEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() throws Exception {
        int guildId = this.packet.readInt();

        if (!GuildInputGuard.isPositiveId(guildId)) {
            return;
        }

        Guild guild = Emulator.getGameEnvironment().getGuildManager().getGuild(guildId);

        if (guild != null) {
            if (guild.getOwnerId() == this.client.getUser().getUserInfo().getId()
                    || this.client.getUser().hasPermission(Permission.ACC_GUILD_ADMIN)) {
                Set<GuildMember> members =
                        Emulator.getGameEnvironment().getGuildManager().getGuildMembers(guild.getId());

                for (GuildMember member : members) {
                    User user = Emulator.getGameServer().getGameClientManager().getUser(member.getUserId());
                    if (user != null)
                        if (user.getUserInfo().getCurrentRoom() != null && user.getRoomUnit() != null)
                            user.getUserInfo()
                                    .getCurrentRoom()
                                    .sendComposer(new GuildFavoriteRoomUserUpdateComposer(user.getRoomUnit(), null)
                                            .compose());
                }

                Emulator.getGameEnvironment().getGuildManager().deleteGuild(guild);
                Emulator.getPluginManager().fireEvent(new GuildDeletedEvent(guild, this.client.getUser()));
                polaris.hotel.rooms.Room guildRoom =
                        Emulator.getGameEnvironment().getRoomManager().getRoom(guild.getRoomId());

                if (guildRoom != null) {
                    for (User user : guildRoom.getUsers()) {
                        if (user.getClient() == null) {
                            continue;
                        }

                        // The group is gone: its badge and its panel have to go with it, and only
                        // this packet tells the room which group it was.
                        user.getClient().sendResponse(new RemoveGuildFromRoomComposer(guild.getId()));
                        user.getClient().sendResponse(new RoomDataComposer(guildRoom, user, true, false));
                    }
                }
            }
        }
    }
}
