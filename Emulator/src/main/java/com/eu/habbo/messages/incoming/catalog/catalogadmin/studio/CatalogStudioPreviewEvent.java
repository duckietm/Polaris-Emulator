package com.eu.habbo.messages.incoming.catalog.catalogadmin.studio;

import com.eu.habbo.habbohotel.catalog.versioning.CatalogDraftPreview;
import com.eu.habbo.habbohotel.catalog.versioning.CatalogPreviewPersona;
import com.eu.habbo.habbohotel.catalog.versioning.CatalogVersionSnapshot;
import com.eu.habbo.messages.outgoing.catalog.catalogadmin.studio.CatalogStudioPreviewComposer;
import java.util.LinkedHashMap;
import java.util.Map;

public final class CatalogStudioPreviewEvent extends CatalogStudioEvent {
    @Override
    public void handle() {
        if (!authorize()) return;
        String operationId = this.packet.readString();
        long draftVersionId = this.packet.readInt();
        long expectedRevision = this.packet.readInt();
        int rank = this.packet.readInt();
        boolean hc = this.packet.readBoolean();
        boolean vip = this.packet.readBoolean();
        boolean buildersClub = this.packet.readBoolean();
        boolean showHidden = this.packet.readBoolean();
        int credits = this.packet.readInt();
        int currencyCount = Math.max(0, Math.min(this.packet.readInt(), 100));
        Map<Integer, Integer> currencies = new LinkedHashMap<>();
        for (int index = 0; index < currencyCount; index++) {
            currencies.put(this.packet.readInt(), this.packet.readInt());
        }
        CatalogVersionSnapshot draft = studio().queries().loadDraftSnapshot(draftVersionId);
        if (draft.version().revision() != expectedRevision) {
            throw new IllegalArgumentException("Catalog preview revision is stale");
        }
        CatalogDraftPreview preview = studio().preview()
                .preview(
                        draft, new CatalogPreviewPersona(rank, hc, vip, buildersClub, showHidden, credits, currencies));
        this.client.sendResponse(new CatalogStudioPreviewComposer(operationId, preview));
    }
}
