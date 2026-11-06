package polaris.messages.incoming.guilds;

import polaris.Emulator;
import polaris.hotel.guilds.Guild;
import polaris.hotel.guilds.GuildMember;
import polaris.hotel.guilds.GuildRank;
import polaris.hotel.permissions.Permission;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.User;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.guilds.GuildInfoComposer;
import polaris.messages.outgoing.guilds.GuildMembersComposer;
import polaris.messages.outgoing.guilds.GuildRefreshMembersListComposer;
import polaris.plugin.events.guilds.GuildDeclinedMembershipEvent;

public class GuildDeclineMembershipEvent extends MessageHandler {
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
            GuildMember member =
                    Emulator.getGameEnvironment().getGuildManager().getGuildMember(guild, this.client.getUser());
            if (userId == this.client.getUser().getUserInfo().getId()
                    || guild.getOwnerId() == this.client.getUser().getUserInfo().getId()
                    || (member != null
                            && (member.getRank().equals(GuildRank.ADMIN)
                                    || member.getRank().equals(GuildRank.OWNER)))
                    || this.client.getUser().hasPermission(Permission.ACC_GUILD_ADMIN)) {
                GuildMember target =
                        Emulator.getGameEnvironment().getGuildManager().getGuildMember(guildId, userId);
                if (target == null || target.getRank().type != GuildRank.REQUESTED.type) {
                    return;
                }

                guild.decreaseRequestCount();
                Emulator.getGameEnvironment().getGuildManager().removeMember(guild, userId);
                this.client.sendResponse(new GuildMembersComposer(
                        guild,
                        Emulator.getGameEnvironment().getGuildManager().getGuildMembers(guild, 0, 0, ""),
                        this.client.getUser(),
                        0,
                        0,
                        "",
                        true,
                        Emulator.getGameEnvironment().getGuildManager().getGuildMembersCount(guild, 0, 0, "")));
                this.client.sendResponse(new GuildRefreshMembersListComposer(guild));

                User user = Emulator.getGameEnvironment().getUserManager().getUser(userId);
                Emulator.getPluginManager()
                        .fireEvent(new GuildDeclinedMembershipEvent(guild, userId, user, this.client.getUser()));

                if (user != null) {
                    Room room = user.getUserInfo().getCurrentRoom();
                    if (room != null) {
                        if (room.getGuildId() == guildId) {
                            user.getClient().sendResponse(new GuildInfoComposer(guild, user.getClient(), false, null));
                        }
                    }
                }
            }
        }
    }
}
