package polaris.messages.outgoing.friends;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class FriendRequestErrorComposer extends MessageComposer {
    public static final int FRIEND_LIST_OWN_FULL = 1;
    public static final int FRIEND_LIST_TARGET_FULL = 2;
    public static final int TARGET_NOT_ACCEPTING_REQUESTS = 3;
    public static final int TARGET_NOT_FOUND = 4;

    private final int errorCode;

    public FriendRequestErrorComposer(int errorCode) {
        this.errorCode = errorCode;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.FriendRequestErrorComposer);
        this.response.appendInt(0);
        this.response.appendInt(this.errorCode);
        return this.response;
    }

    public int getErrorCode() {
        return errorCode;
    }
}
