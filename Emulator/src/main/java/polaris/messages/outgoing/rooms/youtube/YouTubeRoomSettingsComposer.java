package polaris.messages.outgoing.rooms.youtube;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class YouTubeRoomSettingsComposer extends MessageComposer {
    private final boolean youtubeEnabled;

    public YouTubeRoomSettingsComposer(boolean youtubeEnabled) {
        this.youtubeEnabled = youtubeEnabled;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.YouTubeRoomSettingsComposer);
        this.response.appendInt(this.youtubeEnabled ? 1 : 0);
        return this.response;
    }
}
