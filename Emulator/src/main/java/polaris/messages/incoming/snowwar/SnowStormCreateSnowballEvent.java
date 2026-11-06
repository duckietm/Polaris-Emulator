package polaris.messages.incoming.snowwar;

import polaris.hotel.games.snowwar.SnowWarGame;
import polaris.hotel.games.snowwar.SnowWarGamePlayer;
import polaris.hotel.games.snowwar.SnowWarManager;
import polaris.messages.incoming.MessageHandler;

public class SnowStormCreateSnowballEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        if (this.client.getUser() == null) {
            return;
        }

        int userId = this.client.getUser().getUserInfo().getId();
        if (!SnowWarManager.getInstance().allowPacket(userId)) {
            return;
        }

        // No fields of its own; only the optional turn and subturn.
        if (!SnowWarPacket.hasInts(this.packet.bytesAvailable(), 0)) {
            SnowWarPacket.reject(userId, "create snowball", "unexpected payload length");
            return;
        }
        SnowWarGame game = SnowWarManager.getInstance().getGameByUserId(userId);

        if (game == null) {
            return;
        }

        SnowWarGamePlayer player = game.getPlayer(userId);
        if (player == null) {
            return;
        }

        game.handleCreateSnowball(player);
    }
}
