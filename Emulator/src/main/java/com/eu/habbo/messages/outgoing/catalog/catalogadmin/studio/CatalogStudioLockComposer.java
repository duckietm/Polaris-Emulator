package com.eu.habbo.messages.outgoing.catalog.catalogadmin.studio;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.Outgoing;
import java.time.Instant;

public final class CatalogStudioLockComposer extends AbstractCatalogStudioLockComposer {
    private final int header;

    public CatalogStudioLockComposer(
            int header,
            String operationId,
            boolean success,
            String code,
            String message,
            long draftVersionId,
            String entityType,
            String catalogType,
            int entityId,
            int ownerId,
            String ownerName,
            String token,
            Instant expiresAt) {
        super(
                operationId,
                success,
                code,
                message,
                draftVersionId,
                entityType,
                catalogType,
                entityId,
                ownerId,
                ownerName,
                token,
                expiresAt);
        if (header != Outgoing.CatalogStudioAcquireLockComposer && header != Outgoing.CatalogStudioRenewLockComposer) {
            throw new IllegalArgumentException("Unsupported catalog lock response header: " + header);
        }
        this.header = header;
    }

    public CatalogStudioLockComposer(
            int header,
            String operationId,
            boolean success,
            String code,
            String message,
            long draftVersionId,
            String entityType,
            int entityId,
            int ownerId,
            String ownerName,
            String token,
            Instant expiresAt) {
        this(
                header,
                operationId,
                success,
                code,
                message,
                draftVersionId,
                entityType,
                "NORMAL",
                entityId,
                ownerId,
                ownerName,
                token,
                expiresAt);
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(header);
        return appendPayload();
    }
}
