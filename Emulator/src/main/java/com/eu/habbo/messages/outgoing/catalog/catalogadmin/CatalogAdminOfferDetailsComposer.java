package com.eu.habbo.messages.outgoing.catalog.catalogadmin;

import com.eu.habbo.habbohotel.catalog.versioning.CatalogOfferSnapshot;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public class CatalogAdminOfferDetailsComposer extends MessageComposer {
    private final int offerId;
    private final int pageId;
    private final String itemIds;
    private final String catalogName;
    private final int costCredits;
    private final int costPoints;
    private final int pointsType;
    private final int amount;
    private final boolean clubOnly;
    private final String extradata;
    private final boolean haveOffer;
    private final int offerIdGroup;
    private final int limitedStack;
    private final int limitedSells;
    private final int orderNumber;
    private final int songId;
    private final String catalogMode;

    public CatalogAdminOfferDetailsComposer(int offerId, int offerIdGroup, int limitedStack, int orderNumber) {
        this(offerId, 0, "", "", 0, 0, 0, 1, false, "", false, offerIdGroup, limitedStack, 0, orderNumber, 0, "NORMAL");
    }

    public CatalogAdminOfferDetailsComposer(CatalogOfferSnapshot offer, int limitedSells) {
        this(
                offer.offerId(),
                offer.pageId(),
                offer.itemIds(),
                offer.catalogName(),
                offer.costCredits(),
                offer.costPoints(),
                offer.pointsType(),
                offer.amount(),
                offer.clubOnly(),
                offer.extradata(),
                offer.haveOffer(),
                offer.offerIdClient(),
                offer.limitedStack(),
                limitedSells,
                offer.orderNumber(),
                offer.songId(),
                offer.catalogType().name());
    }

    public CatalogAdminOfferDetailsComposer(
            int offerId,
            int pageId,
            String itemIds,
            String catalogName,
            int costCredits,
            int costPoints,
            int pointsType,
            int amount,
            boolean clubOnly,
            String extradata,
            boolean haveOffer,
            int offerIdGroup,
            int limitedStack,
            int limitedSells,
            int orderNumber,
            int songId,
            String catalogMode) {
        this.offerId = offerId;
        this.pageId = pageId;
        this.itemIds = itemIds;
        this.catalogName = catalogName;
        this.costCredits = costCredits;
        this.costPoints = costPoints;
        this.pointsType = pointsType;
        this.amount = amount;
        this.clubOnly = clubOnly;
        this.extradata = extradata;
        this.haveOffer = haveOffer;
        this.offerIdGroup = offerIdGroup;
        this.limitedStack = limitedStack;
        this.limitedSells = limitedSells;
        this.orderNumber = orderNumber;
        this.songId = songId;
        this.catalogMode = catalogMode;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.CatalogAdminOfferDetailsComposer);
        this.response.appendInt(this.offerId);
        this.response.appendInt(this.pageId);
        this.response.appendString(this.itemIds);
        this.response.appendString(this.catalogName);
        this.response.appendInt(this.costCredits);
        this.response.appendInt(this.costPoints);
        this.response.appendInt(this.pointsType);
        this.response.appendInt(this.amount);
        this.response.appendBoolean(this.clubOnly);
        this.response.appendString(this.extradata);
        this.response.appendBoolean(this.haveOffer);
        this.response.appendInt(this.offerIdGroup);
        this.response.appendInt(this.limitedStack);
        this.response.appendInt(this.limitedSells);
        this.response.appendInt(this.orderNumber);
        this.response.appendInt(this.songId);
        this.response.appendString(this.catalogMode);
        return this.response;
    }
}
