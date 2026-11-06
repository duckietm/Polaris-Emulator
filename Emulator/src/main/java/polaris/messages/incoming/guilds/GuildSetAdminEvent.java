package polaris.messages.incoming.guilds;

import polaris.Emulator;
import polaris.hotel.guilds.Guild;
import polaris.hotel.guilds.GuildManager;
import polaris.hotel.guilds.GuildMember;
import polaris.hotel.guilds.GuildRank;
import polaris.hotel.permissions.Permission;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.User;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.guilds.GuildMemberUpdateComposer;
import polaris.plugin.events.guilds.GuildGivenAdminEvent;

public class GuildSetAdminEvent extends MessageHandler {
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

        GuildManager guilds = Emulator.getGameEnvironment().getGuildManager();
        Guild guild = guilds.getGuild(guildId);

        if (guild != null) {
            if (guild.getOwnerId() == this.client.getUser().getUserInfo().getId()
                    || this.client.getUser().hasPermission(Permission.ACC_GUILD_ADMIN)) {

                // Only an accepted member becomes an admin: a user who had only asked to join was made admin, and
                // so got the group room's rights, without ever being accepted.
                GuildMember target = guilds.getGuildMember(guildId, userId);
                if (target == null || target.getRank() != GuildRank.MEMBER || userId == guild.getOwnerId()) return;

                User user = Emulator.getGameEnvironment().getUserManager().getUser(userId);

                GuildGivenAdminEvent adminEvent = new GuildGivenAdminEvent(guild, userId, user, this.client.getUser());
                Emulator.getPluginManager().fireEvent(adminEvent);

                if (adminEvent.isCancelled()) return;

                guilds.setAdmin(guild, userId);

                if (user != null) {
                    Room room = user.getUserInfo().getCurrentRoom();
                    if (room != null) {
                        if (room.getGuildId() == guildId) {
                            room.refreshRightsForUser(user);
                        }
                    }
                }

                GuildMember guildMember = guilds.getGuildMember(guildId, userId);

                this.client.sendResponse(new GuildMemberUpdateComposer(guild, guildMember));
            }
        }
    }
}
