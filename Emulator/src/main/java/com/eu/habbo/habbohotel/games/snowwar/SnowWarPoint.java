package com.eu.habbo.habbohotel.games.snowwar;

/**
 * Simple immutable integer 2D point used for tile and world coordinates.
 */
public class SnowWarPoint {
    private final int x;
    private final int y;

    public SnowWarPoint(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public int getX() {
        return this.x;
    }

    public int getY() {
        return this.y;
    }

    public SnowWarPoint add(int dx, int dy) {
        return new SnowWarPoint(this.x + dx, this.y + dy);
    }

    public int getDistanceSquared(SnowWarPoint other) {
        int dx = this.x - other.x;
        int dy = this.y - other.y;
        return (dx * dx) + (dy * dy);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SnowWarPoint)) return false;
        SnowWarPoint other = (SnowWarPoint) o;
        return this.x == other.x && this.y == other.y;
    }

    @Override
    public int hashCode() {
        return (this.x * 31) + this.y;
    }

    @Override
    public String toString() {
        return "(" + this.x + ", " + this.y + ")";
    }
}
