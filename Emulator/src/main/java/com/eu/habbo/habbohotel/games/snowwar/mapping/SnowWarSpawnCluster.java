package com.eu.habbo.habbohotel.games.snowwar.mapping;

/**
 * A circular spawn region on the arena (README 5.3).
 */
public class SnowWarSpawnCluster {

    private final int x;
    private final int y;
    private final int radius;
    private final int minDistance;

    public SnowWarSpawnCluster(int x, int y, int radius, int minDistance) {
        this.x = x;
        this.y = y;
        this.radius = radius;
        this.minDistance = minDistance;
    }

    public int getX() {
        return this.x;
    }

    public int getY() {
        return this.y;
    }

    public int getRadius() {
        return this.radius;
    }

    public int getMinDistance() {
        return this.minDistance;
    }
}
