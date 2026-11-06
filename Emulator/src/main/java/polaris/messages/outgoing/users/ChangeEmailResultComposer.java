package polaris.messages.outgoing.users;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class ChangeEmailResultComposer extends MessageComposer {
    private final int result;

    public ChangeEmailResultComposer(int result) {
        this.result = result;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.ChangeEmailResultComposer);
        this.response.appendInt(this.result);
        return this.response;
    }

    public int getResult() {
        return result;
    }
}
