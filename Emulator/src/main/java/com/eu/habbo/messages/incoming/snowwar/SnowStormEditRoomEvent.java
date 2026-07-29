package com.eu.habbo.messages.incoming.snowwar;

import com.eu.habbo.habbohotel.games.snowwar.SnowWarManager;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;

/**
 * Header 6010: a permitted user (acc_snowwar_edit, rank 7 by default) opens the
 * in-game arena editor - either from a running game or straight from the queue.
 * The manager takes the player out of any game/queue, freezes matchmaking and
 * ends every running game (nothing may tick behind the editor), then sends the
 * editor the current arena map to edit. The client switches to edit mode and
 * publishes changes with the save-editor packet (6011).
 */
public class SnowStormEditRoomEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        Habbo habbo = this.client.getHabbo();
        if (habbo == null || !habbo.hasPermission(SnowWarManager.EDIT_PERMISSION)) {
            return;
        }

        SnowWarManager.getInstance().enterEditor(habbo);
    }
}
