package com.eu.habbo.messages.outgoing.collectibles;

import com.eu.habbo.habbohotel.collectibles.CollectibleData.Claim;
import com.eu.habbo.habbohotel.collectibles.CollectibleData.Product;
import com.eu.habbo.habbohotel.collectibles.CollectibleFurniTypes;
import com.eu.habbo.habbohotel.collectibles.CollectiblesRuntime;
import com.eu.habbo.messages.ServerMessage;
import java.util.List;

/** The official client's collectible structures ({@code class_2508} and friends) on the wire. */
final class CollectibleWire {
    private CollectibleWire() {}

    /** Flash {@code readLong()}: two big-endian ints. */
    static void appendLong(ServerMessage message, long value) {
        message.appendInt((int) (value >>> 32));
        message.appendInt((int) value);
    }

    /** {@code class_2508}: the product info every collectible screen reads. */
    static void appendProduct(ServerMessage message, Product product) {
        appendProduct(message, product, null);
    }

    /**
     * {@code class_2508} with the extra int of {@code class_4366} (the amount owned) after the score when
     * {@code amount} is given. A furni product's item type goes out as its sprite id, and a clothing
     * product without stored figure set ids sends its furni's (see {@link CollectibleFurniTypes}).
     */
    static void appendProduct(ServerMessage message, Product product, Integer amount) {
        message.appendShort(product.productType());
        message.appendString(CollectibleFurniTypes.wireItemTypeId(product, CollectiblesRuntime::baseItem));
        message.appendInt(product.score());
        if (amount != null) message.appendInt(amount);
        message.appendString(product.petFigure());
        List<Integer> figureSetIds = CollectibleFurniTypes.wireFigureSetIds(product, CollectiblesRuntime::baseItem);
        message.appendInt(figureSetIds.size());
        for (Integer setId : figureSetIds) message.appendInt(setId);
        message.appendString(product.productCode());
        message.appendString(product.rarity());
    }

    /** {@code CollectibleItemClaim}: claim id, claimed amount, claim limit, status. */
    static void appendClaim(ServerMessage message, Claim claim) {
        message.appendString(claim.claimId());
        message.appendInt(claim.claimedAmount());
        message.appendInt(claim.claimLimit());
        message.appendShort(claim.status());
    }
}
