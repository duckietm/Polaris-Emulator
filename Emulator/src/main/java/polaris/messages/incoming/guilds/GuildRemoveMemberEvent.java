package polaris.messages.incoming.guilds;

import polaris.Emulator;
import polaris.hotel.guilds.Guild;
import polaris.hotel.guilds.GuildMember;
import polaris.hotel.guilds.GuildRank;
import polaris.hotel.permissions.Permission;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.User;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.guilds.GuildFavoriteRoomUserUpdateComposer;
import polaris.messages.outgoing.guilds.GuildInfoComposer;
import polaris.messages.outgoing.guilds.GuildRefreshMembersListComposer;
import polaris.plugin.events.guilds.GuildRemovedMemberEvent;

public class GuildRemoveMemberEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() throws Exception {
        int guildId = this.packet.readInt();
        int userId = this.packet.readInt();
        boolean block = this.packet.readBoolean(); // AIR 13 KickMember third field; false when a client omits it

        if (!GuildInputGuard.arePositiveIds(guildId, userId)) {
            return;
        }

        Guild guild = Emulator.getGameEnvironment().getGuildManager().getGuild(guildId);

        if (guild == null) {
            return;
        }

        GuildMember member =
                Emulator.getGameEnvironment().getGuildManager().getGuildMember(guild, this.client.getUser());

        if (member == null) {
            return;
        }

        if (userId == guild.getOwnerId()) {
            return;
        }

        GuildMember targetMember =
                Emulator.getGameEnvironment().getGuildManager().getGuildMember(guildId, userId);
        if (targetMember == null) {
            return;
        }

        boolean actorIsGuildOwner =
                guild.getOwnerId() == this.client.getUser().getUserInfo().getId()
                        || member.getRank().equals(GuildRank.OWNER);
        boolean actorIsGlobalGuildAdmin = this.client.getUser().hasPermission(Permission.ACC_GUILD_ADMIN);
        if ((targetMember.getRank().equals(GuildRank.ADMIN)
                        || targetMember.getRank().equals(GuildRank.OWNER))
                && !actorIsGuildOwner
                && !actorIsGlobalGuildAdmin) {
            return;
        }

        if (userId == this.client.getUser().getUserInfo().getId()
                || guild.getOwnerId() == this.client.getUser().getUserInfo().getId()
                || member.getRank().equals(GuildRank.OWNER)
                || member.getRank().equals(GuildRank.ADMIN)
                || this.client.getUser().hasPermission(Permission.ACC_GUILD_ADMIN)) {
            User user = Emulator.getGameEnvironment().getUserManager().getUser(userId);
            GuildRemovedMemberEvent removedMemberEvent = new GuildRemovedMemberEvent(guild, userId, user);
            Emulator.getPluginManager().fireEvent(removedMemberEvent);
            if (removedMemberEvent.isCancelled()) return;

            // A pending request was never counted as a member, and only members can be blocked.
            boolean wasRequest = targetMember.getRank().equals(GuildRank.REQUESTED);

            if (block
                    && !wasRequest
                    && userId != this.client.getUser().getUserInfo().getId()) {
                Emulator.getGameEnvironment().getGuildManager().blockMember(guild, userId);
            } else {
                Emulator.getGameEnvironment().getGuildManager().removeMember(guild, userId);
            }

            if (!wasRequest) guild.decreaseMemberCount();

            if (userId != this.client.getUser().getUserInfo().getId()) {
                this.client.sendResponse(new GuildRefreshMembersListComposer(guild));
            }

            Room room = Emulator.getGameEnvironment().getRoomManager().loadRoom(guild.getRoomId());

            if (user != null) {
                user.getUserStats().removeGuild(guild.getId());
                if (user.getUserStats().guild == guildId) user.getUserStats().guild = 0;

                if (room != null) {
                    if (user.getUserInfo().getCurrentRoom() != null && user.getRoomUnit() != null)
                        user.getUserInfo()
                                .getCurrentRoom()
                                .sendComposer(
                                        new GuildFavoriteRoomUserUpdateComposer(user.getRoomUnit(), null).compose());
                    if (user.getUserInfo().getCurrentRoom() == room) room.refreshRightsForUser(user);
                }

                user.getClient().sendResponse(new GuildInfoComposer(guild, user.getClient(), false, null));
            }

            if (room != null) {
                if (room.getGuildId() == guildId) {
                    room.ejectUserFurni(userId);
                }
            }
        }
    }
}
