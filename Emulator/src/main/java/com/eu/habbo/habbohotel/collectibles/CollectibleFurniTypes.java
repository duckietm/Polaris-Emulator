package com.eu.habbo.habbohotel.collectibles;

import com.eu.habbo.habbohotel.collectibles.CollectibleData.Product;
import com.eu.habbo.habbohotel.items.Item;
import java.util.List;
import java.util.function.IntFunction;
import java.util.regex.Pattern;

/**
 * Furni types between the database and the client. The collectibles tables store furni by
 * {@code items_base.id}, which is what delivery and minting look up; the client knows furni by their
 * furnidata id, the {@code items_base.sprite_id}, as every inventory and catalog packet sends it. These two
 * differ in most hotels, so every furni type a collectibles packet sends goes through here.
 */
public final class CollectibleFurniTypes {
    private static final Pattern ID_LIST = Pattern.compile("\\d{1,9}(,\\d{1,9})*");

    private CollectibleFurniTypes() {}

    /** Whether a product's {@code item_type_id} is an {@code items_base} id: wall, floor and clothing furni. */
    public static boolean isFurniProduct(int productType) {
        return productType == CollectibleData.TYPE_WALL
                || productType == CollectibleData.TYPE_FLOOR
                || productType == CollectibleData.TYPE_CLOTHING;
    }

    /**
     * The sprite id of an {@code items_base} id, or the id itself when the furni is not loaded (no item
     * manager yet, or a row that names a missing furni).
     */
    public static int spriteId(int baseItemId, IntFunction<Item> baseItems) {
        Item item = baseItems == null ? null : baseItems.apply(baseItemId);
        return item == null ? baseItemId : item.getSpriteId();
    }

    /**
     * A product's {@code itemTypeId} as the client reads it: the sprite id for furni products, the stored
     * value (effect id, badge code, chat style, pet type) for the others.
     */
    public static String wireItemTypeId(Product product, IntFunction<Item> baseItems) {
        String stored = product.itemTypeId();
        if (!isFurniProduct(product.productType())) return stored;
        int baseItemId;
        try {
            baseItemId = Integer.parseInt(stored.trim());
        } catch (NumberFormatException exception) {
            return stored;
        }
        return Integer.toString(spriteId(baseItemId, baseItems));
    }

    /**
     * A product's figure set ids as the client reads them ({@code class_2508}): the stored
     * {@code figure_set_ids}, or for a clothing product without any the ids in its furni's
     * {@code items_base.customparams}, as the furnidata keeps them for clothing furni ({@code "0"}, the
     * column default, is no id list). The hub previews clothing on the avatar with them.
     */
    public static List<Integer> wireFigureSetIds(Product product, IntFunction<Item> baseItems) {
        if (!product.figureSetIds().isEmpty() || product.productType() != CollectibleData.TYPE_CLOTHING) {
            return product.figureSetIds();
        }
        Item item;
        try {
            item = baseItems == null
                    ? null
                    : baseItems.apply(Integer.parseInt(product.itemTypeId().trim()));
        } catch (NumberFormatException exception) {
            return product.figureSetIds();
        }
        String params = item == null || item.getCustomParams() == null
                ? ""
                : item.getCustomParams().replace(" ", "");
        if ("0".equals(params) || !ID_LIST.matcher(params).matches()) return product.figureSetIds();
        return List.copyOf(CollectiblesService.parseIds(params));
    }
}
