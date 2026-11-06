package polaris.messages.outgoing.friends;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class FriendFindingRoomComposer extends MessageComposer {
    public static final int NO_ROOM_FOUND = 0;
    public static final int ROOM_FOUND = 1;

    private final int errorCode;

    public FriendFindingRoomComposer(int errorCode) {
        this.errorCode = errorCode;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.FriendFindingRoomComposer);
        this.response.appendBoolean(this.errorCode == ROOM_FOUND);
        return this.response;
    }

    public int getErrorCode() {
        return errorCode;
    }
}
