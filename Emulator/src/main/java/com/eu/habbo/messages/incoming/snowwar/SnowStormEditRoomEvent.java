package com.eu.habbo.messages.incoming.snowwar;

import com.eu.habbo.habbohotel.games.snowwar.SnowWarGame;
import com.eu.habbo.habbohotel.games.snowwar.SnowWarManager;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.rooms.ForwardToRoomComposer;

/**
 * Header 6010: a permitted user (acc_snowwar_edit, rank 7 by default) asks
 * to edit the arena. The player leaves the running game/queue and is
 * forwarded into the editor room - a normal room using the SnowWar room
 * model - where furniture is placed with the standard tools and published
 * back into room_models.public_items with :snowwarsave.
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
        int mapId = game != null ? game.getMapId() : 1;

        if (game != null) {
            game.exitGame(userId);
        }
        SnowWarManager.getInstance().leaveQueue(habbo);

        Room room = SnowWarManager.getInstance().getOrCreateEditorRoom(habbo, mapId);
        if (room != null) {
            this.client.sendResponse(new ForwardToRoomComposer(room.getId()));
        }
    }
}
