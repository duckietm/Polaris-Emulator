package com.eu.habbo.messages.incoming.snowwar;

import com.eu.habbo.habbohotel.games.snowwar.SnowWarGame;
import com.eu.habbo.habbohotel.games.snowwar.SnowWarGamePlayer;
import com.eu.habbo.habbohotel.games.snowwar.SnowWarManager;
import com.eu.habbo.messages.incoming.MessageHandler;

public class SnowStormWalkEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        if (this.client.getHabbo() == null) {
            return;
        }

        int worldX = this.packet.readInt();
        int worldY = this.packet.readInt();
        // AIR turn/subturn values may follow. Polaris remains authoritative and
        // safely ignores those trailing fields in this isolated packet buffer.

        int userId = this.client.getHabbo().getHabboInfo().getId();
        if (!SnowWarManager.getInstance().allowPacket(userId)) {
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

        game.handleWalk(player, worldX, worldY);
    }
}
