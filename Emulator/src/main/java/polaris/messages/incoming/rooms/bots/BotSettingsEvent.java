package polaris.messages.incoming.rooms.bots;

import polaris.hotel.bots.Bot;
import polaris.hotel.permissions.Permission;
import polaris.hotel.rooms.Room;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.rooms.BotSettingsComposer;

public class BotSettingsEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        Room room = this.client.getUser().getUserInfo().getCurrentRoom();

        if (room == null) return;

        if (room.getOwnerId() == this.client.getUser().getUserInfo().getId()
                || this.client.getUser().hasPermission(Permission.ACC_ANYROOMOWNER)) {
            int botId = this.packet.readInt();

            Bot bot = room.getBot(Math.abs(botId));

            if (bot == null) return;

            this.client.sendResponse(new BotSettingsComposer(bot, this.packet.readInt()));
        }
    }
}
