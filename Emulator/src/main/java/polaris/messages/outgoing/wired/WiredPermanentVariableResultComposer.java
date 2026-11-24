package polaris.messages.outgoing.wired;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

/** Official result of a permanent-variable change in the holder detail window: whether it was applied. */
public class WiredPermanentVariableResultComposer extends MessageComposer {
    private final boolean success;

    public WiredPermanentVariableResultComposer(boolean success) {
        this.success = success;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.WiredPermanentVariableResultComposer);
        this.response.appendBoolean(this.success);
        return this.response;
    }

    public boolean isSuccess() {
        return success;
    }
}
