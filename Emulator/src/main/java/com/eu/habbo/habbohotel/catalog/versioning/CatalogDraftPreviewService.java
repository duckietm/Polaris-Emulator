package com.eu.habbo.habbohotel.catalog.versioning;

import com.eu.habbo.habbohotel.catalog.CatalogPageType;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class CatalogDraftPreviewService {
    public CatalogDraftPreview preview(CatalogVersionSnapshot draft, CatalogPreviewPersona persona) {
        Objects.requireNonNull(draft, "draft");
        Objects.requireNonNull(persona, "persona");
        if (draft.version().status() != CatalogVersionStatus.DRAFT) {
            throw new IllegalArgumentException("Preview requires a draft snapshot");
        }

        List<CatalogPageSnapshot> pages = draft.pages().stream()
                .filter(page -> visible(draft, page, persona))
                .toList();
        Set<PageKey> visiblePages = new HashSet<>();
        pages.forEach(page -> visiblePages.add(new PageKey(page.catalogType(), page.pageId())));

        List<CatalogPreviewOffer> offers = new ArrayList<>();
        for (CatalogOfferSnapshot offer : draft.offers()) {
            if (!visiblePages.contains(new PageKey(offer.catalogType(), offer.pageId()))) continue;
            List<String> reasons = new ArrayList<>();
            if (!offer.haveOffer()) reasons.add("OFFER_DISABLED");
            if (offer.clubOnly() && !persona.hc()) reasons.add("HC_REQUIRED");
            if (offer.costCredits() > persona.credits()) reasons.add("INSUFFICIENT_CREDITS");
            if (offer.costPoints() > persona.currencies().getOrDefault(offer.pointsType(), 0)) {
                reasons.add("INSUFFICIENT_CURRENCY_" + offer.pointsType());
            }
            offers.add(new CatalogPreviewOffer(offer, reasons.isEmpty(), reasons));
        }
        return new CatalogDraftPreview(draft.version().id(), draft.version().revision(), pages, offers);
    }

    private static boolean visible(
            CatalogVersionSnapshot snapshot, CatalogPageSnapshot page, CatalogPreviewPersona persona) {
        if (page.catalogType() == CatalogPageType.BUILDER && !persona.buildersClub()) return false;
        if (page.minRank() > persona.rank()) return false;
        if (page.clubOnly() && !persona.hc()) return false;
        if (page.vipOnly() && !persona.vip()) return false;
        if ((!page.visible() || !page.enabled()) && !persona.showHidden()) return false;

        Set<Integer> visited = new HashSet<>();
        int parentId = page.parentId();
        while (parentId > 0 && visited.add(parentId)) {
            CatalogPageSnapshot parent =
                    snapshot.page(page.catalogType(), parentId).orElse(null);
            if (parent == null) return false;
            if ((!parent.visible() || !parent.enabled()) && !persona.showHidden()) return false;
            if (parent.minRank() > persona.rank()) return false;
            if (parent.clubOnly() && !persona.hc()) return false;
            if (parent.vipOnly() && !persona.vip()) return false;
            parentId = parent.parentId();
        }
        return parentId <= 0;
    }

    private record PageKey(CatalogPageType catalogType, int pageId) {}
}
