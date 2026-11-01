package com.eu.habbo.habbohotel.wired.highscores;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Putting a board in a wired chest moves its row from items to wired_chest_items. The start-up clean-up of
 * deleted boards looked only at items, so every restart erased the scores of a board stored in a chest.
 */
class WiredHighscoreStoredBoardTest {

    @Test
    void aBoardStoredInAChestKeepsItsScores() {
        String sql = WiredHighscoreManager.DROP_ROWS_OF_DELETED_BOARDS;

        assertTrue(sql.contains("LEFT JOIN `items` i ON i.`id` = h.`item_id`"));
        assertTrue(sql.contains("WHERE i.`id` IS NULL"));
        assertTrue(sql.contains("AND NOT EXISTS (SELECT 1 FROM `wired_chest_items` s WHERE s.`id` = h.`item_id`)"));
    }
}
