package com.eu.habbo.messages.incoming.furnieditor;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * Shared utility for building item data maps from ResultSet rows.
 * Used by FurniEditorDetailEvent, FurniEditorBySpriteEvent, and
 * FurniEditorSearchEvent to ensure consistent field reading.
 */
public class FurniEditorHelper {
    public static final String CATALOG_ITEM_IDS_TOKEN_SQL = "CONCAT(',', REPLACE(item_ids, ' ', ''), ',') LIKE ?";

    public static String catalogItemIdsTokenSql(String column) {
        return "CONCAT(',', REPLACE(" + column + ", ' ', ''), ',') LIKE ?";
    }

    public static String catalogItemIdsTokenPattern(int itemId) {
        return "%," + itemId + ",%";
    }

    /**
     * Read the 14 base fields from items_base into a Map.
     */
    public static Map<String, Object> readBaseItem(ResultSet set) throws SQLException {
        Map<String, Object> item = new HashMap<>();
        item.put("id", set.getInt("id"));
        item.put("sprite_id", set.getInt("sprite_id"));
        item.put("item_name", set.getString("item_name"));
        item.put("public_name", set.getString("public_name"));
        item.put("type", set.getString("type"));
        item.put("width", set.getInt("width"));
        item.put("length", set.getInt("length"));
        item.put("stack_height", set.getDouble("stack_height"));
        item.put("allow_stack", set.getString("allow_stack"));
        item.put("allow_walk", set.getString("allow_walk"));
        item.put("allow_sit", set.getString("allow_sit"));
        item.put("allow_lay", set.getString("allow_lay"));
        item.put("interaction_type", set.getString("interaction_type"));
        item.put("interaction_modes_count", set.getInt("interaction_modes_count"));
        return item;
    }

    /**
     * Read all fields (14 base + 13 extended) from items_base into a Map.
     */
    public static Map<String, Object> readFullItem(ResultSet set) throws SQLException {
        Map<String, Object> item = readBaseItem(set);
        item.put("allow_gift", set.getString("allow_gift"));
        item.put("allow_trade", set.getString("allow_trade"));
        item.put("allow_recycle", set.getString("allow_recycle"));
        item.put("allow_marketplace_sell", set.getString("allow_marketplace_sell"));
        item.put("allow_inventory_stack", set.getString("allow_inventory_stack"));
        item.put("vending_ids", set.getString("vending_ids"));
        item.put("customparams", set.getString("customparams"));
        item.put("effect_id_male", set.getInt("effect_id_male"));
        item.put("effect_id_female", set.getInt("effect_id_female"));
        item.put("clothing_on_walk", set.getString("clothing_on_walk"));
        item.put("multiheight", set.getString("multiheight"));

        // description may not exist in all schemas, handle gracefully
        try {
            item.put("description", set.getString("description"));
        } catch (SQLException e) {
            item.put("description", "");
        }

        return item;
    }

    /**
     * Read a catalog item reference from a result set that joined
     * catalog_items with catalog_pages.
     */
    public static Map<String, Object> readCatalogRef(ResultSet set) throws SQLException {
        Map<String, Object> ref = new HashMap<>();
        ref.put("id", set.getInt("ci_id"));
        ref.put("catalog_name", set.getString("catalog_name"));
        ref.put("cost_credits", set.getInt("cost_credits"));
        ref.put("cost_points", set.getInt("cost_points"));
        ref.put("points_type", set.getInt("points_type"));
        ref.put("page_id", set.getInt("ci_page_id"));
        ref.put("page_caption", set.getString("page_caption"));
        return ref;
    }

    /**
     * Whitelist of allowed field names for update operations.
     * Prevents SQL injection via arbitrary column names.
     */
    public static final java.util.Set<String> ALLOWED_UPDATE_FIELDS = java.util.Set.of(
        "public_name", "sprite_id", "type", "width", "length",
        "stack_height", "allow_stack", "allow_walk", "allow_sit", "allow_lay",
        "allow_gift", "allow_trade", "allow_recycle", "allow_marketplace_sell",
        "allow_inventory_stack", "interaction_type", "interaction_modes_count",
        "vending_ids", "customparams", "effect_id_male", "effect_id_female",
        "clothing_on_walk", "multiheight", "description"
    );

    /**
     * Map camelCase JS field names to DB column names.
     */
    public static final Map<String, String> FIELD_MAP = Map.ofEntries(
        Map.entry("itemName", "item_name"),
        Map.entry("publicName", "public_name"),
        Map.entry("spriteId", "sprite_id"),
        Map.entry("type", "type"),
        Map.entry("width", "width"),
        Map.entry("length", "length"),
        Map.entry("stackHeight", "stack_height"),
        Map.entry("allowStack", "allow_stack"),
        Map.entry("allowWalk", "allow_walk"),
        Map.entry("allowSit", "allow_sit"),
        Map.entry("allowLay", "allow_lay"),
        Map.entry("allowGift", "allow_gift"),
        Map.entry("allowTrade", "allow_trade"),
        Map.entry("allowRecycle", "allow_recycle"),
        Map.entry("allowMarketplaceSell", "allow_marketplace_sell"),
        Map.entry("allowInventoryStack", "allow_inventory_stack"),
        Map.entry("interactionType", "interaction_type"),
        Map.entry("interactionModesCount", "interaction_modes_count"),
        Map.entry("vendingIds", "vending_ids"),
        Map.entry("customparams", "customparams"),
        Map.entry("effectIdMale", "effect_id_male"),
        Map.entry("effectIdFemale", "effect_id_female"),
        Map.entry("clothingOnWalk", "clothing_on_walk"),
        Map.entry("multiheight", "multiheight"),
        Map.entry("description", "description")
    );
}
