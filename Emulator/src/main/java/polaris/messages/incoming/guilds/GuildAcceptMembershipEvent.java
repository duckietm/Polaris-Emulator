package polaris.messages.incoming.guilds;

import polaris.Emulator;
import polaris.hotel.guilds.Guild;
import polaris.hotel.guilds.GuildMember;
import polaris.hotel.guilds.GuildRank;
import polaris.hotel.permissions.Permission;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.User;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.guilds.GuildAcceptMemberErrorComposer;
import polaris.messages.outgoing.guilds.GuildInfoComposer;
import polaris.messages.outgoing.guilds.GuildRefreshMembersListComposer;
import polaris.plugin.events.guilds.GuildAcceptedMembershipEvent;

public class GuildAcceptMembershipEvent extends MessageHandler {
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

        GuildMember targetMember =
                Emulator.getGameEnvironment().getGuildManager().getGuildMember(guildId, userId);

        if (targetMember == null) {
            this.client.sendResponse(
                    new GuildAcceptMemberErrorComposer(guild.getId(), GuildAcceptMemberErrorComposer.NO_LONGER_MEMBER));
            return;
        }

        if (targetMember.getRank().type != GuildRank.REQUESTED.type) {
            this.client.sendResponse(
                    new GuildAcceptMemberErrorComposer(guild.getId(), GuildAcceptMemberErrorComposer.ALREADY_ACCEPTED));
            return;
        }

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
        this.client.sendResponse(new GuildRefreshMembersListComposer(guild));

        if (user != null) {
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
}
