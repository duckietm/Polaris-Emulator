package polaris.messages.incoming.soundboard;

import polaris.Emulator;
import polaris.hotel.rooms.Room;
import polaris.hotel.soundboard.SoundboardManager;
import polaris.hotel.soundboard.SoundboardRoomMode;
import polaris.hotel.soundboard.SoundboardSound;
import polaris.hotel.users.User;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.soundboard.SoundboardPlayComposer;
import polaris.messages.outgoing.soundboard.SoundboardPlayDeniedComposer;

public class SoundboardPlayEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 250;
    }

    @Override
    public void handle() throws Exception {
        User user = this.client.getUser();
        if (user == null) return;

        Room room = this.currentRoom();
        if (room == null) {
            this.sendDenied(SoundboardPlayDeniedComposer.Reason.UNAVAILABLE, 0);
            return;
        }
        SoundboardRoomMode mode = room.getSoundboardMode();
        if (!mode.enabled()) {
            this.sendDenied(SoundboardPlayDeniedComposer.Reason.ROOM_DISABLED, 0);
            return;
        }
        if (!mode.allows(room.hasRights(user))) {
            this.sendDenied(SoundboardPlayDeniedComposer.Reason.RIGHTS_REQUIRED, 0);
            return;
        }

        int soundId = this.packet.readInt();
        SoundboardManager.PlayDecision decision = Emulator.getGameEnvironment()
                .getSoundboardManager()
                .tryPlay(
                        user.getUserInfo().getId(),
                        user.getUserInfo().getRank().getId(),
                        soundId,
                        System.currentTimeMillis());
        if (!decision.allowed()) {
            switch (decision.denialReason()) {
                case COOLDOWN ->
                    this.sendDenied(SoundboardPlayDeniedComposer.Reason.COOLDOWN, decision.remainingSeconds());
                case PAD_COOLDOWN ->
                    this.sendDenied(SoundboardPlayDeniedComposer.Reason.PAD_COOLDOWN, decision.remainingSeconds());
                default -> this.sendDenied(SoundboardPlayDeniedComposer.Reason.UNAVAILABLE, 0);
            }
            return;
        }

        SoundboardSound sound = decision.sound();
        room.sendComposer(new SoundboardPlayComposer(
                        sound.id,
                        sound.url,
                        sound.classname,
                        sound.name,
                        user.getUserInfo().getId(),
                        user.getRoomUnit().getId(),
                        user.getUserInfo().getUsername())
                .compose());
    }

    private void sendDenied(SoundboardPlayDeniedComposer.Reason reason, int remainingSeconds) {
        this.client.sendResponse(new SoundboardPlayDeniedComposer(reason, remainingSeconds).compose());
    }
}
