package com.eu.habbo.messages.incoming.catalog.catalogadmin.studio;

import com.eu.habbo.habbohotel.catalog.versioning.CatalogLockResult;
import com.eu.habbo.habbohotel.catalog.versioning.CatalogLockService;
import com.eu.habbo.messages.outgoing.catalog.catalogadmin.studio.CatalogStudioAcquireLockComposer;

public final class CatalogStudioAcquireLockEvent extends CatalogStudioEvent {
    @Override
    public void handle() {
        if (!authorize()) return;
        CatalogStudioLockRequest request = CatalogStudioRequestParser.parseAcquire(this.packet);
        CatalogLockResult result = studio().locks()
                .acquire(request.draftVersionId(), request.key(), actorId(), CatalogLockService.DEFAULT_TTL);
        String ownerName = studio().queries().username(result.ownerId());
        this.client.sendResponse(new CatalogStudioAcquireLockComposer(
                request.operationId(),
                result.acquired(),
                result.acquired() ? "LOCK_ACQUIRED" : "LOCKED_BY_OTHER",
                result.acquired() ? "Lock acquired" : "Entity is locked by " + ownerName,
                request.draftVersionId(),
                request.key().entityType().name(),
                request.key().catalogType().name(),
                request.key().entityId(),
                result.ownerId(),
                ownerName,
                result.token().map(Object::toString).orElse(""),
                result.expiresAt()));
    }
}
