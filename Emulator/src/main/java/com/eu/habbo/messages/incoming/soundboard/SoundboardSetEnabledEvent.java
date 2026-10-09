package com.eu.habbo.messages.incoming.soundboard;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.soundboard.SoundboardManager;
import com.eu.habbo.habbohotel.soundboard.SoundboardRoomMode;
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

        if (!canToggle(habbo, room)) {
            // The client flips its switch before the server answers; say what is true so it can put it back.
            SoundboardSettingsSender.send(habbo, room);
            return;
        }

        // 0 and 1 are what every client has always sent; 2 limits the pads to people with rights.
        SoundboardRoomMode mode = SoundboardRoomMode.fromWire(this.packet.readInt());

        room.setSoundboardMode(mode);
        SoundboardManager manager = Emulator.getGameEnvironment().getSoundboardManager();
        manager.setRoomMode(room.getId(), mode);

        SoundboardSettingsSender.sendToRoom(room, manager);
    }

    static boolean canToggle(Habbo habbo, Room room) {
        if (habbo == null || room == null) return false;

        return room.getOwnerId() == habbo.getHabboInfo().getId() || habbo.hasPermission(Permission.ACC_ANYROOMOWNER);
    }
}
