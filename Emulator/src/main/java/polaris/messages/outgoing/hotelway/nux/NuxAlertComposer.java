package polaris.messages.outgoing.hotelway.nux;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class NuxAlertComposer extends MessageComposer {
    private final String link;

    public NuxAlertComposer(String link) {
        this.link = link;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.NuxAlertComposer);
        this.response.appendString(this.link);
        return this.response;
    }

    public String getLink() {
        return link;
    }
}
