package polaris.messages.incoming.soundboard;

import polaris.hotel.rooms.Room;
import polaris.hotel.users.User;
import polaris.messages.incoming.MessageHandler;

public class SoundboardRequestSettingsEvent extends MessageHandler {

    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() throws Exception {
        User user = this.client.getUser();
        Room room = this.currentRoom();
        SoundboardSettingsSender.send(user, room);
    }
}
