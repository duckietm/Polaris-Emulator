package polaris.messages.outgoing.modtool;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class ModToolIssueResponseAlertComposer extends MessageComposer {
    private final String message;

    public ModToolIssueResponseAlertComposer(String message) {
        this.message = message;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.ModToolIssueResponseAlertComposer);
        this.response.appendString(this.message);
        return this.response;
    }

    public String getMessage() {
        return message;
    }
}
