package polaris.messages.outgoing.rooms.items.youtube;

import polaris.hotel.rooms.BuildersClubItemIds;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class YoutubeStateChangeComposer extends MessageComposer {
    private final int furniId;
    private final int state;

    public YoutubeStateChangeComposer(int furniId, int state) {
        this.furniId = furniId;
        this.state = state;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.YoutubeMessageComposer3);
        this.response.appendInt(BuildersClubItemIds.toWire(this.furniId));
        this.response.appendInt(this.state);

        return this.response;
    }

    public int getFurniId() {
        return furniId;
    }

    public int getState() {
        return state;
    }
}
