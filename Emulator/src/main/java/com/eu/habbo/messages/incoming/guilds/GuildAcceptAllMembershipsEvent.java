package com.eu.habbo.messages.incoming.guilds;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.guilds.Guild;
import com.eu.habbo.habbohotel.guilds.GuildMember;
import com.eu.habbo.habbohotel.guilds.GuildRank;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.guilds.GuildInfoComposer;
import com.eu.habbo.messages.outgoing.guilds.GuildRefreshMembersListComposer;
import com.eu.habbo.plugin.events.guilds.GuildAcceptedMembershipEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * Official {@code ApproveAllMembershipRequestsMessageComposer} (882): the "accept all" button of
 * the group members window. Same permission and side effects as
 * {@link GuildAcceptMembershipEvent}, applied to every pending request at once.
 */
public class GuildAcceptAllMembershipsEvent extends MessageHandler {
    /** Bounded so one click cannot walk an unbounded member list. */
    static final int MAX_ACCEPTED_PER_REQUEST = 250;

    @Override
    public int getRatelimit() {
        return 2000;
    }

    @Override
    public void handle() throws Exception {
        int guildId = this.packet.readInt();

        if (!GuildInputGuard.arePositiveIds(guildId)) {
            return;
        }

        Guild guild = Emulator.getGameEnvironment().getGuildManager().getGuild(guildId);

        if (guild == null) {
            return;
        }

        GuildMember actorMember =
                Emulator.getGameEnvironment().getGuildManager().getGuildMember(guild, this.client.getHabbo());
        boolean canAccept =
                guild.getOwnerId() == this.client.getHabbo().getHabboInfo().getId()
                        || this.client.getHabbo().hasPermission(Permission.ACC_GUILD_ADMIN)
                        || (actorMember != null
                                && (actorMember.getRank().equals(GuildRank.ADMIN)
                                        || actorMember.getRank().equals(GuildRank.OWNER)));

        if (!canAccept) {
            return;
        }

        List<Integer> pending = new ArrayList<>();

        for (GuildMember member :
                Emulator.getGameEnvironment().getGuildManager().getGuildMembers(guildId)) {
            if (member.getRank().type == GuildRank.REQUESTED.type) {
                pending.add(member.getUserId());
            }

            if (pending.size() >= MAX_ACCEPTED_PER_REQUEST) {
                break;
            }
        }

        if (pending.isEmpty()) {
            return;
        }

        for (int userId : pending) {
            this.accept(guild, guildId, userId);
        }

        this.client.sendResponse(new GuildRefreshMembersListComposer(guild));
    }

    private void accept(Guild guild, int guildId, int userId) {
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

        if (habbo == null) {
            return;
        }

        Room room = habbo.getHabboInfo().getCurrentRoom();

        if (room != null && room.getGuildId() == guildId) {
            habbo.getClient()
                    .sendResponse(new GuildInfoComposer(
                            guild,
                            habbo.getClient(),
                            false,
                            Emulator.getGameEnvironment().getGuildManager().getGuildMember(guildId, userId)));
            room.refreshRightsForHabbo(habbo);
        }
    }
}
