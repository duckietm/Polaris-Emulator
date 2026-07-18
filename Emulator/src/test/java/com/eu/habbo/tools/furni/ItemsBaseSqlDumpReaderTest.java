package com.eu.habbo.tools.furni;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ItemsBaseSqlDumpReaderTest {
    @Test
    void readsMultipleItemsBaseInsertBlocksAndQuotedValues() {
        String sql = """
                INSERT INTO `items_base` (`id`, `sprite_id`, `public_name`, `item_name`, `type`, `interaction_type`) VALUES
                  (1, 2066, 'Gold Bar', 'CF_50_goldbar', 's', 'default'),
                  (2, 4608, 'Manager''s Portrait', 'diamond_painting14', 'i', 'default');
                INSERT INTO `other` (`id`) VALUES (9);
                INSERT INTO `items_base` (`interaction_type`, `type`, `item_name`, `sprite_id`, `id`) VALUES
                  ('default', 's', 'escaped\\_name', 99, 3);
                """;

        var items = ItemsBaseSqlDumpReader.read(sql);

        assertEquals(3, items.size());
        assertEquals("CF_50_goldbar", items.get(0).itemName());
        assertEquals(4608, items.get(1).spriteId());
        assertEquals("escaped_name", items.get(2).itemName());
    }
}
