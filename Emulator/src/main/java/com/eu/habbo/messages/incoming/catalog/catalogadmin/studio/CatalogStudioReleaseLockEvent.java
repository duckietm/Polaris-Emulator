package com.eu.habbo.messages.incoming.catalog.catalogadmin.studio;

import com.eu.habbo.messages.outgoing.catalog.catalogadmin.studio.CatalogStudioReleaseLockComposer;

public final class CatalogStudioReleaseLockEvent extends CatalogStudioEvent {
    @Override
    public void handle() {
        if (!authorize()) return;
        CatalogStudioLockRequest request = CatalogStudioRequestParser.parseTokenLock(this.packet);
        boolean released =
                studio().locks().release(request.draftVersionId(), request.key(), actorId(), request.token());
        long revision = studio().queries().loadSession().revision();
        this.client.sendResponse(new CatalogStudioReleaseLockComposer(
                request.operationId(),
                released,
                released ? "LOCK_RELEASED" : "LOCK_NOT_OWNED",
                released ? "Lock released" : "Lock could not be released",
                revision));
    }
}
