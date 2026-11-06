package polaris.messages.incoming.rooms.youtube;

import polaris.hotel.rooms.Room;
import polaris.hotel.users.User;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.rooms.youtube.YouTubeRoomWatchersComposer;

public class YouTubeRoomWatchingEvent extends MessageHandler {

    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() throws Exception {
        User user = this.client.getUser();
        if (user == null) return;

        Room room = user.getUserInfo().getCurrentRoom();
        if (room == null) return;

        boolean watching = this.packet.readInt() == 1;
        int userId = user.getUserInfo().getId();

        boolean changed;
        if (watching) {
            changed = room.getYoutubeWatchers().add(userId);
        } else {
            changed = room.getYoutubeWatchers().remove(userId);
        }

        if (changed) {
            room.sendComposer(new YouTubeRoomWatchersComposer(room.getYoutubeWatchers()).compose());
        }
    }
}
