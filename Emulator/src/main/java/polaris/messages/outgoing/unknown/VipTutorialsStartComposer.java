package polaris.messages.outgoing.unknown;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

/**
 * @deprecated nothing in the hotel sends this composer. Kept for plugins.
 */
@Deprecated
public class VipTutorialsStartComposer extends MessageComposer {
    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.VipTutorialsStartComposer);
        // Empty Body
        return this.response;
    }
}
