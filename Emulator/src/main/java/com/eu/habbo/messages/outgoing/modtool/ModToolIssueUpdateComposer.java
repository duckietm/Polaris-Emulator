package com.eu.habbo.messages.outgoing.modtool;

import com.eu.habbo.habbohotel.modtool.ModToolIssue;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

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
