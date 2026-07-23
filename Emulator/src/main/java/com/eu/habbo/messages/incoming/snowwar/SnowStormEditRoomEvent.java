package com.eu.habbo.messages.incoming.snowwar;

import com.eu.habbo.habbohotel.games.snowwar.SnowWarGame;
import com.eu.habbo.habbohotel.games.snowwar.SnowWarManager;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;

/**
 * Header 6010: a permitted user (acc_snowwar_edit, rank 7 by default) opens
 * the in-game arena editor. There is no separate editor room any more - the
 * client edits the arena WYSIWYG in the SnowWar view itself and publishes the
 * layout with the save-editor packet (6011). All the server does here is take
 * the player out of the running game/queue so nothing keeps ticking behind the
 * editor; the client keeps the level snapshot it already has and switches to
 * edit mode locally.
 */
public class SnowStormEditRoomEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        Habbo habbo = this.client.getHabbo();
        if (habbo == null || !habbo.hasPermission(SnowWarManager.EDIT_PERMISSION)) {
            return;
        }

        int userId = habbo.getHabboInfo().getId();
        SnowWarGame game = SnowWarManager.getInstance().getGameByUserId(userId);
        if (game != null) {
            game.exitGame(userId);
        }
        SnowWarManager.getInstance().leaveQueue(habbo);
    }
}
