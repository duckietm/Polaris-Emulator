package polaris.messages.outgoing.housekeeping;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

/**
 * Generic ack for any housekeeping action (ban, mute, kick, give-credits,
 * room-close, …). The client matches it back to the originating call via
 * the `actionKey` field, which lets multiple in-flight actions share the
 * same event stream without ordering bugs.
 */
public class HousekeepingActionResultComposer extends MessageComposer {
    private final String actionKey;
    private final boolean ok;
    private final int actionId;
    private final String message;

    public HousekeepingActionResultComposer(String actionKey, boolean ok, int actionId, String message) {
        this.actionKey = actionKey != null ? actionKey : "";
        this.ok = ok;
        this.actionId = actionId;
        this.message = message != null ? message : "";
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.HousekeepingActionResultComposer);
        this.response.appendString(this.actionKey);
        this.response.appendBoolean(this.ok);
        this.response.appendInt(this.actionId);
        this.response.appendString(this.message);

        return this.response;
    }
}
