package polaris.messages.incoming.guilds;

import java.util.ArrayList;
import java.util.List;
import polaris.Emulator;
import polaris.hotel.guilds.Guild;
import polaris.hotel.guilds.GuildMember;
import polaris.hotel.guilds.GuildRank;
import polaris.hotel.permissions.Permission;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.User;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.guilds.GuildInfoComposer;
import polaris.messages.outgoing.guilds.GuildRefreshMembersListComposer;
import polaris.plugin.events.guilds.GuildAcceptedMembershipEvent;

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
                Emulator.getGameEnvironment().getGuildManager().getGuildMember(guild, this.client.getUser());
        boolean canAccept =
                guild.getOwnerId() == this.client.getUser().getUserInfo().getId()
                        || this.client.getUser().hasPermission(Permission.ACC_GUILD_ADMIN)
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
        User user = Emulator.getGameEnvironment().getUserManager().getUser(userId);

        GuildAcceptedMembershipEvent event = new GuildAcceptedMembershipEvent(guild, userId, user);
        Emulator.getPluginManager().fireEvent(event);

        if (event.isCancelled()) {
            return;
        }

        // A join a limit refused, or a request that is already gone, changes no count and no guild list.
        if (!Emulator.getGameEnvironment().getGuildManager().tryJoinGuild(guild, this.client, userId, true)) {
            return;
        }

        if (user != null) {
            user.getUserStats().addGuild(guild.getId());
        }
        guild.decreaseRequestCount();
        guild.increaseMemberCount();

        if (user == null) {
            return;
        }

        Room room = user.getUserInfo().getCurrentRoom();

        if (room != null && room.getGuildId() == guildId) {
            user.getClient()
                    .sendResponse(new GuildInfoComposer(
                            guild,
                            user.getClient(),
                            false,
                            Emulator.getGameEnvironment().getGuildManager().getGuildMember(guildId, userId)));
            room.refreshRightsForUser(user);
        }
    }
}
