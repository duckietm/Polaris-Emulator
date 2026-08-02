package com.eu.habbo.habbohotel.catalog.versioning;

import com.eu.habbo.habbohotel.catalog.CatalogPageType;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class CatalogVersionSnapshot {
    private final CatalogVersion version;
    private final List<CatalogPageSnapshot> pages;
    private final List<CatalogOfferSnapshot> offers;
    private final Map<Integer, CatalogPageSnapshot> pageIndex;
    private final Map<Integer, CatalogOfferSnapshot> offerIndex;
    private final Map<CatalogSnapshotKey, CatalogPageSnapshot> scopedPageIndex;
    private final Map<CatalogSnapshotKey, CatalogOfferSnapshot> scopedOfferIndex;

    public CatalogVersionSnapshot(
            CatalogVersion version, List<CatalogPageSnapshot> pages, List<CatalogOfferSnapshot> offers) {
        this.version = Objects.requireNonNull(version, "version");
        this.pages = List.copyOf(Objects.requireNonNull(pages, "pages"));
        this.offers = List.copyOf(Objects.requireNonNull(offers, "offers"));
        this.scopedPageIndex = Map.copyOf(indexPages(this.pages));
        this.scopedOfferIndex = Map.copyOf(indexOffers(this.offers));
        this.pageIndex = Map.copyOf(indexNormalPages(this.pages));
        this.offerIndex = Map.copyOf(indexNormalOffers(this.offers));
    }

    public CatalogVersion version() {
        return version;
    }

    public List<CatalogPageSnapshot> pages() {
        return pages;
    }

    public List<CatalogOfferSnapshot> offers() {
        return offers;
    }

    public Map<Integer, CatalogPageSnapshot> pageIndex() {
        return pageIndex;
    }

    public Map<Integer, CatalogOfferSnapshot> offerIndex() {
        return offerIndex;
    }

    public Optional<CatalogPageSnapshot> page(int pageId) {
        return page(CatalogPageType.NORMAL, pageId);
    }

    public Optional<CatalogOfferSnapshot> offer(int offerId) {
        return offer(CatalogPageType.NORMAL, offerId);
    }

    public Optional<CatalogPageSnapshot> page(CatalogPageType catalogType, int pageId) {
        return Optional.ofNullable(scopedPageIndex.get(new CatalogSnapshotKey(catalogType, pageId)));
    }

    public Optional<CatalogOfferSnapshot> offer(CatalogPageType catalogType, int offerId) {
        return Optional.ofNullable(scopedOfferIndex.get(new CatalogSnapshotKey(catalogType, offerId)));
    }

    private static Map<CatalogSnapshotKey, CatalogPageSnapshot> indexPages(List<CatalogPageSnapshot> pages) {
        Map<CatalogSnapshotKey, CatalogPageSnapshot> result = new LinkedHashMap<>();
        for (CatalogPageSnapshot page : pages) {
            CatalogPageSnapshot item = Objects.requireNonNull(page, "page");
            CatalogSnapshotKey key = new CatalogSnapshotKey(item.catalogType(), item.pageId());
            if (result.put(key, item) != null) {
                throw new IllegalArgumentException("Duplicate page ID in " + item.catalogType() + ": " + item.pageId());
            }
        }
        return result;
    }

    private static Map<CatalogSnapshotKey, CatalogOfferSnapshot> indexOffers(List<CatalogOfferSnapshot> offers) {
        Map<CatalogSnapshotKey, CatalogOfferSnapshot> result = new LinkedHashMap<>();
        for (CatalogOfferSnapshot offer : offers) {
            CatalogOfferSnapshot item = Objects.requireNonNull(offer, "offer");
            CatalogSnapshotKey key = new CatalogSnapshotKey(item.catalogType(), item.offerId());
            if (result.put(key, item) != null) {
                throw new IllegalArgumentException(
                        "Duplicate offer ID in " + item.catalogType() + ": " + item.offerId());
            }
        }
        return result;
    }

    private static Map<Integer, CatalogPageSnapshot> indexNormalPages(List<CatalogPageSnapshot> pages) {
        Map<Integer, CatalogPageSnapshot> result = new LinkedHashMap<>();
        pages.stream()
                .filter(page -> page.catalogType() == CatalogPageType.NORMAL)
                .forEach(page -> result.put(page.pageId(), page));
        return result;
    }

    private static Map<Integer, CatalogOfferSnapshot> indexNormalOffers(List<CatalogOfferSnapshot> offers) {
        Map<Integer, CatalogOfferSnapshot> result = new LinkedHashMap<>();
        offers.stream()
                .filter(offer -> offer.catalogType() == CatalogPageType.NORMAL)
                .forEach(offer -> result.put(offer.offerId(), offer));
        return result;
    }

    private record CatalogSnapshotKey(CatalogPageType catalogType, int entityId) {
        private CatalogSnapshotKey {
            catalogType = Objects.requireNonNull(catalogType, "catalogType");
            if (catalogType == CatalogPageType.BOTH) {
                throw new IllegalArgumentException("A snapshot lookup cannot target BOTH catalogs");
            }
        }
    }
}
