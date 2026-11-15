package com.srth.plugin.commands;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.commands.Command;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;

public class UpdateRPTexts extends Command {

    public UpdateRPTexts(String permission, String[] keys) {
        super(permission, keys);
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        
        RoleplayWebsocketsRoomsInfo.getTexts().reload();

        gameClient.getHabbo().whisper(Emulator.getTexts().getValue("commands.succes.cmd_update_rp_text"), RoomChatMessageBubbles.ALERT);

        return true;
    }
}

