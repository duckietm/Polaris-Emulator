package polaris.messages.outgoing.rooms;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class FavoriteRoomChangedComposer extends MessageComposer {
    private final int roomId;
    private final boolean favorite;

    public FavoriteRoomChangedComposer(int roomId, boolean favorite) {
        this.roomId = roomId;
        this.favorite = favorite;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.FavoriteRoomChangedComposer);
        this.response.appendInt(this.roomId);
        this.response.appendBoolean(this.favorite);
        return this.response;
    }

    public int getRoomId() {
        return roomId;
    }

    public boolean isFavorite() {
        return favorite;
    }
}
