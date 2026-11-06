package polaris.messages.outgoing.users;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class InClientLinkComposer extends MessageComposer {
    private final String link;

    public InClientLinkComposer(String link) {
        this.link = link;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.InClientLinkComposer);
        this.response.appendString(this.link);
        return this.response;
    }
}
