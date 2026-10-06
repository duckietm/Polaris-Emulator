package com.eu.habbo.messages.outgoing.unknown;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

/** Flash's InstantMessageErrorMessageEvent: a console message was not delivered. */
public class UnknownMessengerErrorComposer extends MessageComposer {
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

    public UnknownMessengerErrorComposer(int errorCode, int userId, String message) {
        this.errorCode = errorCode;
        this.userId = userId;
        this.message = message;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.UnknownMessengerErrorComposer);
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
