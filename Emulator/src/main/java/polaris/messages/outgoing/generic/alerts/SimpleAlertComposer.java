package polaris.messages.outgoing.generic.alerts;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class SimpleAlertComposer extends MessageComposer {
    private final String alertMessage;
    private final String titleMessage;

    public SimpleAlertComposer(String alertMessage) {
        this(alertMessage, null);
    }

    public SimpleAlertComposer(String alertMessage, String titleMessage) {
        this.alertMessage = alertMessage;
        this.titleMessage = titleMessage;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.SimpleAlertComposer);
        this.response.appendString(this.alertMessage);

        if (this.titleMessage != null && !this.titleMessage.isEmpty()) {
            this.response.appendString(this.titleMessage);
        }

        return this.response;
    }
}
