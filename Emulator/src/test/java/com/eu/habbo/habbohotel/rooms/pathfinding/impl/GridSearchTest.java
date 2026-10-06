package com.eu.habbo.habbohotel.rooms.pathfinding.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomTileState;
import java.util.Arrays;
import java.util.Deque;
import java.util.PriorityQueue;
import java.util.Random;
import org.junit.jupiter.api.Test;

class GridSearchTest {
    private static final GridSearch.Rules RULES = rules(true, false, true);

    /**
     * {@code .} floor, {@code #} furni, {@code x} no tile, {@code 1}-{@code 9} floor at that stack height,
     * {@code S} seat, {@code L} bed, {@code U} someone standing, {@code O} furni the walker may pass.
     */
    private static final class Grid implements PathGrid {
        final RoomTile[][] tiles;
        final boolean[][] units;
        final boolean[][] overrides;
        final int width;
        final int height;

        Grid(String... rows) {
            this.height = rows.length;
            this.width = rows[0].length();
            this.tiles = new RoomTile[this.width][this.height];
            this.units = new boolean[this.width][this.height];
            this.overrides = new boolean[this.width][this.height];
            for (int y = 0; y < this.height; y++) {
                for (int x = 0; x < this.width; x++) {
                    char c = rows[y].charAt(x);
                    if (c == 'x') {
                        continue;
                    }
                    RoomTileState state =
                            switch (c) {
                                case '#', 'O' -> RoomTileState.BLOCKED;
                                case 'S' -> RoomTileState.SIT;
                                case 'L' -> RoomTileState.LAY;
                                default -> RoomTileState.OPEN;
                            };
                    RoomTile tile = new RoomTile((short) x, (short) y, (short) 0, state, true);
                    if (Character.isDigit(c)) {
                        tile.setStackHeight(c - '0');
                    }
                    this.tiles[x][y] = tile;
                    this.units[x][y] = c == 'U';
                    this.overrides[x][y] = c == 'O';
                }
            }
        }

        RoomTile at(int x, int y) {
            return this.tiles[x][y];
        }

        @Override
        public int width() {
            return this.width;
        }

        @Override
        public int height() {
            return this.height;
        }

        @Override
        public RoomTile tile(int x, int y) {
            return x < 0 || y < 0 || x >= this.width || y >= this.height ? null : this.tiles[x][y];
        }

        @Override
        public boolean occupied(int x, int y) {
            return this.units[x][y];
        }

        @Override
        public boolean canOverride(RoomTile tile) {
            return this.overrides[tile.x][tile.y];
        }
    }

    private static GridSearch.Rules rules(boolean diagonals, boolean walkthrough, boolean falling) {
        return new GridSearch.Rules(1.5, falling, diagonals, false, null, walkthrough, 0L);
    }

    private static Deque<RoomTile> find(Grid grid, int sx, int sy, int tx, int ty) {
        return find(new GridSearch(), grid, RULES, sx, sy, tx, ty, false);
    }

    private static Deque<RoomTile> find(
            GridSearch search, Grid grid, GridSearch.Rules rules, int sx, int sy, int tx, int ty, boolean nextTo) {
        RoomTile target = grid.at(tx, ty);
        return search.find(grid, rules, grid.at(sx, sy), target, target, false, nextTo);
    }

    /** Every step goes to a neighbouring tile and the path ends on {@code (tx, ty)}. */
    private static void assertWalkable(Deque<RoomTile> path, int sx, int sy, int tx, int ty) {
        assertFalse(path.isEmpty(), "a path");
        int x = sx;
        int y = sy;
        for (RoomTile step : path) {
            assertTrue(Math.max(Math.abs(step.x - x), Math.abs(step.y - y)) == 1, "step to a neighbour at " + step);
            x = step.x;
            y = step.y;
        }
        assertEquals(tx, x);
        assertEquals(ty, y);
    }

    private static int cost(Deque<RoomTile> path, int sx, int sy) {
        int total = 0;
        int x = sx;
        int y = sy;
        for (RoomTile step : path) {
            total += step.x != x && step.y != y ? 11 : 10;
            x = step.x;
            y = step.y;
        }
        return total;
    }

    @Test
    void straightAcrossAnEmptyRoom() {
        Grid grid = new Grid("..........");
        Deque<RoomTile> path = find(grid, 0, 0, 9, 0);
        assertWalkable(path, 0, 0, 9, 0);
        assertEquals(9, path.size());
    }

