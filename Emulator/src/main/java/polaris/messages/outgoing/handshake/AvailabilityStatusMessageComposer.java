package polaris.messages.outgoing.handshake;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class AvailabilityStatusMessageComposer extends MessageComposer {
    private final boolean isOpen;
    private final boolean isShuttingDown;
    private final boolean isAuthenticUser;

    public AvailabilityStatusMessageComposer(boolean isOpen, boolean isShuttingDown, boolean isAuthenticUser) {
        this.isOpen = isOpen;
        this.isShuttingDown = isShuttingDown;
        this.isAuthenticUser = isAuthenticUser;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.AvailabilityStatusMessageComposer);

        this.response.appendBoolean(isOpen); // isOpen
        this.response.appendBoolean(isShuttingDown); // onShutdown
        this.response.appendBoolean(isAuthenticUser); // isAuthenticUser
        return this.response;
    }

    public boolean isOpen() {
        return isOpen;
    }

    public boolean isShuttingDown() {
        return isShuttingDown;
    }

    public boolean isAuthenticUser() {
        return isAuthenticUser;
    }
}
