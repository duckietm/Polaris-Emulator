package polaris.messages.outgoing.generic.alerts;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class UserBannedComposer extends MessageComposer {
    private final String message;

    public UserBannedComposer(String message) {
        this.message = message;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.UserBannedComposer);
        this.response.appendString(this.message);
        return this.response;
    }

    public String getMessage() {
        return message;
    }
}
