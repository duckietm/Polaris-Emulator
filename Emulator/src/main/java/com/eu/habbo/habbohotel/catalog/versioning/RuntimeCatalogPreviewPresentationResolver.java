package com.eu.habbo.habbohotel.catalog.versioning;

import com.eu.habbo.habbohotel.items.FurnitureType;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.ItemManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class RuntimeCatalogPreviewPresentationResolver implements CatalogPreviewPresentationResolver {
    private final ItemManager items;
    private final CatalogOperationalOfferRepository operationalOffers;

    public RuntimeCatalogPreviewPresentationResolver(
            ItemManager items, CatalogOperationalOfferRepository operationalOffers) {
        this.items = Objects.requireNonNull(items, "items");
        this.operationalOffers = Objects.requireNonNull(operationalOffers, "operationalOffers");
    }

    @Override
    public CatalogPreviewPresentation resolve(CatalogOfferSnapshot offer) {
        List<CatalogPreviewProduct> products = new ArrayList<>();
        List<Item> resolvedItems = new ArrayList<>();
        int limitedSells = offer.limitedStack() > 0
                ? operationalOffers.findLimitedSells(offer.offerId()).orElse(0)
                : 0;

        for (String token : offer.itemIds().split(";")) {
            ParsedItem parsed = parseItem(token, offer.amount());
            if (parsed == null) continue;
            Item item = items.getItem(parsed.baseItemId());
            if (item == null) continue;
            resolvedItems.add(item);
            boolean limited = offer.limitedStack() > 0;
            products.add(new CatalogPreviewProduct(
                    item.getType().code,
                    item.getType() == FurnitureType.BADGE ? -1 : item.getSpriteId(),
                    extraParam(offer, item),
                    parsed.count(),
                    limited,
                    limited ? offer.limitedStack() : 0,
                    limited ? Math.max(0, offer.limitedStack() - limitedSells) : 0));
        }

        boolean giftable = resolvedItems.size() == 1 && resolvedItems.getFirst().allowGift();
        return new CatalogPreviewPresentation(products, giftable);
    }

    private static ParsedItem parseItem(String token, int defaultCount) {
        if (token == null || token.isBlank()) return null;
        String[] parts = token.trim().split(":", 2);
        try {
            int baseItemId = Integer.parseInt(parts[0]);
            int count = parts.length == 2 ? Integer.parseInt(parts[1]) : defaultCount;
            if (baseItemId <= 0 || count <= 0) return null;
            return new ParsedItem(baseItemId, count);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String extraParam(CatalogOfferSnapshot offer, Item item) {
        if (item.getType() == FurnitureType.BADGE) return item.getName();
        if (item.getType() == FurnitureType.ROBOT) {
            for (String value : offer.extradata().split(";")) {
                if (value.startsWith("figure:")) return value.substring("figure:".length());
            }
            return offer.extradata();
        }
        if (item.getName().equalsIgnoreCase("poster") || offer.catalogName().startsWith("SONG ")) {
            return offer.extradata();
        }
        if (offer.catalogName().contains("_single")) {
            String[] parts = offer.catalogName().split("_");
            if (parts.length > 2) return parts[2];
        }
        return "";
    }

    private record ParsedItem(int baseItemId, int count) {}
}
