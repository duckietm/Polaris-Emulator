package polaris.messages.outgoing.modtool;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class HelperRequestDisabledComposer extends MessageComposer {
    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.HelperRequestDisabledComposer);
        this.response.appendString("");
        return this.response;
    }
}
