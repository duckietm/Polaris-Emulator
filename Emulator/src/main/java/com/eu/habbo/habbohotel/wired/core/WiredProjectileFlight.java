package com.eu.habbo.habbohotel.wired.core;

import java.util.List;
import java.util.function.IntBinaryOperator;

/**
 * A projectile's flight as the client draws it: the server moves the furni at once and the client
 * animates it from the old tile to the new one, so this follows that animation on the server.
 * Whoever stood on a tile of the path when the flight began is counted as a collision once the
 * animation reaches that tile; the tile it started on is never counted.
 */
public final class WiredProjectileFlight {
    public static final String POSITION_X = "@projectile.animation.position.x";
    public static final String POSITION_Y = "@projectile.animation.position.y";
    public static final String POSITION_ALTITUDE = "@projectile.animation.position.altitude";
    /** AIR's spelling; our earlier {@code is_traveling} stays readable as an alias. */
    public static final String IS_TRAVELING = "@projectile.animation.is_travelling";
    /** AIR's spelling; our earlier {@code tiles_traveled} stays readable as an alias. */
    public static final String TILES_TRAVELED = "@projectile.animation.tiles_travelled";

    public static final String FURNI_COLLISIONS = "@projectile.animation.furni_collisions";
    public static final String USER_COLLISIONS = "@projectile.animation.user_collisions";

    /** Our names before AIR's spelling. */
    static final String LEGACY_IS_TRAVELING = "@projectile.animation.is_traveling";

    static final String LEGACY_TILES_TRAVELED = "@projectile.animation.tiles_traveled";

    /**
     * In the order of the add-on's internal variables mask, bit 0 first: AIR's sub-variable ids (0 tiles
     * travelled, 1 user collisions, 2 furni collisions, 3 x, 4 y, 5 altitude, 6 is travelling).
     */
    public static final List<String> KEYS = List.of(
            TILES_TRAVELED, USER_COLLISIONS, FURNI_COLLISIONS, POSITION_X, POSITION_Y, POSITION_ALTITUDE, IS_TRAVELING);

    public static final int ALL_VARIABLES = (1 << KEYS.size()) - 1;

    static final int MAX_PATH_TILES = 256;

    private final int[] pathX;
    private final int[] pathY;
    private final int[] avatarsOnPath;
    private final int[] furniOnPath;
    private final int sourceX;
    private final int sourceY;
    private final int sourceZ;
    private final int targetX;
    private final int targetY;
    private final int targetZ;
    private final long startedAtMs;
    private final int durationMs;
    private final int variablesMask;

    private WiredProjectileFlight(
            int[] pathX,
            int[] pathY,
            int[] avatarsOnPath,
            int[] furniOnPath,
            int sourceX,
            int sourceY,
            int sourceZ,
            int targetX,
            int targetY,
            int targetZ,
            long startedAtMs,
            int durationMs,
            int variablesMask) {
        this.pathX = pathX;
        this.pathY = pathY;
        this.avatarsOnPath = avatarsOnPath;
        this.furniOnPath = furniOnPath;
        this.sourceX = sourceX;
        this.sourceY = sourceY;
        this.sourceZ = sourceZ;
        this.targetX = targetX;
        this.targetY = targetY;
        this.targetZ = targetZ;
        this.startedAtMs = startedAtMs;
        this.durationMs = durationMs;
        this.variablesMask = variablesMask & ALL_VARIABLES;
    }

    /**
     * A flight from one tile to another, altitudes in hundredths of a tile. The counters give how
     * many avatars, and how many other floor furni, stand on a tile when it begins.
     */
    public static WiredProjectileFlight launch(
            int sourceX,
            int sourceY,
            int sourceZ,
            int targetX,
            int targetY,
            int targetZ,
            long startedAtMs,
            int durationMs,
            int variablesMask,
            IntBinaryOperator avatarsAt,
            IntBinaryOperator furniAt) {
        int[][] path = buildPath(sourceX, sourceY, targetX, targetY);
        int[] avatars = new int[path[0].length];
        int[] furni = new int[path[0].length];

        for (int tile = 1; tile < avatars.length; tile++) {
            avatars[tile] = (avatarsAt != null) ? Math.max(0, avatarsAt.applyAsInt(path[0][tile], path[1][tile])) : 0;
            furni[tile] = (furniAt != null) ? Math.max(0, furniAt.applyAsInt(path[0][tile], path[1][tile])) : 0;
        }

        return new WiredProjectileFlight(
                path[0],
                path[1],
                avatars,
                furni,
                sourceX,
                sourceY,
                sourceZ,
                targetX,
                targetY,
                targetZ,
                startedAtMs,
                Math.max(0, durationMs),
                variablesMask);
    }

