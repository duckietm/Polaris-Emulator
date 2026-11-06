package polaris.messages.outgoing.handshake;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class ConnectionErrorComposer extends MessageComposer {
    private final int messageId;
    private final int errorCode;
    private final String timestamp;

    public ConnectionErrorComposer(int errorCode) {
        this.messageId = 0;
        this.errorCode = errorCode;
        this.timestamp = "";
    }

    public ConnectionErrorComposer(int messageId, int errorCode, String timestamp) {
        this.messageId = messageId;
        this.errorCode = errorCode;
        this.timestamp = timestamp;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.ConnectionErrorComposer);
        this.response.appendInt(this.messageId);
        this.response.appendInt(this.errorCode);
        this.response.appendString(this.timestamp);

        return this.response;
    }

    public int getMessageId() {
        return messageId;
    }

    public int getErrorCode() {
        return errorCode;
    }

    public String getTimestamp() {
        return timestamp;
    }
}
