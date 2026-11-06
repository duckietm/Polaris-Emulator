package polaris.messages.incoming.snowwar;

import polaris.hotel.games.snowwar.SnowWarGame;
import polaris.hotel.games.snowwar.SnowWarGamePlayer;
import polaris.hotel.games.snowwar.SnowWarManager;
import polaris.messages.incoming.MessageHandler;

public class SnowStormThrowAtPlayerEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        if (this.client.getUser() == null) {
            return;
        }

        int userId = this.client.getUser().getUserInfo().getId();
        if (!SnowWarManager.getInstance().allowPacket(userId)) {
            return;
        }

        // targetObjectId, trajectory [, turn, subturn]
        if (!SnowWarPacket.hasInts(this.packet.bytesAvailable(), 2)) {
            SnowWarPacket.reject(userId, "throw at player", "unexpected payload length");
            return;
        }
        int targetObjectId = this.packet.readInt();
        int trajectory = this.packet.readInt();
        if (targetObjectId <= 0 || !SnowWarPacket.isTrajectory(trajectory)) {
            SnowWarPacket.reject(
                    userId, "throw at player", "bad target " + targetObjectId + " or trajectory " + trajectory);
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

        game.handleThrowAtPlayer(player, targetObjectId, trajectory);
    }
}
