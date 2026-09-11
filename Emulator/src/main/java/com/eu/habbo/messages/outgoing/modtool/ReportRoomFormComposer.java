package com.eu.habbo.messages.outgoing.modtool;

import com.eu.habbo.habbohotel.modtool.ModToolIssue;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;
import java.util.List;

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
