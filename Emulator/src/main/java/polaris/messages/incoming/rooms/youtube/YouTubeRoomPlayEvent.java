package polaris.messages.incoming.rooms.youtube;

import java.util.ArrayList;
import java.util.List;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.User;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.rooms.youtube.YouTubeRoomBroadcastComposer;

public class YouTubeRoomPlayEvent extends MessageHandler {

    private static final int MAX_VIDEO_ID_LENGTH = 100;
    private static final int MAX_PLAYLIST_ITEM_LENGTH = 200;
    private static final int MAX_PLAYLIST_SIZE = 50;

    @Override
    public int getRatelimit() {
        // Max 1 broadcast every 2 seconds per client
        return 2000;
    }

    @Override
    public void handle() throws Exception {
        User user = this.client.getUser();
        if (user == null) return;

        Room room = user.getUserInfo().getCurrentRoom();
        if (room == null) return;
        if (!room.isYoutubeEnabled()) return;
        if (!room.isOwner(user) && !room.hasRights(user)) return;

        String videoId = this.packet.readString();
        if (videoId.length() > MAX_VIDEO_ID_LENGTH) {
            videoId = videoId.substring(0, MAX_VIDEO_ID_LENGTH);
        }

        int playlistCount = this.packet.readInt();
        if (playlistCount > MAX_PLAYLIST_SIZE) playlistCount = MAX_PLAYLIST_SIZE;
        if (playlistCount < 0) playlistCount = 0;

        List<String> playlist = new ArrayList<>();
        for (int i = 0; i < playlistCount; i++) {
            String item = this.packet.readString();
            if (item.length() > MAX_PLAYLIST_ITEM_LENGTH) {
                item = item.substring(0, MAX_PLAYLIST_ITEM_LENGTH);
            }
            playlist.add(item);
        }

        // Store the current video + playlist on the room, or clear if empty
        if (videoId.isEmpty()) {
            room.clearYoutubeVideo();
        } else {
            room.setYoutubeVideo(videoId, user.getUserInfo().getUsername(), playlist);
        }

        // Broadcast to everyone in the room (empty videoId = stop)
        room.sendComposer(
                new YouTubeRoomBroadcastComposer(videoId, user.getUserInfo().getUsername(), playlist).compose());
    }
}
