package polaris.messages.outgoing.wired;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

/**
 * WiredMenuError (289): the wired menu refused an action. The official client announces
 * {@link #NOT_VARIABLE_OWNER} with a notice and reads the other codes silently.
 */
public class WiredMenuErrorComposer extends MessageComposer {
    /** Only the owner of the variable may do this (clearing it from everyone, reaching an absent holder). */
    public static final int NOT_VARIABLE_OWNER = 7;

    private final int errorCode;

    public WiredMenuErrorComposer(int errorCode) {
        this.errorCode = errorCode;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.WiredMenuErrorComposer);
        this.response.appendShort(this.errorCode);
        return this.response;
    }

    public int getErrorCode() {
        return errorCode;
    }
}