    /** The tiles a straight flight crosses, the longer axis one step at a time; xs then ys. */
    static int[][] buildPath(int sourceX, int sourceY, int targetX, int targetY) {
        int dx = targetX - sourceX;
        int dy = targetY - sourceY;
        int steps = Math.min(MAX_PATH_TILES - 1, Math.max(Math.abs(dx), Math.abs(dy)));
        int[] xs = new int[steps + 1];
        int[] ys = new int[steps + 1];

        for (int step = 0; step <= steps; step++) {
            double at = (steps == 0) ? 0d : step / (double) steps;
            xs[step] = (int) Math.round(sourceX + (dx * at));
            ys[step] = (int) Math.round(sourceY + (dy * at));
        }

        return new int[][] {xs, ys};
    }

    public double progress(long nowMs) {
        if (this.durationMs <= 0) {
            return 1d;
        }

        return Math.max(0d, Math.min(1d, (nowMs - this.startedAtMs) / (double) this.durationMs));
    }

    public boolean isTraveling(long nowMs) {
        return this.progress(nowMs) < 1d;
    }

    /** Tiles behind it; the whole path once it has landed. */
    public int tilesTraveled(long nowMs) {
        return (int) Math.floor(this.progress(nowMs) * (this.pathX.length - 1));
    }

    public int x(long nowMs) {
        return this.interpolate(this.sourceX, this.targetX, nowMs);
    }

    public int y(long nowMs) {
        return this.interpolate(this.sourceY, this.targetY, nowMs);
    }

    public int altitude(long nowMs) {
        return this.interpolate(this.sourceZ, this.targetZ, nowMs);
    }

    public int userCollisions(long nowMs) {
        return this.countUpTo(this.avatarsOnPath, nowMs);
    }

    public int furniCollisions(long nowMs) {
        return this.countUpTo(this.furniOnPath, nowMs);
    }

    public int pathLength() {
        return this.pathX.length;
    }

    public int variablesMask() {
        return this.variablesMask;
    }

    /** Whether the projectile holds the variable now: the add-on enabled it, and a flag only mid-flight. */
    public boolean holds(String key, long nowMs) {
        int bit = KEYS.indexOf(key);

        if (bit < 0 || (this.variablesMask & (1 << bit)) == 0) {
            return false;
        }

        return !IS_TRAVELING.equals(key) || this.isTraveling(nowMs);
    }

    /** The variable's value now, or null when it is not held or, like the traveling flag, has none. */
    public Integer read(String key, long nowMs) {
        if (!this.holds(key, nowMs)) {
            return null;
        }

        return switch (key) {
            case POSITION_X -> this.x(nowMs);
            case POSITION_Y -> this.y(nowMs);
            case POSITION_ALTITUDE -> this.altitude(nowMs);
            case TILES_TRAVELED -> this.tilesTraveled(nowMs);
            case FURNI_COLLISIONS -> this.furniCollisions(nowMs);
            case USER_COLLISIONS -> this.userCollisions(nowMs);
            default -> null;
        };
    }

    public static boolean isProjectileKey(String key) {
        return key != null && KEYS.contains(key);
    }

    private int interpolate(int from, int to, long nowMs) {
        return (int) Math.round(from + ((to - from) * this.progress(nowMs)));
    }

    private int countUpTo(int[] perTile, long nowMs) {
        int reached = this.tilesTraveled(nowMs);
        int total = 0;

        for (int tile = 1; tile <= reached && tile < perTile.length; tile++) {
            total += perTile[tile];
        }

        return total;
    }
}
