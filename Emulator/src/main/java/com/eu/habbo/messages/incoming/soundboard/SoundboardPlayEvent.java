package com.eu.habbo.messages.incoming.soundboard;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.soundboard.SoundboardSound;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.soundboard.SoundboardPlayComposer;

public class SoundboardPlayEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 250;
    }

    @Override
    public void handle() throws Exception {
        Habbo habbo = this.client.getHabbo();
        if (habbo == null) return;

        Room room = this.currentRoom();
        if (room == null || !room.isSoundboardEnabled()) return;

        int soundId = this.packet.readInt();
        SoundboardSound sound = Emulator.getGameEnvironment().getSoundboardManager().getSound(soundId);
        if (sound == null) return;

        // Broadcast to everyone in the room.
        room.sendComposer(new SoundboardPlayComposer(sound.id, sound.url, habbo.getHabboInfo().getUsername()).compose());
    }
}
