package polaris.messages.incoming.soundboard;

import polaris.Emulator;
import polaris.hotel.permissions.Permission;
import polaris.hotel.rooms.Room;
import polaris.hotel.soundboard.SoundboardManager;
import polaris.hotel.soundboard.SoundboardRoomMode;
import polaris.hotel.users.User;
import polaris.messages.incoming.MessageHandler;

public class SoundboardSetEnabledEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 1000;
    }

    @Override
    public void handle() throws Exception {
        User user = this.client.getUser();
        if (user == null) return;

        Room room = this.currentRoom();
        if (room == null) return;

        if (!canToggle(user, room)) {
            // The client flips its switch before the server answers; say what is true so it can put it back.
            SoundboardSettingsSender.send(user, room);
            return;
        }

        // 0 and 1 are what every client has always sent; 2 limits the pads to people with rights.
        SoundboardRoomMode mode = SoundboardRoomMode.fromWire(this.packet.readInt());

        room.setSoundboardMode(mode);
        SoundboardManager manager = Emulator.getGameEnvironment().getSoundboardManager();
        manager.setRoomMode(room.getId(), mode);

        SoundboardSettingsSender.sendToRoom(room, manager);
    }

    static boolean canToggle(User user, Room room) {
        if (user == null || room == null) return false;

        return room.getOwnerId() == user.getUserInfo().getId() || user.hasPermission(Permission.ACC_ANYROOMOWNER);
    }
}
