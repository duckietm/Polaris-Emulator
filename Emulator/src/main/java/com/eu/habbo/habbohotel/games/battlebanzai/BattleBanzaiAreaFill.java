package com.eu.habbo.habbohotel.games.battlebanzai;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Finds the area a team closes off with its locked tiles. Works on a snapshot of tile positions, so it
 * never reads the live game state, and walks the field iteratively: every position is visited once, and
 * only positions of the field are visited.
 */
final class BattleBanzaiAreaFill {
    private BattleBanzaiAreaFill() {}

    static int key(int x, int y) {
        return (x << 16) | (y & 0xFFFF);
    }

    /**
     * The positions enclosed from the start position, or null when the area reaches the edge of the
     * field or a tile locked by another team.
     */
    static List<Integer> enclosedArea(
            int startX, int startY, Set<Integer> field, Set<Integer> ownLocked, Set<Integer> foreignLocked) {
        List<Integer> area = new ArrayList<>();
        Set<Integer> visited = new HashSet<>();
        Deque<int[]> pending = new ArrayDeque<>();
        pending.push(new int[] {startX, startY});

        while (!pending.isEmpty()) {
            int[] position = pending.pop();
            int x = position[0];
            int y = position[1];
            int key = key(x, y);

            if (!field.contains(key) || foreignLocked.contains(key)) return null;

            if (ownLocked.contains(key) || !visited.add(key)) continue;

            area.add(key);

            pending.push(new int[] {x, y - 1});
            pending.push(new int[] {x, y + 1});
            pending.push(new int[] {x - 1, y});
            pending.push(new int[] {x + 1, y});
        }

        return area;
    }
}
