package polaris.messages.outgoing.rooms;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class DoorbellAddUserComposer extends MessageComposer {
    private final String user;

    public DoorbellAddUserComposer(String user) {
        this.user = user;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.DoorbellAddUserComposer);
        this.response.appendString(this.user);
        return this.response;
    }

    public String getUser() {
        return user;
    }
}
