package com.eu.habbo.messages.incoming.catalog.catalogadmin;

import com.eu.habbo.habbohotel.catalog.CatalogPageType;
import com.eu.habbo.habbohotel.catalog.versioning.CatalogChangeOperation;
import com.eu.habbo.habbohotel.catalog.versioning.CatalogDraftMutationRequest;
import com.eu.habbo.habbohotel.catalog.versioning.CatalogEntityType;
import com.eu.habbo.habbohotel.catalog.versioning.CatalogLockKey;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.incoming.catalog.catalogadmin.studio.CatalogStudioMutationEnvelope;
import com.eu.habbo.messages.incoming.catalog.catalogadmin.studio.CatalogStudioRequestParser;
import com.eu.habbo.messages.incoming.catalog.catalogadmin.studio.CatalogStudioRuntime;
import com.eu.habbo.messages.outgoing.catalog.catalogadmin.CatalogAdminResultComposer;

public class CatalogAdminDeletePageEvent extends MessageHandler {

    @Override
    public void handle() throws Exception {
        if (!this.client.getHabbo().hasPermission(Permission.ACC_CATALOGFURNI)) {
            this.client.sendResponse(new CatalogAdminResultComposer(false, "No permission"));
            return;
        }

        int pageId = this.packet.readInt();
        CatalogPageType pageType = CatalogPageType.fromString(this.packet.readString());

        CatalogStudioMutationEnvelope envelope = CatalogStudioRequestParser.parseMutationEnvelope(this.packet);
        var mutations = CatalogStudioRuntime.services().mutations();
        var draft = mutations.loadDraft(envelope.draftVersionId(), envelope.expectedRevision());
        if (draft.page(pageType, pageId).isEmpty()) {
            this.client.sendResponse(
                    new CatalogAdminResultComposer(false, "Page not found in shared draft: " + pageId));
            return;
        }
        if (draft.pages().stream().anyMatch(page -> page.parentId() == pageId)) {
            this.client.sendResponse(new CatalogAdminResultComposer(false, "Move or delete child pages first"));
            return;
        }
        if (draft.offers().stream().anyMatch(offer -> offer.pageId() == pageId)) {
            this.client.sendResponse(new CatalogAdminResultComposer(false, "Move or delete offers on this page first"));
            return;
        }

        var result = mutations.apply(new CatalogDraftMutationRequest(
                envelope.draftVersionId(),
                envelope.expectedRevision(),
                this.client.getHabbo().getHabboInfo().getId(),
                new CatalogLockKey(CatalogEntityType.PAGE, pageType, pageId),
                envelope.lockToken(),
                envelope.summary(),
                CatalogEntityType.PAGE,
                pageId,
                CatalogChangeOperation.DELETE,
                null));
        this.client.sendResponse(new CatalogAdminResultComposer(
                true, "Page deleted from shared draft at revision " + result.revision()));
    }
}
