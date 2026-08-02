package com.eu.habbo.messages.incoming.soundboard;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.soundboard.SoundboardManager;
import com.eu.habbo.habbohotel.soundboard.SoundboardSound;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.soundboard.SoundboardPlayComposer;
import com.eu.habbo.messages.outgoing.soundboard.SoundboardPlayDeniedComposer;

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
        if (room == null) {
            this.sendDenied(SoundboardPlayDeniedComposer.Reason.UNAVAILABLE, 0);
            return;
        }
        if (!room.isSoundboardEnabled()) {
            this.sendDenied(SoundboardPlayDeniedComposer.Reason.ROOM_DISABLED, 0);
            return;
        }

        int soundId = this.packet.readInt();
        SoundboardManager.PlayDecision decision = Emulator.getGameEnvironment()
                .getSoundboardManager()
                .tryPlay(
                        habbo.getHabboInfo().getId(),
                        habbo.getHabboInfo().getRank().getId(),
                        soundId,
                        System.currentTimeMillis());
        if (!decision.allowed()) {
            if (decision.denialReason() == SoundboardManager.DenialReason.COOLDOWN) {
                this.sendDenied(SoundboardPlayDeniedComposer.Reason.COOLDOWN, decision.remainingSeconds());
            } else {
                this.sendDenied(SoundboardPlayDeniedComposer.Reason.UNAVAILABLE, 0);
            }
            return;
        }

        SoundboardSound sound = decision.sound();
        room.sendComposer(new SoundboardPlayComposer(
                        sound.id,
                        sound.url,
                        sound.name,
                        habbo.getHabboInfo().getId(),
                        habbo.getRoomUnit().getId(),
                        habbo.getHabboInfo().getUsername())
                .compose());
    }

    private void sendDenied(SoundboardPlayDeniedComposer.Reason reason, int remainingSeconds) {
        this.client.sendResponse(new SoundboardPlayDeniedComposer(reason, remainingSeconds).compose());
    }
}
