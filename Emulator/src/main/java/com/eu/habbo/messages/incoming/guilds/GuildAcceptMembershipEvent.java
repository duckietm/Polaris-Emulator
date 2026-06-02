package com.eu.habbo.messages.incoming.guilds;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.guilds.Guild;
import com.eu.habbo.habbohotel.guilds.GuildMember;
import com.eu.habbo.habbohotel.guilds.GuildRank;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.guilds.GuildAcceptMemberErrorComposer;
import com.eu.habbo.messages.outgoing.guilds.GuildInfoComposer;
import com.eu.habbo.messages.outgoing.guilds.GuildRefreshMembersListComposer;
import com.eu.habbo.plugin.events.guilds.GuildAcceptedMembershipEvent;

public class GuildAcceptMembershipEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() throws Exception {
        int guildId = this.packet.readInt();
        int userId = this.packet.readInt();

        Guild guild = Emulator.getGameEnvironment().getGuildManager().getGuild(guildId);

        if (guild == null) {
            return;
        }

        GuildMember actorMember = Emulator.getGameEnvironment().getGuildManager().getGuildMember(guild, this.client.getHabbo());
        boolean canAccept = guild.getOwnerId() == this.client.getHabbo().getHabboInfo().getId()
                || this.client.getHabbo().hasPermission(Permission.ACC_GUILD_ADMIN)
                || (actorMember != null && (actorMember.getRank().equals(GuildRank.ADMIN) || actorMember.getRank().equals(GuildRank.OWNER)));

        if (!canAccept) {
            return;
        }

        GuildMember targetMember = Emulator.getGameEnvironment().getGuildManager().getGuildMember(guildId, userId);

        if (targetMember == null) {
            this.client.sendResponse(new GuildAcceptMemberErrorComposer(guild.getId(), GuildAcceptMemberErrorComposer.NO_LONGER_MEMBER));
            return;
        }

        if (targetMember.getRank().type != GuildRank.REQUESTED.type) {
            this.client.sendResponse(new GuildAcceptMemberErrorComposer(guild.getId(), GuildAcceptMemberErrorComposer.ALREADY_ACCEPTED));
            return;
        }

        Habbo habbo = Emulator.getGameEnvironment().getHabboManager().getHabbo(userId);

        GuildAcceptedMembershipEvent event = new GuildAcceptedMembershipEvent(guild, userId, habbo);
        Emulator.getPluginManager().fireEvent(event);

        if (event.isCancelled()) {
            return;
        }

        if (habbo != null) {
            habbo.getHabboStats().addGuild(guild.getId());
        }

        Emulator.getGameEnvironment().getGuildManager().joinGuild(guild, this.client, userId, true);
        guild.decreaseRequestCount();
        guild.increaseMemberCount();
        this.client.sendResponse(new GuildRefreshMembersListComposer(guild));

        if (habbo != null) {
            Room room = habbo.getHabboInfo().getCurrentRoom();
            if (room != null && room.getGuildId() == guildId) {
                habbo.getClient().sendResponse(new GuildInfoComposer(guild, habbo.getClient(), false, Emulator.getGameEnvironment().getGuildManager().getGuildMember(guildId, userId)));
                room.refreshRightsForHabbo(habbo);
            }
        }
    }
}
