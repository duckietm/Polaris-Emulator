package polaris.messages.incoming.guilds;

import polaris.Emulator;
import polaris.hotel.guilds.Guild;
import polaris.hotel.guilds.GuildMember;
import polaris.hotel.permissions.Permission;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.User;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.guilds.GuildInfoComposer;
import polaris.messages.outgoing.guilds.GuildMemberUpdateComposer;
import polaris.plugin.events.guilds.GuildRemovedAdminEvent;

public class GuildRemoveAdminEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() throws Exception {
        int guildId = this.packet.readInt();
        int userId = this.packet.readInt();

        if (!GuildInputGuard.arePositiveIds(guildId, userId)) {
            return;
        }

        Guild guild = Emulator.getGameEnvironment().getGuildManager().getGuild(guildId);

        if (guild != null) {
            if (guild.getOwnerId() == this.client.getUser().getUserInfo().getId()
                    || this.client.getUser().hasPermission(Permission.ACC_GUILD_ADMIN)) {
                Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(guild.getRoomId());
                User user = Emulator.getGameEnvironment().getUserManager().getUser(userId);

                GuildRemovedAdminEvent removedAdminEvent = new GuildRemovedAdminEvent(guild, userId, user);
                Emulator.getPluginManager().fireEvent(removedAdminEvent);

                if (removedAdminEvent.isCancelled()) return;

                Emulator.getGameEnvironment().getGuildManager().removeAdmin(guild, userId);

                if (user != null) {
                    user.getClient()
                            .sendResponse(new GuildInfoComposer(
                                    guild,
                                    this.client,
                                    false,
                                    Emulator.getGameEnvironment()
                                            .getGuildManager()
                                            .getGuildMember(guild.getId(), userId)));

                    if (room != null
                            && user.getUserInfo().getCurrentRoom() != null
                            && user.getUserInfo().getCurrentRoom() == room) room.refreshRightsForUser(user);
                }
                GuildMember guildMember =
                        Emulator.getGameEnvironment().getGuildManager().getGuildMember(guildId, userId);

                this.client.sendResponse(new GuildMemberUpdateComposer(guild, guildMember));
            }
        }
    }
}
