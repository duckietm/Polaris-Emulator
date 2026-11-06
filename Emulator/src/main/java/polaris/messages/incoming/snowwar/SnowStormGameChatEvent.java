package polaris.messages.incoming.snowwar;

import polaris.hotel.games.snowwar.SnowWarGame;
import polaris.hotel.games.snowwar.SnowWarGamePlayer;
import polaris.hotel.games.snowwar.SnowWarManager;
import polaris.messages.incoming.MessageHandler;

public class SnowStormGameChatEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        if (this.client.getUser() == null) {
            return;
        }

        int userId = this.client.getUser().getUserInfo().getId();
        if (!SnowWarManager.getInstance().allowPacket(userId)) {
            return;
        }

        // The message must be the whole payload: its length prefix has to
        // match the bytes that follow (readString would silently clamp it).
        int available = this.packet.bytesAvailable();
        int declaredLength = available >= Short.BYTES
                ? this.packet
                        .getBuffer()
                        .getUnsignedShort(this.packet.getBuffer().readerIndex())
                : -1;
        if (!SnowWarPacket.isWholeString(available, declaredLength, SnowWarPacket.MAX_CHAT_BYTES)) {
            SnowWarPacket.reject(userId, "game chat", "length prefix does not match the payload");
            return;
        }
        String message = this.packet.readString();
        SnowWarGame game = SnowWarManager.getInstance().getGameByUserId(userId);

        if (game == null) {
            return;
        }

        SnowWarGamePlayer player = game.getPlayer(userId);
        if (player == null) {
            return;
        }

        game.handleChat(player, message);
    }
}
