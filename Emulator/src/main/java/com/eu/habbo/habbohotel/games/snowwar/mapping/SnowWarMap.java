package com.eu.habbo.habbohotel.games.snowwar.mapping;

import com.eu.habbo.habbohotel.games.snowwar.SnowWarPoint;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;

/**
 * Parsed SnowWar arena (README 5.4).
 */
public class SnowWarMap {

    private final int mapId;
    private final List<String> heightmapRows;
    private final List<SnowWarItem> items;
    private final List<SnowWarPoint> machinePositions;
    private final List<SnowWarSpawnCluster> spawnClusters;

    private final int sizeX;
    private final int sizeY;
    private final SnowWarTile[][] tiles;

    public SnowWarMap(
            int mapId,
            List<String> heightmapRows,
            List<SnowWarItem> items,
            List<SnowWarPoint> machinePositions,
            List<SnowWarSpawnCluster> spawnClusters) {
        this.mapId = mapId;
        this.heightmapRows = heightmapRows;
        this.items = items;
        this.machinePositions = machinePositions;
        this.spawnClusters = spawnClusters;

        this.sizeY = heightmapRows.size();
        this.sizeX = heightmapRows.isEmpty() ? 0 : heightmapRows.get(0).length();
        this.tiles = new SnowWarTile[this.sizeX][this.sizeY];

        for (int y = 0; y < this.sizeY; y++) {
            String row = heightmapRows.get(y);

            for (int x = 0; x < this.sizeX; x++) {
                char tileChar = x < row.length() ? row.charAt(x) : 'x';
                boolean blocked = tileChar == 'X' || tileChar == 'x';

                List<SnowWarItem> itemsAtPosition = new ArrayList<>();
                for (SnowWarItem item : items) {
                    // A furni occupies its whole footprint (width x length,
                    // extending +x/+y from its origin tile, swapped for the
                    // 90/270 rotations) - not just the origin tile - so a 3x3
                    // prop blocks all nine tiles it covers, matching the room
                    // engine. Single-tile props keep their old behaviour.
                    int effW = item.getEffectiveWidth();
                    int effL = item.getEffectiveLength();
                    if (x >= item.getX() && x < item.getX() + effW && y >= item.getY() && y < item.getY() + effL) {
                        itemsAtPosition.add(item);
                    }
                }

                this.tiles[x][y] = new SnowWarTile(x, y, blocked, itemsAtPosition);
            }
        }
    }

    public int getMapId() {
        return this.mapId;
    }

    public int getSizeX() {
        return this.sizeX;
    }

    public int getSizeY() {
        return this.sizeY;
    }

    public SnowWarTile getTile(int x, int y) {
        if (x < 0 || y < 0 || x >= this.sizeX || y >= this.sizeY) {
            return null;
        }
        return this.tiles[x][y];
    }

    public SnowWarTile getTile(SnowWarPoint position) {
        return this.getTile(position.getX(), position.getY());
    }

    public List<SnowWarItem> getItems() {
        return this.items;
    }

    /**
     * Items serialized in the LevelData packet (machine tiles are excluded,
     * machines have their own section).
     */
    public List<SnowWarItem> getVisibleItems() {
        List<SnowWarItem> visible = new ArrayList<>();
        for (SnowWarItem item : this.items) {
            if (!item.isHidden()) {
                visible.add(item);
            }
        }
        return visible;
    }

    public List<SnowWarPoint> getMachinePositions() {
        return this.machinePositions;
    }

    public List<SnowWarSpawnCluster> getSpawnClusters() {
        return this.spawnClusters;
    }

    /**
     * Heightmap in wire format: rows separated by (char) 13.
     */
    public String getHeightmapForPacket() {
        return String.join(String.valueOf((char) 13), this.heightmapRows);
    }

    /**
     * Picks a random walkable spawn tile inside the given spawn cluster,
     * avoiding already occupied tiles.
     */
    /**
     * Random spawn tile, spread out from already-assigned spawns: starts by
     * demanding a generous distance between players and relaxes the
     * requirement until candidates exist. Runs before the deterministic tick
     * loop starts and the resulting positions are transmitted in the full
     * game status, so server-side randomness is safe here.
     */
    public SnowWarPoint generateSpawn(Collection<SnowWarPoint> occupied, Random random) {
        for (int minDistance = 12; minDistance >= 0; minDistance -= 2) {
            int minDistanceSquared = minDistance * minDistance;
            List<SnowWarPoint> candidates = new ArrayList<>();

            for (int y = 0; y < this.sizeY; y++) {
                for (int x = 0; x < this.sizeX; x++) {
                    if (!this.tiles[x][y].isWalkable()) {
                        continue;
                    }

                    SnowWarPoint candidate = new SnowWarPoint(x, y);
                    boolean valid = true;
                    for (SnowWarPoint used : occupied) {
                        if (candidate.getDistanceSquared(used) < Math.max(1, minDistanceSquared)) {
                            valid = false;
                            break;
                        }
                    }

                    if (valid) {
                        candidates.add(candidate);
                    }
                }
            }

            if (!candidates.isEmpty()) {
                return candidates.get(random.nextInt(candidates.size()));
            }
        }

        return new SnowWarPoint(0, 0);
    }
}
