package com.eu.habbo.habbohotel.rooms;

import com.eu.habbo.habbohotel.guilds.Guild;
import com.eu.habbo.habbohotel.guilds.GuildMember;
import com.eu.habbo.habbohotel.items.interactions.InteractionGuildFurni;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.outgoing.guilds.GuildInfoComposer;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import java.util.Optional;
import java.util.Set;

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
            for (Habbo habbo : this.room.getHabbos()) {
                Optional<GuildMember> member = members.stream()
                        .filter(candidate ->
                                candidate.getUserId() == habbo.getHabboInfo().getId())
                        .findAny();
                if (member.isPresent()) {
                    habbo.getClient()
                            .sendResponse(new GuildInfoComposer(guild, habbo.getClient(), false, member.get()));
                }
            }
        }
        this.refreshRights();
    }

    void refreshColors(Guild guild) {
        if (guild.getRoomId() != this.room.getId()) {
            return;
        }

        Int2ObjectMap<HabboItem> items = this.room.getItemManager().getRoomItems();
        synchronized (items) {
            for (HabboItem item : items.values()) {
                if (item instanceof InteractionGuildFurni guildFurni && guildFurni.getGuildId() == guild.getId()) {
                    this.room.updateItem(item);
                }
            }
        }
    }

    void refreshRights() {
        for (Habbo habbo : this.room.getHabbos()) {
            if (habbo.getHabboInfo().getCurrentRoom() == this.room
                    && habbo.getHabboInfo().getId() != this.room.getOwnerId()
                    && !habbo.hasPermission(Permission.ACC_ANYROOMOWNER)
                    && !habbo.hasPermission(Permission.ACC_MOVEROTATE)) {
                this.room.refreshRightsForHabbo(habbo);
            }
        }
    }
}
