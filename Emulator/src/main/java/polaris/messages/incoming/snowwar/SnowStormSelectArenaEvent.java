package polaris.messages.incoming.snowwar;

import polaris.hotel.games.snowwar.SnowWarManager;
import polaris.messages.incoming.MessageHandler;

public class SnowStormSelectArenaEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        if (this.client.getUser() == null) {
            return;
        }

        SnowWarManager manager = SnowWarManager.getInstance();
        int userId = this.client.getUser().getUserInfo().getId();
        if (!manager.allowPacket(userId)) {
            return;
        }

        if (!SnowWarPacket.hasInts(this.packet.bytesAvailable(), 1)) {
            SnowWarPacket.reject(userId, "select arena", "unexpected payload length");
            return;
        }
        manager.selectArena(this.client.getUser(), this.packet.readInt());
    }
}
