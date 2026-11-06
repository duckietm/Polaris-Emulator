package polaris.hotel.rooms;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import java.util.Optional;
import java.util.Set;
import polaris.hotel.guilds.Guild;
import polaris.hotel.guilds.GuildMember;
import polaris.hotel.items.interactions.InteractionGuildFurni;
import polaris.hotel.permissions.Permission;
import polaris.hotel.users.User;
import polaris.hotel.users.UserItem;
import polaris.messages.outgoing.guilds.GuildInfoComposer;

final class RoomGuildService {

    private final Room room;

    RoomGuildService(Room room) {
        this.room = room;
    }

    String name() {
        if (!this.room.hasGuild()) {
            return "";
        }

        Guild guild = this.room.gameEnvironment().getGuildManager().getGuild(this.room.getGuildId());
        return guild == null ? "" : guild.getName();
    }

    void refresh(Guild guild) {
        if (guild.getRoomId() == this.room.getId()) {
            Set<GuildMember> members =
                    this.room.gameEnvironment().getGuildManager().getGuildMembers(guild.getId());
            for (User user : this.room.getUsers()) {
                Optional<GuildMember> member = members.stream()
                        .filter(candidate ->
                                candidate.getUserId() == user.getUserInfo().getId())
                        .findAny();
                if (member.isPresent()) {
                    user.getClient().sendResponse(new GuildInfoComposer(guild, user.getClient(), false, member.get()));
                }
            }
        }
        this.refreshRights();
    }

    void refreshColors(Guild guild) {
        if (guild.getRoomId() != this.room.getId()) {
            return;
        }

        Int2ObjectMap<UserItem> items = this.room.getItemManager().getRoomItems();
        synchronized (items) {
            for (UserItem item : items.values()) {
                if (item instanceof InteractionGuildFurni guildFurni && guildFurni.getGuildId() == guild.getId()) {
                    this.room.updateItem(item);
                }
            }
        }
    }

    void refreshRights() {
        for (User user : this.room.getUsers()) {
            if (user.getUserInfo().getCurrentRoom() == this.room
                    && user.getUserInfo().getId() != this.room.getOwnerId()
                    && !user.hasPermission(Permission.ACC_ANYROOMOWNER)
                    && !user.hasPermission(Permission.ACC_MOVEROTATE)) {
                this.room.refreshRightsForUser(user);
            }
        }
    }
}
