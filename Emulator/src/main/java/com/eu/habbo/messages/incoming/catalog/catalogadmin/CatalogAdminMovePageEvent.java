package com.eu.habbo.messages.incoming.catalog.catalogadmin;

import com.eu.habbo.habbohotel.catalog.CatalogPageType;
import com.eu.habbo.habbohotel.catalog.versioning.CatalogChangeOperation;
import com.eu.habbo.habbohotel.catalog.versioning.CatalogEntityType;
import com.eu.habbo.habbohotel.catalog.versioning.CatalogPageMovePlanner;
import com.eu.habbo.habbohotel.catalog.versioning.CatalogVersionSnapshot;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.incoming.catalog.catalogadmin.studio.CatalogStudioMutationEnvelope;
import com.eu.habbo.messages.incoming.catalog.catalogadmin.studio.CatalogStudioRequestParser;
import com.eu.habbo.messages.incoming.catalog.catalogadmin.studio.CatalogStudioRuntime;
import com.eu.habbo.messages.outgoing.catalog.catalogadmin.CatalogAdminResultComposer;
import com.google.gson.Gson;

public class CatalogAdminMovePageEvent extends MessageHandler {

    private static final int MAX_PARENT_WALK = 64;
    private static final int ROOT_PARENT_ID = -1;

    @Override
    public void handle() throws Exception {
        if (!this.client.getHabbo().hasPermission(Permission.ACC_CATALOGFURNI)) {
            this.client.sendResponse(new CatalogAdminResultComposer(false, "No permission"));
            return;
        }

        int pageId = this.packet.readInt();
        int newParentId = this.packet.readInt();
        int newIndex = this.packet.readInt();
        CatalogPageType pageType = CatalogPageType.fromString(this.packet.readString());

        CatalogStudioMutationEnvelope envelope = CatalogStudioRequestParser.parseMutationEnvelope(this.packet);
        var liveMutations = CatalogStudioRuntime.services().liveMutations();
        CatalogVersionSnapshot live = liveMutations.loadLive();
        var page = live.page(pageType, pageId).orElse(null);
        if (page == null) {
            this.client.sendResponse(new CatalogAdminResultComposer(false, "Live catalog page not found: " + pageId));
            return;
        }

        if (newParentId == pageId) {
            this.client.sendResponse(new CatalogAdminResultComposer(false, "A page cannot be its own parent"));
            return;
        }

        if (newParentId != ROOT_PARENT_ID) {
            if (live.page(pageType, newParentId).isEmpty()) {
                this.client.sendResponse(
                        new CatalogAdminResultComposer(false, "Parent page not found: " + newParentId));
                return;
            }

            if (this.wouldCreateCycle(pageType, pageId, newParentId, live)) {
                this.client.sendResponse(
                        new CatalogAdminResultComposer(false, "Refusing to move: that would create a cycle"));
                return;
            }
        }

        var editedPages = CatalogPageMovePlanner.plan(live.pages(), pageType, pageId, newParentId, newIndex);
        if (editedPages.isEmpty()) {
            this.client.sendResponse(new CatalogAdminResultComposer(true, "Page is already in the requested position"));
            return;
        }

        var gson = new Gson();
        var requests = editedPages.stream()
                .map(edited -> CatalogAdminLiveRequest.atRevision(
                        envelope,
                        live.version().revision(),
                        this.client.getHabbo().getHabboInfo().getId(),
                        CatalogEntityType.PAGE,
                        pageType,
                        edited.pageId(),
                        edited.pageId() == pageId ? CatalogChangeOperation.MOVE : CatalogChangeOperation.UPDATE,
                        gson.toJson(edited)))
                .toList();
        var result = liveMutations.applyBatch(requests);
        this.client.sendResponse(
                new CatalogAdminResultComposer(true, "Page moved live at revision " + result.revision()));
    }

    private boolean wouldCreateCycle(CatalogPageType pageType, int pageId, int parentId, CatalogVersionSnapshot draft) {
        int current = parentId;
        for (int hops = 0; hops < MAX_PARENT_WALK; hops++) {
            if (current == ROOT_PARENT_ID) return false;
            if (current == pageId) return true;
            var parent = draft.page(pageType, current).orElse(null);
            if (parent == null) return false;
            current = parent.parentId();
        }
        return true;
    }
}
