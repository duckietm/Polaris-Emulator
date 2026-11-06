package polaris.messages.outgoing.modtool;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

/**
 * Confirms that the sender's pending calls for help are gone (header 77, empty body). The name is
 * kept because plugins compile against it.
 */
public class CallForHelpPendingCallsDeletedComposer extends MessageComposer {
    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.CallForHelpPendingCallsDeletedComposer);
        // Empty body
        return this.response;
    }
}
