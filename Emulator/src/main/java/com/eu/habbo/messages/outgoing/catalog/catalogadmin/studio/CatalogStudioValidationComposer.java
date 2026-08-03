package com.eu.habbo.messages.outgoing.catalog.catalogadmin.studio;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;
import java.util.List;
import java.util.Objects;

public final class CatalogStudioValidationComposer extends MessageComposer {
    private final String operationId;
    private final boolean success;
    private final String code;
    private final String message;
    private final long revision;
    private final boolean current;
    private final List<CatalogStudioValidationIssue> issues;

    public CatalogStudioValidationComposer(
            String operationId,
            boolean success,
            String code,
            String message,
            long revision,
            boolean current,
            List<CatalogStudioValidationIssue> issues) {
        this.operationId = Objects.requireNonNull(operationId, "operationId");
        this.success = success;
        this.code = Objects.requireNonNull(code, "code");
        this.message = Objects.requireNonNull(message, "message");
        this.revision = revision;
        this.current = current;
        this.issues = List.copyOf(issues);
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.CatalogStudioValidationComposer);
        this.response.appendString(operationId);
        this.response.appendBoolean(success);
        this.response.appendString(code);
        this.response.appendString(message);
        this.response.appendInt(Math.toIntExact(revision));
        this.response.appendBoolean(current);
        this.response.appendInt(issues.size());
        for (CatalogStudioValidationIssue issue : issues) {
            this.response.appendString(issue.code());
            this.response.appendString(issue.entityType());
            this.response.appendInt(issue.entityId());
            this.response.appendString(issue.field());
            this.response.appendString(issue.message());
        }
        return this.response;
    }
}
