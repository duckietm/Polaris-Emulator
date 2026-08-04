package com.eu.habbo.messages.incoming.catalog.catalogadmin.studio;

import com.eu.habbo.habbohotel.catalog.versioning.CatalogLockResult;
import com.eu.habbo.habbohotel.catalog.versioning.CatalogLockService;
import com.eu.habbo.messages.outgoing.catalog.catalogadmin.studio.CatalogStudioRenewLockComposer;

public final class CatalogStudioRenewLockEvent extends CatalogStudioEvent {
    @Override
    public void handle() {
        if (!authorize()) return;
        CatalogStudioLockRequest request = CatalogStudioRequestParser.parseTokenLock(this.packet);
        CatalogLockResult result = studio().locks()
                .renew(
                        request.draftVersionId(),
                        request.key(),
                        actorId(),
                        request.token(),
                        CatalogLockService.DEFAULT_TTL);
        String ownerName = result.ownerId() > 0 ? studio().queries().username(result.ownerId()) : "";
        this.client.sendResponse(new CatalogStudioRenewLockComposer(
                request.operationId(),
                result.acquired(),
                result.acquired() ? "LOCK_RENEWED" : "LOCK_EXPIRED",
                result.acquired() ? "Lock renewed" : "Lock is missing or expired",
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
