package polaris.messages.outgoing.catalog;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

/** The Furni-Matic's state for one user: open, closed, or how many seconds they still wait after an exchange. */
public class ReloadRecyclerComposer extends MessageComposer {
    public static final int STATUS_ENABLED = 1;
    public static final int STATUS_DISABLED = 2;
    public static final int STATUS_TIMEOUT = 3;

    private final int status;
    private final int secondsToWait;

    public ReloadRecyclerComposer() {
        this(STATUS_ENABLED, 0);
    }

    public ReloadRecyclerComposer(int status, int secondsToWait) {
        this.status = status;
        this.secondsToWait = secondsToWait;
    }

    /** The state to tell a user: closed wins, then a running wait. */
    public static ReloadRecyclerComposer of(boolean enabled, int secondsToWait) {
        if (!enabled) return new ReloadRecyclerComposer(STATUS_DISABLED, 0);

        return secondsToWait > 0
                ? new ReloadRecyclerComposer(STATUS_TIMEOUT, secondsToWait)
                : new ReloadRecyclerComposer(STATUS_ENABLED, 0);
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.ReloadRecyclerComposer);
        this.response.appendInt(this.status);
        this.response.appendInt(this.secondsToWait);
        return this.response;
    }

    public int getStatus() {
        return status;
    }

    public int getSecondsToWait() {
        return secondsToWait;
    }
}
