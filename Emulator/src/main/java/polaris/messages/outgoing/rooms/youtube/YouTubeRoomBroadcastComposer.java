package polaris.messages.outgoing.rooms.youtube;

import java.util.List;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class YouTubeRoomBroadcastComposer extends MessageComposer {
    private final String videoId;
    private final String senderName;
    private final List<String> playlist;

    public YouTubeRoomBroadcastComposer(String videoId, String senderName, List<String> playlist) {
        this.videoId = videoId;
        this.senderName = senderName;
        this.playlist = playlist;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.YouTubeRoomBroadcastComposer);
        this.response.appendString(this.videoId);
        this.response.appendString(this.senderName);
        this.response.appendInt(this.playlist.size());
        for (String id : this.playlist) {
            this.response.appendString(id);
        }
        return this.response;
    }
}
