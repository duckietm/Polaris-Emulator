package polaris.messages.outgoing.modtool;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class IssueDeletedComposer extends MessageComposer {
    private final int issueId;

    public IssueDeletedComposer(int issueId) {
        this.issueId = issueId;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.IssueDeletedComposer);
        this.response.appendString(Integer.toString(this.issueId));
        return this.response;
    }
}
