package com.eu.habbo.messages.incoming.catalog.catalogadmin;

import com.eu.habbo.habbohotel.catalog.CatalogPageType;
import com.eu.habbo.habbohotel.catalog.versioning.CatalogChangeOperation;
import com.eu.habbo.habbohotel.catalog.versioning.CatalogDraftMutationRequest;
import com.eu.habbo.habbohotel.catalog.versioning.CatalogEntityType;
import com.eu.habbo.habbohotel.catalog.versioning.CatalogLockKey;
import com.eu.habbo.habbohotel.catalog.versioning.CatalogSnapshotPatch;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.incoming.catalog.catalogadmin.studio.CatalogStudioMutationEnvelope;
import com.eu.habbo.messages.incoming.catalog.catalogadmin.studio.CatalogStudioRequestParser;
import com.eu.habbo.messages.incoming.catalog.catalogadmin.studio.CatalogStudioRuntime;
import com.eu.habbo.messages.outgoing.catalog.catalogadmin.CatalogAdminResultComposer;
import com.google.gson.Gson;

public class CatalogAdminSavePageIconEvent extends MessageHandler {

    @Override
    public void handle() throws Exception {
        if (!this.client.getHabbo().hasPermission(Permission.ACC_CATALOGFURNI)) {
            this.client.sendResponse(new CatalogAdminResultComposer(false, "No permission"));
            return;
        }

        int pageId = this.packet.readInt();
        int iconId = this.packet.readInt();
        CatalogPageType pageType = CatalogPageType.fromString(this.packet.readString());

        if (iconId < 0) iconId = 0;
        CatalogStudioMutationEnvelope envelope = CatalogStudioRequestParser.parseMutationEnvelope(this.packet);
        var mutations = CatalogStudioRuntime.services().mutations();
        var page = mutations
                .loadDraft(envelope.draftVersionId(), envelope.expectedRevision())
                .page(pageType, pageId)
                .orElse(null);
        if (page == null) {
            this.client.sendResponse(
                    new CatalogAdminResultComposer(false, "Page not found in shared draft: " + pageId));
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
                CatalogChangeOperation.UPDATE,
                new Gson().toJson(CatalogSnapshotPatch.setPageIcon(page, iconId))));
        this.client.sendResponse(new CatalogAdminResultComposer(
                true, "Page icon saved in shared draft at revision " + result.revision()));
    }
}
