package com.eu.habbo.messages.incoming.guilds;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.guilds.Guild;
import com.eu.habbo.habbohotel.guilds.GuildMember;
import com.eu.habbo.habbohotel.guilds.GuildRank;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.guilds.GuildFavoriteRoomUserUpdateComposer;
import com.eu.habbo.messages.outgoing.guilds.GuildInfoComposer;
import com.eu.habbo.messages.outgoing.guilds.GuildRefreshMembersListComposer;
import com.eu.habbo.plugin.events.guilds.GuildRemovedMemberEvent;

public class GuildRemoveMemberEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() throws Exception {
        int guildId = this.packet.readInt();
        int userId = this.packet.readInt();

        Guild guild = Emulator.getGameEnvironment().getGuildManager().getGuild(guildId);

        if(guild == null) {
            return;
        }

        GuildMember member = Emulator.getGameEnvironment().getGuildManager().getGuildMember(guild, this.client.getHabbo());

        if(member == null) {
            return;
        }

        if(userId == guild.getOwnerId()) {
            return;
        }

        GuildMember targetMember = Emulator.getGameEnvironment().getGuildManager().getGuildMember(guildId, userId);
        if (targetMember == null) {
            return;
        }

        boolean actorIsGuildOwner = guild.getOwnerId() == this.client.getHabbo().getHabboInfo().getId() || member.getRank().equals(GuildRank.OWNER);
        boolean actorIsGlobalGuildAdmin = this.client.getHabbo().hasPermission(Permission.ACC_GUILD_ADMIN);
        if ((targetMember.getRank().equals(GuildRank.ADMIN) || targetMember.getRank().equals(GuildRank.OWNER))
                && !actorIsGuildOwner
                && !actorIsGlobalGuildAdmin) {
            return;
        }

        if (userId == this.client.getHabbo().getHabboInfo().getId() || guild.getOwnerId() == this.client.getHabbo().getHabboInfo().getId() || member.getRank().equals(GuildRank.OWNER) || member.getRank().equals(GuildRank.ADMIN) || this.client.getHabbo().hasPermission(Permission.ACC_GUILD_ADMIN)) {
            Habbo habbo = Emulator.getGameEnvironment().getHabboManager().getHabbo(userId);
            GuildRemovedMemberEvent removedMemberEvent = new GuildRemovedMemberEvent(guild, userId, habbo);
            Emulator.getPluginManager().fireEvent(removedMemberEvent);
            if (removedMemberEvent.isCancelled())
                return;

            Emulator.getGameEnvironment().getGuildManager().removeMember(guild, userId);
            guild.decreaseMemberCount();

            if (userId != this.client.getHabbo().getHabboInfo().getId()) {
                this.client.sendResponse(new GuildRefreshMembersListComposer(guild));
            }

            Room room = Emulator.getGameEnvironment().getRoomManager().loadRoom(guild.getRoomId());

            if (habbo != null) {
                habbo.getHabboStats().removeGuild(guild.getId());
                if (habbo.getHabboStats().guild == guildId)
                    habbo.getHabboStats().guild = 0;

                if (room != null) {
                    if (habbo.getHabboInfo().getCurrentRoom() != null && habbo.getRoomUnit() != null)
                        habbo.getHabboInfo().getCurrentRoom().sendComposer(new GuildFavoriteRoomUserUpdateComposer(habbo.getRoomUnit(), null).compose());
                    if (habbo.getHabboInfo().getCurrentRoom() == room)
                        room.refreshRightsForHabbo(habbo);
                }

                habbo.getClient().sendResponse(new GuildInfoComposer(guild, habbo.getClient(), false, null));
            }

            if (room != null) {
                if (room.getGuildId() == guildId) {
                    room.ejectUserFurni(userId);
                }
            }
        }
    }
}
