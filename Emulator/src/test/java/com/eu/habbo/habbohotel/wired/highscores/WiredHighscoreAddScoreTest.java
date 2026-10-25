package com.eu.habbo.habbohotel.wired.highscores;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The give-points box copied the board without its lock, added to its copy and rewrote the whole board
 * on the shared pool: concurrent boxes and games lost each other's points, two rewrites of one board
 * left duplicate or lost rows behind, and the sum could overflow.
 */
class WiredHighscoreAddScoreTest {

    @Test
    void pointsAddUpOnOneRowPerTeam() {
        WiredHighscoreManager manager = new WiredHighscoreManager();
        try {
            manager.addScore(101, List.of(7, 8), 10, 1_000);
            manager.addScore(101, List.of(8, 7), 15, 1_001);
            manager.addScore(101, List.of(9), 4, 1_002);

            List<WiredHighscoreDataEntry> board = manager.getEntriesForItemId(101);
            assertEquals(2, board.size(), "the same team, listed in another order, is one row");
            assertEquals(25, board.get(0).getScore());
            assertEquals(1_001, board.get(0).getTimestamp());
            assertEquals(4, board.get(1).getScore());
        } finally {
            manager.dispose();
        }
    }

    @Test
    void concurrentAdditionsAreNotLost() throws Exception {
        WiredHighscoreManager manager = new WiredHighscoreManager();
        try {
            List<Thread> threads = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                threads.add(Thread.ofPlatform().start(() -> {
                    for (int n = 0; n < 500; n++) {
                        manager.addScore(202, List.of(1), 1, n);
                    }
                }));
            }
            for (Thread thread : threads) {
                thread.join(10_000);
            }

            assertEquals(4_000, manager.getEntriesForItemId(202).get(0).getScore());
        } finally {
            manager.dispose();
        }
    }

    @Test
    void aScoreStopsAtTheLimitInsteadOfWrapping() {
        assertEquals(Integer.MAX_VALUE, WiredHighscoreManager.saturatedSum(Integer.MAX_VALUE - 1, 50));
        assertEquals(Integer.MIN_VALUE, WiredHighscoreManager.saturatedSum(Integer.MIN_VALUE + 1, -50));
        assertEquals(12, WiredHighscoreManager.saturatedSum(5, 7));
    }
}