    @Test
    void diagonalsGiveTheShortestRouteNotAZigZag() {
        Grid grid = new Grid("........", "........", "........", "........", "........");
        Deque<RoomTile> path = find(grid, 0, 0, 7, 4);
        assertWalkable(path, 0, 0, 7, 4);
        assertEquals(7, path.size(), "as many steps as the larger distance");
        assertEquals(4 * 11 + 3 * 10, cost(path, 0, 0));
    }

    @Test
    void walksRoundFurniThroughTheGap() {
        Grid grid = new Grid(".....", "####.", ".....", ".####", ".....");
        Deque<RoomTile> path = find(grid, 0, 0, 4, 4);
        assertWalkable(path, 0, 0, 4, 4);
        path.forEach(step -> assertEquals(RoomTileState.OPEN, step.state));
    }

    @Test
    void noPathIntoAClosedOffArea() {
        Grid grid = new Grid("..#..", "..#..", "..#..");
        assertTrue(find(grid, 0, 1, 4, 1).isEmpty());
    }

    @Test
    void cutsACornerPastOneFurniButNotBetweenTwo() {
        Grid one = new Grid("..", "#.");
        assertEquals(1, find(one, 0, 0, 1, 1).size(), "diagonal past a single furni");

        Grid two = new Grid(".#", "#.");
        assertTrue(find(two, 0, 0, 1, 1).isEmpty(), "no squeezing between two furni");
    }

    @Test
    void stepsUpAtMostTheStepHeightAndFallsUnlessForbidden() {
        Grid wall = new Grid(".3.");
        assertTrue(find(wall, 0, 0, 2, 0).isEmpty(), "3 is too high to step up");

        Grid stairs = new Grid(".123");
        assertWalkable(find(stairs, 0, 0, 3, 0), 0, 0, 3, 0);

        Grid ledge = new Grid("3.");
        assertWalkable(find(ledge, 0, 0, 1, 0), 0, 0, 1, 0);
        RoomTile target = ledge.at(1, 0);
        assertTrue(
                new GridSearch()
                        .find(ledge, rules(true, false, false), ledge.at(0, 0), target, target, false, false)
                        .isEmpty(),
                "no falling when the room forbids it");
    }

    @Test
    void seatsAndBedsOnlyAsTheGoal() {
        Grid grid = new Grid(".S.");
        assertTrue(find(grid, 0, 0, 2, 0).isEmpty(), "no walking over a chair");
        assertWalkable(find(grid, 0, 0, 1, 0), 0, 0, 1, 0);

        Grid bed = new Grid(".L.");
        assertTrue(find(bed, 0, 0, 2, 0).isEmpty());
    }

    @Test
    void someoneStandingBlocksExceptNearTheDoorOrOnAWalkthroughRetry() {
        Grid grid = new Grid(".U.", "###");
        assertTrue(find(grid, 0, 0, 2, 0).isEmpty());

        RoomTile target = grid.at(2, 0);
        GridSearch.Rules nearDoor = new GridSearch.Rules(1.5, true, true, false, grid.at(1, 0), false, 0L);
        assertWalkable(new GridSearch().find(grid, nearDoor, grid.at(0, 0), target, target, false, false), 0, 0, 2, 0);

        GridSearch.Rules walkthrough = rules(true, true, true);
        assertWalkable(
                new GridSearch().find(grid, walkthrough, grid.at(0, 0), target, target, true, false), 0, 0, 2, 0);
    }

    @Test
    void someoneOnTheGoalIsWalkedUpTo() {
        Grid grid = new Grid("....U");
        assertTrue(find(grid, 0, 0, 4, 0).isEmpty());

        Deque<RoomTile> path = find(new GridSearch(), grid, RULES, 0, 0, 4, 0, true);
        assertWalkable(path, 0, 0, 3, 0);
    }

    @Test
    void furniTheWalkerMayPassIsWalkable() {
        Grid grid = new Grid(".O.", "###");
        assertWalkable(find(grid, 0, 0, 2, 0), 0, 0, 2, 0);
    }

    @Test
    void withoutDiagonalsOnlyStraightSteps() {
        Grid grid = new Grid("....", "....", "....");
        RoomTile target = grid.at(3, 2);
        Deque<RoomTile> path =
                new GridSearch().find(grid, rules(false, false, true), grid.at(0, 0), target, target, false, false);
        assertEquals(5, path.size());
        RoomTile previous = grid.at(0, 0);
        for (RoomTile step : path) {
            assertEquals(1, Math.abs(step.x - previous.x) + Math.abs(step.y - previous.y));
            previous = step;
        }
    }

