package com.eu.habbo.messages.incoming.soundboard;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.soundboard.SoundboardManager;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;

public class SoundboardSetEnabledEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 1000;
    }

    @Override
    public void handle() throws Exception {
        Habbo habbo = this.client.getHabbo();
        if (habbo == null) return;

        Room room = this.currentRoom();
        if (room == null) return;

        if (!canToggle(habbo, room)) return;

        boolean enabled = this.packet.readInt() == 1;

        room.setSoundboardEnabled(enabled);
        SoundboardManager manager = Emulator.getGameEnvironment().getSoundboardManager();
        manager.setRoomEnabled(room.getId(), enabled);

        SoundboardSettingsSender.sendToRoom(room, manager);
    }

    static boolean canToggle(Habbo habbo, Room room) {
        if (habbo == null || room == null) return false;

        return room.getOwnerId() == habbo.getHabboInfo().getId() || habbo.hasPermission(Permission.ACC_ANYROOMOWNER);
    }
}
