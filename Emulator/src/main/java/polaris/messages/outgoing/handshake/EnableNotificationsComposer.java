package polaris.messages.outgoing.handshake;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class EnableNotificationsComposer extends MessageComposer {
    private final boolean enabled;

    public EnableNotificationsComposer(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.EnableNotificationsComposer);
        this.response.appendBoolean(this.enabled);
        return this.response;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
