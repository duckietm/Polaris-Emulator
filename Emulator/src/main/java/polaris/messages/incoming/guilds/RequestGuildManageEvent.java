package polaris.messages.incoming.guilds;

import polaris.Emulator;
import polaris.hotel.guilds.Guild;
import polaris.hotel.guilds.GuildManager;
import polaris.hotel.guilds.GuildMember;
import polaris.hotel.guilds.GuildRank;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.User;
import polaris.hotel.users.UserInfo;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.guilds.GuildManageComposer;

public class RequestGuildManageEvent extends MessageHandler {
    private static final String ACC_GUILD_ADMIN = "acc_guild_admin";

    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() {
        final User user = this.client.getUser();
        if (user == null) return;

        final UserInfo userInfo = user.getUserInfo();
        if (userInfo == null) return;

        final int guildId = this.packet.readInt();
        if (!GuildInputGuard.isPositiveId(guildId)) return;

        final GuildManager guildManager = Emulator.getGameEnvironment().getGuildManager();
        final Guild guild = guildManager.getGuild(guildId);

        if (guild == null) return;

        if (!user.hasPermission(ACC_GUILD_ADMIN)) {
            Room room = userInfo.getCurrentRoom();

            if (room == null || room.getId() != guild.getRoomId()) {
                return;
            }
        }

        if (this.hasManageRights(guildManager, guild, user)) {
            this.client.sendResponse(new GuildManageComposer(guild));
        }
    }

    private boolean hasManageRights(GuildManager guildManager, Guild guild, User user) {
        if (user.hasPermission(ACC_GUILD_ADMIN) || user.getUserInfo().getId() == guild.getOwnerId()) return true;

        final GuildMember member = guildManager.getGuildMember(guild, user);
        return member != null && member.getRank() == GuildRank.ADMIN;
    }
}
