package polaris.messages.outgoing.users;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.UnsupportedOutgoing;

public class UserBCLimitsComposer extends MessageComposer {
    @Override
    protected ServerMessage composeInternal() {
        this.response.init(UnsupportedOutgoing.UserBCLimitsComposer);
        this.response.appendInt(0);
        this.response.appendInt(500);
        this.response.appendInt(0);
        return this.response;
    }
}
