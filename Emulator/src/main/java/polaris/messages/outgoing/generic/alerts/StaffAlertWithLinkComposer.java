package polaris.messages.outgoing.generic.alerts;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class StaffAlertWithLinkComposer extends MessageComposer {
    private final String message;
    private final String link;

    public StaffAlertWithLinkComposer(String message, String link) {
        this.message = message;
        this.link = link;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.StaffAlertWithLinkComposer);
        this.response.appendString(this.message);
        this.response.appendString(this.link);
        return this.response;
    }

    public String getMessage() {
        return message;
    }

    public String getLink() {
        return link;
    }
}
