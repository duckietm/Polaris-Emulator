package polaris.messages.incoming.guilds;

import polaris.Emulator;
import polaris.hotel.guilds.Guild;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.guilds.GuildFavoriteRoomUserUpdateComposer;
import polaris.messages.outgoing.rooms.users.RoomUsersAddGuildBadgeComposer;
import polaris.messages.outgoing.users.UserProfileComposer;
import polaris.plugin.events.guilds.GuildFavoriteSetEvent;

public class GuildSetFavoriteEvent extends MessageHandler {
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

        Guild guild = Emulator.getGameEnvironment().getGuildManager().getGuild(guildId);

        if (this.client.getUser().getUserStats().hasGuild(guildId)) {
            GuildFavoriteSetEvent favoriteSetEvent = new GuildFavoriteSetEvent(guild, this.client.getUser());
            Emulator.getPluginManager().fireEvent(favoriteSetEvent);

            if (favoriteSetEvent.isCancelled()) return;

            this.client.getUser().getUserStats().guild = guildId;

            if (this.client.getUser().getUserInfo().getCurrentRoom() != null) {
                if (guild != null) {
                    this.client
                            .getUser()
                            .getUserInfo()
                            .getCurrentRoom()
                            .sendComposer(new RoomUsersAddGuildBadgeComposer(guild).compose());
                    this.client
                            .getUser()
                            .getUserInfo()
                            .getCurrentRoom()
                            .sendComposer(new GuildFavoriteRoomUserUpdateComposer(
                                            this.client.getUser().getRoomUnit(), guild)
                                    .compose());
                }
            }

            this.client.sendResponse(new UserProfileComposer(this.client.getUser(), this.client));
        }
    }
}
