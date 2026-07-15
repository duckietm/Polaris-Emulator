package com.eu.habbo.messages.outgoing.modtool;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

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
