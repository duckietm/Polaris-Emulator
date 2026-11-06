package polaris.messages.incoming.guilds;

import polaris.Emulator;
import polaris.hotel.guilds.Guild;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.guilds.GuildFavoriteRoomUserUpdateComposer;
import polaris.messages.outgoing.users.UserProfileComposer;
import polaris.plugin.events.guilds.GuildRemovedFavoriteEvent;

public class GuildRemoveFavoriteEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() throws Exception {
        int guildId = this.packet.readInt();

        if (!GuildInputGuard.isPositiveId(guildId)) {
            return;
        }

        if (this.client.getUser().getUserStats().hasGuild(guildId)) {
            Guild guild = Emulator.getGameEnvironment().getGuildManager().getGuild(guildId);
            GuildRemovedFavoriteEvent favoriteEvent = new GuildRemovedFavoriteEvent(guild, this.client.getUser());
            Emulator.getPluginManager().fireEvent(favoriteEvent);
            if (favoriteEvent.isCancelled()) return;

            this.client.getUser().getUserStats().guild = 0;

            if (this.client.getUser().getUserInfo().getCurrentRoom() != null && guild != null) {
                this.client
                        .getUser()
                        .getUserInfo()
                        .getCurrentRoom()
                        .sendComposer(new GuildFavoriteRoomUserUpdateComposer(
                                        this.client.getUser().getRoomUnit(), null)
                                .compose());
            }

            this.client.sendResponse(new UserProfileComposer(this.client.getUser(), this.client));
        }
    }
}
