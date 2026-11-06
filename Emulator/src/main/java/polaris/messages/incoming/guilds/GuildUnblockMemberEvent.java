package polaris.messages.incoming.guilds;

import polaris.Emulator;
import polaris.hotel.guilds.Guild;
import polaris.hotel.guilds.GuildMember;
import polaris.hotel.guilds.GuildRank;
import polaris.hotel.permissions.Permission;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.guilds.GuildRefreshMembersListComposer;

/** AIR 13 UnblockGroupMember (2864): an admin lifts a block so the user can join again. */
public class GuildUnblockMemberEvent extends MessageHandler {
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

        if (guild == null) {
            return;
        }

        GuildMember actor =
                Emulator.getGameEnvironment().getGuildManager().getGuildMember(guild, this.client.getUser());
        boolean allowed =
                guild.getOwnerId() == this.client.getUser().getUserInfo().getId()
                        || this.client.getUser().hasPermission(Permission.ACC_GUILD_ADMIN)
                        || (actor != null
                                && (actor.getRank().equals(GuildRank.OWNER)
                                        || actor.getRank().equals(GuildRank.ADMIN)));

        if (!allowed) {
            return;
        }

        if (Emulator.getGameEnvironment().getGuildManager().unblockMember(guild, userId)) {
            this.client.sendResponse(new GuildRefreshMembersListComposer(guild));
        }
    }
}
