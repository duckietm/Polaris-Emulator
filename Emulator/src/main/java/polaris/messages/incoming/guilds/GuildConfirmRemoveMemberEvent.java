package polaris.messages.incoming.guilds;

import polaris.Emulator;
import polaris.hotel.guilds.Guild;
import polaris.hotel.guilds.GuildMember;
import polaris.hotel.guilds.GuildRank;
import polaris.hotel.rooms.Room;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.guilds.GuildConfirmRemoveMemberComposer;

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
            GuildMember member =
                    Emulator.getGameEnvironment().getGuildManager().getGuildMember(guild, this.client.getUser());
            if (userId == this.client.getUser().getUserInfo().getId()
                    || guild.getOwnerId() == this.client.getUser().getUserInfo().getId()
                    || (member != null && member.getRank().equals(GuildRank.OWNER)
                            || (member != null
                                    && (member.getRank().equals(GuildRank.OWNER)
                                            || member.getRank().equals(GuildRank.ADMIN))))) {
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
