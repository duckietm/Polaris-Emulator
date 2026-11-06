package polaris.messages.incoming.catalog.catalogadmin;

import polaris.hotel.catalog.CatalogPageType;
import polaris.hotel.catalog.versioning.CatalogDraftOfferData;

final class CatalogAdminOfferDraftData {
    private CatalogAdminOfferDraftData() {}

    static CatalogDraftOfferData from(CatalogAdminOfferPayload payload) {
        boolean builder = payload.pageType == CatalogPageType.BUILDER;
        return new CatalogDraftOfferData(
                payload.itemIds,
                payload.pageId,
                payload.catalogName,
                builder ? 0 : payload.costCredits,
                builder ? 0 : payload.costPoints,
                builder ? 0 : payload.pointsType,
                builder ? 1 : payload.amount,
                builder ? 0 : payload.limitedStack,
                payload.orderNumber,
                builder ? -1 : payload.offerIdGroup,
                builder ? 0 : payload.songId,
                payload.extradata,
                builder || payload.haveOffer,
                !builder && payload.clubOnly == 1);
    }
}
