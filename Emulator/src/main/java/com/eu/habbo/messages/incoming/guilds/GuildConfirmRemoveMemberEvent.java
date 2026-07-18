package com.eu.habbo.messages.incoming.guilds;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.guilds.Guild;
import com.eu.habbo.habbohotel.guilds.GuildMember;
import com.eu.habbo.habbohotel.guilds.GuildRank;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.guilds.GuildConfirmRemoveMemberComposer;

public class GuildConfirmRemoveMemberEvent extends MessageHandler {
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
            GuildMember member = Emulator.getGameEnvironment().getGuildManager().getGuildMember(guild, this.client.getHabbo());
            if (userId == this.client.getHabbo().getHabboInfo().getId() || guild.getOwnerId() == this.client.getHabbo().getHabboInfo().getId() || (member != null && member.getRank().equals(GuildRank.OWNER) || (member != null && (member.getRank().equals(GuildRank.OWNER) || member.getRank().equals(GuildRank.ADMIN))))) {
                Room room = Emulator.getGameEnvironment().getRoomManager().loadRoom(guild.getRoomId());
                int count = 0;
                if (room != null) {
                    count = room.getUserFurniCount(userId);
                }
                this.client.sendResponse(new GuildConfirmRemoveMemberComposer(userId, count));
            }
        }

    }
}
