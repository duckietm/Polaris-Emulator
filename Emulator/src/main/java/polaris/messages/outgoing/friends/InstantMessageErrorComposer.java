package polaris.messages.outgoing.friends;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

/** Flash's InstantMessageErrorMessageEvent: a console message was not delivered. */
public class InstantMessageErrorComposer extends MessageComposer {
    // Error codes as the Flash client maps them to the messenger.error.* texts.
    public static final int RECEIVER_MUTED = 3;
    public static final int SENDER_MUTED = 4;
    public static final int RECEIVER_OFFLINE = 5;
    public static final int NOT_FRIEND = 6;
    public static final int RECEIVER_BUSY = 7;
    public static final int SEND_FAILED = 10;

    private final int errorCode;
    private final int userId;
    private final String message;

    public InstantMessageErrorComposer(int errorCode, int userId, String message) {
        this.errorCode = errorCode;
        this.userId = userId;
        this.message = message;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.InstantMessageErrorComposer);
        this.response.appendInt(this.errorCode);
        this.response.appendInt(this.userId);
        this.response.appendString(this.message);
        return this.response;
    }

    public int getErrorCode() {
        return errorCode;
    }

    public int getUserId() {
        return userId;
    }

    public String getMessage() {
        return message;
    }
}
