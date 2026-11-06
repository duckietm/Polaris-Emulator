package polaris.messages.outgoing.modtool;

import java.util.List;
import polaris.hotel.modtool.ModToolIssue;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

/**
 * The sender's still-open calls for help (header 1121): one row each with the call id, when it was
 * made and what it said. The client shows the first one and asks whether to keep or discard them.
 */
public class ReportRoomFormComposer extends MessageComposer {
    private final List<ModToolIssue> pendingIssues;

    public ReportRoomFormComposer(List<ModToolIssue> issues) {
        this.pendingIssues = issues;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.ReportRoomFormComposer);
        this.response.appendInt(this.pendingIssues.size()); // Current standing help request(s) amount:

        for (ModToolIssue issue : this.pendingIssues) {
            this.response.appendString(issue.id + "");
            this.response.appendString(issue.timestamp + "");
            this.response.appendString(issue.message);
        }
        return this.response;
    }

    public List<ModToolIssue> getPendingIssues() {
        return pendingIssues;
    }
}
