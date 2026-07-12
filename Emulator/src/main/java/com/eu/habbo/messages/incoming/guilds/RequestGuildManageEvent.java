package com.eu.habbo.messages.incoming.guilds;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.guilds.Guild;
import com.eu.habbo.habbohotel.guilds.GuildManager;
import com.eu.habbo.habbohotel.guilds.GuildMember;
import com.eu.habbo.habbohotel.guilds.GuildRank;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.guilds.GuildManageComposer;

public class RequestGuildManageEvent extends MessageHandler {
    private static final String ACC_GUILD_ADMIN = "acc_guild_admin";

    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() {
        final Habbo habbo = this.client.getHabbo();
        if (habbo == null) return;

        final HabboInfo habboInfo = habbo.getHabboInfo();
        if (habboInfo == null) return;

        final int guildId = this.packet.readInt();
        if (!GuildInputGuard.isPositiveId(guildId)) return;

        final GuildManager guildManager = Emulator.getGameEnvironment().getGuildManager();
        final Guild guild = guildManager.getGuild(guildId);

        if (guild == null) return;

        if (!habbo.hasPermission(ACC_GUILD_ADMIN)) {
            Room room = habboInfo.getCurrentRoom();

            if (room == null || room.getId() != guild.getRoomId()) {
                return;
            }
        }

        if (this.hasManageRights(guildManager, guild, habbo)) {
            this.client.sendResponse(new GuildManageComposer(guild));
        }
    }

    private boolean hasManageRights(GuildManager guildManager, Guild guild, Habbo habbo) {
        if (habbo.hasPermission(ACC_GUILD_ADMIN) || habbo.getHabboInfo().getId() == guild.getOwnerId()) return true;

        final GuildMember member = guildManager.getGuildMember(guild, habbo);
        return member != null && member.getRank() == GuildRank.ADMIN;
    }
}
