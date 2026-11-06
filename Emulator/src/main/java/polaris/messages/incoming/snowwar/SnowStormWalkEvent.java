package polaris.messages.incoming.snowwar;

import polaris.hotel.games.snowwar.SnowWarGame;
import polaris.hotel.games.snowwar.SnowWarGamePlayer;
import polaris.hotel.games.snowwar.SnowWarManager;
import polaris.messages.incoming.MessageHandler;

public class SnowStormWalkEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        if (this.client.getUser() == null) {
            return;
        }

        int userId = this.client.getUser().getUserInfo().getId();
        if (!SnowWarManager.getInstance().allowPacket(userId)) {
            return;
        }

        // worldX, worldY [, turn, subturn] - the server ignores the turn fields.
        if (!SnowWarPacket.hasInts(this.packet.bytesAvailable(), 2)) {
            SnowWarPacket.reject(userId, "walk", "unexpected payload length");
            return;
        }
        int worldX = this.packet.readInt();
        int worldY = this.packet.readInt();
        SnowWarGame game = SnowWarManager.getInstance().getGameByUserId(userId);

        if (game == null) {
            return;
        }

        SnowWarGamePlayer player = game.getPlayer(userId);
        if (player == null) {
            return;
        }
        if (!SnowWarPacket.isInArena(game, worldX, worldY)) {
            SnowWarPacket.reject(userId, "walk", "target outside the arena");
            return;
        }

        game.handleWalk(player, worldX, worldY);
    }
}
