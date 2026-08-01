package com.eu.habbo.messages.incoming.soundboard;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;

public class SoundboardRequestSettingsEvent extends MessageHandler {

    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() throws Exception {
        Habbo habbo = this.client.getHabbo();
        Room room = this.currentRoom();
        SoundboardSettingsSender.send(habbo, room);
    }
}
