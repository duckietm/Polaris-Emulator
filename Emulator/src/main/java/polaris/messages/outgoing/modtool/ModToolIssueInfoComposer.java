package polaris.messages.outgoing.modtool;

import polaris.hotel.modtool.ModToolIssue;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class ModToolIssueInfoComposer extends MessageComposer {
    private final ModToolIssue issue;

    public ModToolIssueInfoComposer(ModToolIssue issue) {
        this.issue = issue;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.ModToolIssueInfoComposer);
        this.issue.serialize(this.response);
        return this.response;
    }

    public ModToolIssue getIssue() {
        return issue;
    }
}
