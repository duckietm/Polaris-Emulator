package polaris.messages.incoming.snowwar;

import polaris.hotel.games.snowwar.SnowWarGame;
import polaris.hotel.games.snowwar.SnowWarGamePlayer;
import polaris.hotel.games.snowwar.SnowWarManager;
import polaris.messages.incoming.MessageHandler;

public class SnowStormThrowAtLocationEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        if (this.client.getUser() == null) {
            return;
        }

        int userId = this.client.getUser().getUserInfo().getId();
        if (!SnowWarManager.getInstance().allowPacket(userId)) {
            return;
        }

        // worldX, worldY, trajectory [, turn, subturn]
        if (!SnowWarPacket.hasInts(this.packet.bytesAvailable(), 3)) {
            SnowWarPacket.reject(userId, "throw at location", "unexpected payload length");
            return;
        }
        int worldX = this.packet.readInt();
        int worldY = this.packet.readInt();
        int trajectory = this.packet.readInt();
        if (!SnowWarPacket.isTrajectory(trajectory)) {
            SnowWarPacket.reject(userId, "throw at location", "unknown trajectory " + trajectory);
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
        if (!SnowWarPacket.isInArena(game, worldX, worldY)) {
            SnowWarPacket.reject(userId, "throw at location", "target outside the arena");
            return;
        }

        game.handleThrowAtLocation(player, worldX, worldY, trajectory);
    }
}
