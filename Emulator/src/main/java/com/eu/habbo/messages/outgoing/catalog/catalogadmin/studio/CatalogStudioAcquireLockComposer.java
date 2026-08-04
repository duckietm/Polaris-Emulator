package com.eu.habbo.messages.outgoing.catalog.catalogadmin.studio;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.Outgoing;
import java.time.Instant;

public final class CatalogStudioAcquireLockComposer extends AbstractCatalogStudioLockComposer {
    public CatalogStudioAcquireLockComposer(
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
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.CatalogStudioAcquireLockComposer);
        return appendPayload();
    }
}
