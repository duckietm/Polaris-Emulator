package com.eu.habbo.messages.incoming.guilds;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.guilds.Guild;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.guilds.GuildFavoriteRoomUserUpdateComposer;
import com.eu.habbo.messages.outgoing.users.UserProfileComposer;
import com.eu.habbo.plugin.events.guilds.GuildRemovedFavoriteEvent;

public class GuildRemoveFavoriteEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() throws Exception {
        int guildId = this.packet.readInt();

        if (this.client.getHabbo().getHabboStats().hasGuild(guildId)) {
            Guild guild = Emulator.getGameEnvironment().getGuildManager().getGuild(guildId);
            var favoriteEvent = new GuildRemovedFavoriteEvent(guild, this.client.getHabbo());
            Emulator.getPluginManager().fireEvent(favoriteEvent);
            if (favoriteEvent.isCancelled())
                return;

            this.client.getHabbo().getHabboStats().guild = 0;

            if (this.client.getHabbo().getHabboInfo().getCurrentRoom() != null && guild != null) {
                this.client.getHabbo().getHabboInfo().getCurrentRoom().sendComposer(new GuildFavoriteRoomUserUpdateComposer(this.client.getHabbo().getRoomUnit(), null).compose());
            }

            this.client.sendResponse(new UserProfileComposer(this.client.getHabbo(), this.client));
        }
    }
}
