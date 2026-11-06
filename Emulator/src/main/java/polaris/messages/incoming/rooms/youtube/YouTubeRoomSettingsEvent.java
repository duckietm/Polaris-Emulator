package polaris.messages.incoming.rooms.youtube;

import polaris.hotel.rooms.Room;
import polaris.hotel.users.User;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.rooms.youtube.YouTubeRoomBroadcastComposer;
import polaris.messages.outgoing.rooms.youtube.YouTubeRoomSettingsComposer;

public class YouTubeRoomSettingsEvent extends MessageHandler {

    @Override
    public int getRatelimit() {
        return 200;
    }

    @Override
    public void handle() throws Exception {
        User user = this.client.getUser();
        if (user == null) return;

        Room room = user.getUserInfo().getCurrentRoom();
        if (room == null) return;
        if (!room.isOwner(user)) return;

        boolean enabled = this.packet.readInt() == 1;
        room.setYoutubeEnabled(enabled);
        room.setNeedsUpdate(true);
        room.sendComposer(new YouTubeRoomSettingsComposer(enabled).compose());

        if (!enabled && !room.getYoutubeCurrentVideo().isEmpty()) {
            room.clearYoutubeVideo();
            room.sendComposer(new YouTubeRoomBroadcastComposer("", "", java.util.Collections.emptyList()).compose());
        }
    }
}
