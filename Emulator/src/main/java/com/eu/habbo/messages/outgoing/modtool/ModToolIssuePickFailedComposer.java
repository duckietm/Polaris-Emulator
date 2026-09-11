package com.eu.habbo.messages.outgoing.modtool;

import com.eu.habbo.habbohotel.modtool.ModToolIssue;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;
import java.util.List;

/**
 * The tickets a moderator asked for and could not have, each with the colleague who took it first.
 * The mod tool window says so where it would otherwise silently do nothing.
 */
public class ModToolIssuePickFailedComposer extends MessageComposer {
    private final List<ModToolIssue> issues;
    private final boolean retryEnabled;
    private final int retryCount;

    public ModToolIssuePickFailedComposer(ModToolIssue issue) {
        this(List.of(issue), false, 0);
    }

    public ModToolIssuePickFailedComposer(List<ModToolIssue> issues, boolean retryEnabled, int retryCount) {
        this.issues = issues;
        this.retryEnabled = retryEnabled;
        this.retryCount = retryCount;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.ModToolIssuePickFailedComposer);
        this.response.appendInt(this.issues.size());

        for (ModToolIssue issue : this.issues) {
            this.response.appendInt(issue.id);
            this.response.appendInt(issue.modId);
            this.response.appendString(issue.modName == null ? "" : issue.modName);
        }

        this.response.appendBoolean(this.retryEnabled);
        this.response.appendInt(this.retryCount);
        return this.response;
    }

    public List<ModToolIssue> getIssues() {
        return issues;
    }

    public boolean isRetryEnabled() {
        return retryEnabled;
    }

    public int getRetryCount() {
        return retryCount;
    }
}
