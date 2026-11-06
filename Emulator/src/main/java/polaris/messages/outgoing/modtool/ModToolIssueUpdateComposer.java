package polaris.messages.outgoing.modtool;

import polaris.hotel.modtool.ModToolIssue;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

// The header alias is deprecated in Outgoing; the composer stays for plugin compatibility.
@SuppressWarnings("deprecation")
public class ModToolIssueUpdateComposer extends MessageComposer {
    private final ModToolIssue issue;

    public ModToolIssueUpdateComposer(ModToolIssue issue) {
        this.issue = issue;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.ModToolIssueUpdateComposer);
        this.issue.serialize(this.response);
        return this.response;
    }

    public ModToolIssue getIssue() {
        return issue;
    }
}
