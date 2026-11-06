package polaris.messages.incoming.guilds;

import polaris.Emulator;
import polaris.hotel.guilds.Guild;
import polaris.hotel.rooms.BuildersClubItemIds;
import polaris.hotel.users.UserItem;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.guilds.GuildFurniWidgetComposer;

public class RequestGuildFurniWidgetEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() throws Exception {
        int itemId = BuildersClubItemIds.fromWire(this.currentRoom(), this.packet.readInt());
        int guildId = this.packet.readInt();

        if (!GuildInputGuard.arePositiveIds(itemId, guildId)) {
            return;
        }

        if (this.client.getUser().getUserInfo().getCurrentRoom() != null) {
            UserItem item = this.client.getUser().getUserInfo().getCurrentRoom().getUserItem(itemId);
            Guild guild = Emulator.getGameEnvironment().getGuildManager().getGuild(guildId);

            if (item != null && guild != null)
                this.client.sendResponse(new GuildFurniWidgetComposer(this.client.getUser(), guild, item));
        }
    }
}
