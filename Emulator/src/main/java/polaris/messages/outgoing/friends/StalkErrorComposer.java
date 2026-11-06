package polaris.messages.outgoing.friends;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class StalkErrorComposer extends MessageComposer {
    public static final int NOT_IN_FRIEND_LIST = 0;
    public static final int FRIEND_OFFLINE = 1;
    public static final int FRIEND_NOT_IN_ROOM = 2;
    public static final int FRIEND_BLOCKED_STALKING = 3;

    private final int errorCode;

    public StalkErrorComposer(int errorCode) {
        this.errorCode = errorCode;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.StalkErrorComposer);
        this.response.appendInt(this.errorCode);
        return this.response;
    }

    public int getErrorCode() {
        return errorCode;
    }
}
