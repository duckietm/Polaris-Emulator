package com.eu.habbo.messages.incoming.guilds;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.guilds.Guild;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.guilds.GuildFavoriteRoomUserUpdateComposer;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUsersAddGuildBadgeComposer;
import com.eu.habbo.messages.outgoing.users.UserProfileComposer;
import com.eu.habbo.plugin.events.guilds.GuildFavoriteSetEvent;

public class GuildSetFavoriteEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() throws Exception {
        int guildId = this.packet.readInt();

        Guild guild = Emulator.getGameEnvironment().getGuildManager().getGuild(guildId);

        if (this.client.getHabbo().getHabboStats().hasGuild(guildId)) {
            var favoriteSetEvent = new GuildFavoriteSetEvent(guild, this.client.getHabbo());
            Emulator.getPluginManager().fireEvent(favoriteSetEvent);

            if (favoriteSetEvent.isCancelled())
                return;

            this.client.getHabbo().getHabboStats().guild = guildId;

            if (this.client.getHabbo().getHabboInfo().getCurrentRoom() != null) {
                if (guild != null) {
                    this.client.getHabbo().getHabboInfo().getCurrentRoom().sendComposer(new RoomUsersAddGuildBadgeComposer(guild).compose());
                    this.client.getHabbo().getHabboInfo().getCurrentRoom().sendComposer(new GuildFavoriteRoomUserUpdateComposer(this.client.getHabbo().getRoomUnit(), guild).compose());
                }
            }

            this.client.sendResponse(new UserProfileComposer(this.client.getHabbo(), this.client));
        }
    }
}