    @Test
    void aReusedSearchForgetsThePreviousOne() {
        GridSearch search = new GridSearch();
        Grid first = new Grid("..#..", ".....");
        Grid second = new Grid(".#...", ".#...", "...#.");
        for (int round = 0; round < 3; round++) {
            assertWalkable(find(search, first, RULES, 0, 0, 4, 0, false), 0, 0, 4, 0);
            assertWalkable(find(search, second, RULES, 0, 0, 4, 0, false), 0, 0, 4, 0);
        }
    }

    @Test
    void alwaysAShortestRouteOnRandomRooms() {
        Random random = new Random(7);
        GridSearch search = new GridSearch();
        for (int room = 0; room < 300; room++) {
            int width = 4 + random.nextInt(20);
            int height = 4 + random.nextInt(20);
            String[] rows = new String[height];
            for (int y = 0; y < height; y++) {
                char[] row = new char[width];
                for (int x = 0; x < width; x++) {
                    row[x] = random.nextInt(100) < 28 ? '#' : '.';
                }
                rows[y] = new String(row);
            }
            rows[0] = '.' + rows[0].substring(1);
            rows[height - 1] = rows[height - 1].substring(0, width - 1) + '.';
            Grid grid = new Grid(rows);

            int expected = referenceCost(grid, 0, 0, width - 1, height - 1);
            Deque<RoomTile> path = find(search, grid, RULES, 0, 0, width - 1, height - 1, false);
            if (expected < 0) {
                assertTrue(path.isEmpty(), "room " + room + " has no route");
            } else {
                assertWalkable(path, 0, 0, width - 1, height - 1);
                assertEquals(expected, cost(path, 0, 0), "room " + room + " shortest route");
            }
        }
    }

    @Test
    void crossesALargeOpenRoomQuickly() {
        String[] rows = new String[64];
        Arrays.fill(rows, ".".repeat(64));
        Grid grid = new Grid(rows);
        GridSearch search = new GridSearch();

        Deque<RoomTile> path = find(search, grid, RULES, 0, 0, 63, 40, false);
        assertEquals(63, path.size());
        assertTrue(search.expanded() <= 200, "expanded " + search.expanded() + " tiles for a 63 step walk");

        long started = System.nanoTime();
        for (int i = 0; i < 2000; i++) {
            find(search, grid, RULES, i % 64, 0, 63 - (i % 64), 63, false);
        }
        long micros = (System.nanoTime() - started) / 2000 / 1000;
        assertTrue(micros < 2000, "average search took " + micros + " us");
    }

    /** Dijkstra over floor and furni only, with the same corner rule, as a reference for the cost. */
    private static int referenceCost(Grid grid, int sx, int sy, int tx, int ty) {
        int[] best = new int[grid.width * grid.height];
        Arrays.fill(best, Integer.MAX_VALUE);
        PriorityQueue<int[]> queue = new PriorityQueue<>((a, b) -> Integer.compare(a[0], b[0]));
        best[sy * grid.width + sx] = 0;
        queue.add(new int[] {0, sx, sy});
        int[][] moves = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}, {1, 1}, {-1, -1}, {-1, 1}, {1, -1}};
        while (!queue.isEmpty()) {
            int[] at = queue.poll();
            if (at[0] > best[at[2] * grid.width + at[1]]) {
                continue;
            }
            if (at[1] == tx && at[2] == ty) {
                return at[0];
            }
            for (int m = 0; m < moves.length; m++) {
                int nx = at[1] + moves[m][0];
                int ny = at[2] + moves[m][1];
                RoomTile next = grid.tile(nx, ny);
                if (next == null || !next.isWalkable()) {
                    continue;
                }
                if (m >= 4) {
                    RoomTile alongX = grid.tile(nx, at[2]);
                    RoomTile alongY = grid.tile(at[1], ny);
                    if (alongX == null || alongY == null || (!alongX.isWalkable() && !alongY.isWalkable())) {
                        continue;
                    }
                }
                int cost = at[0] + (m >= 4 ? 11 : 10);
                if (cost < best[ny * grid.width + nx]) {
                    best[ny * grid.width + nx] = cost;
                    queue.add(new int[] {cost, nx, ny});
                }
            }
        }
        return -1;
    }
}
