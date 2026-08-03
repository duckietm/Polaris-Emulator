package com.eu.habbo.habbohotel.catalog.versioning;

public final class CatalogSnapshotPatch {
    private CatalogSnapshotPatch() {}

    public static CatalogPageSnapshot movePage(CatalogPageSnapshot page, int parentId, int orderNum) {
        return page(page, parentId, orderNum, page.visible(), page.enabled());
    }

    public static CatalogPageSnapshot setPageVisible(CatalogPageSnapshot page, boolean visible) {
        return page(page, page.parentId(), page.orderNum(), visible, page.enabled());
    }

    public static CatalogPageSnapshot setPageEnabled(CatalogPageSnapshot page, boolean enabled) {
        return page(page, page.parentId(), page.orderNum(), page.visible(), enabled);
    }

    public static CatalogPageSnapshot setPageIcon(CatalogPageSnapshot page, int iconImage) {
        return new CatalogPageSnapshot(
                page.catalogType(),
                page.pageId(),
                page.parentId(),
                page.captionSave(),
                page.caption(),
                page.pageLayout(),
                page.iconColor(),
                iconImage,
                page.minRank(),
                page.orderNum(),
                page.visible(),
                page.enabled(),
                page.clubOnly(),
                page.catalogMode(),
                page.vipOnly(),
                page.pageHeadline(),
                page.pageTeaser(),
                page.pageSpecial(),
                page.pageText1(),
                page.pageText2(),
                page.pageTextDetails(),
                page.pageTextTeaser(),
                page.roomId(),
                page.includes());
    }

    public static CatalogPageSnapshot setPageImages(CatalogPageSnapshot page, String pageHeadline, String pageTeaser) {
        return new CatalogPageSnapshot(
                page.catalogType(),
                page.pageId(),
                page.parentId(),
                page.captionSave(),
                page.caption(),
                page.pageLayout(),
                page.iconColor(),
                page.iconImage(),
                page.minRank(),
                page.orderNum(),
                page.visible(),
                page.enabled(),
                page.clubOnly(),
                page.catalogMode(),
                page.vipOnly(),
                pageHeadline,
                pageTeaser,
                page.pageSpecial(),
                page.pageText1(),
                page.pageText2(),
                page.pageTextDetails(),
                page.pageTextTeaser(),
                page.roomId(),
                page.includes());
    }

    public static CatalogOfferSnapshot setOfferOrder(CatalogOfferSnapshot offer, int orderNumber) {
        return new CatalogOfferSnapshot(
                offer.catalogType(),
                offer.offerId(),
                offer.itemIds(),
                offer.pageId(),
                offer.catalogName(),
                offer.costCredits(),
                offer.costPoints(),
                offer.pointsType(),
                offer.amount(),
                offer.limitedStack(),
                orderNumber,
                offer.offerIdClient(),
                offer.songId(),
                offer.extradata(),
                offer.haveOffer(),
                offer.clubOnly());
    }

    private static CatalogPageSnapshot page(
            CatalogPageSnapshot page, int parentId, int orderNum, boolean visible, boolean enabled) {
        return new CatalogPageSnapshot(
                page.catalogType(),
                page.pageId(),
                parentId,
                page.captionSave(),
                page.caption(),
                page.pageLayout(),
                page.iconColor(),
                page.iconImage(),
                page.minRank(),
                orderNum,
                visible,
                enabled,
                page.clubOnly(),
                page.catalogMode(),
                page.vipOnly(),
                page.pageHeadline(),
                page.pageTeaser(),
                page.pageSpecial(),
                page.pageText1(),
                page.pageText2(),
                page.pageTextDetails(),
                page.pageTextTeaser(),
                page.roomId(),
                page.includes());
    }
}
