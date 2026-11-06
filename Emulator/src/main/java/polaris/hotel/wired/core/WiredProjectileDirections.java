package polaris.hotel.wired.core;

public final class WiredProjectileDirections {
    public static final int EIGHT_STRAIGHT = 0;

    public static final int EIGHT_DIFFUSE = 1;

    public static final int FOUR_PREFER_VERTICAL = 2;

    public static final int FOUR_PREFER_HORIZONTAL = 3;

    public static final int NORTH = 0;
    public static final int NORTH_EAST = 1;
    public static final int EAST = 2;
    public static final int SOUTH_EAST = 3;
    public static final int SOUTH = 4;
    public static final int SOUTH_WEST = 5;
    public static final int WEST = 6;
    public static final int NORTH_WEST = 7;

    private static final double DIFFUSE_AXIS_SLOPE = 0.41421356237309503;

    private WiredProjectileDirections() {}

    public static int normalizeSystem(int system) {
        return (system >= EIGHT_STRAIGHT && system <= FOUR_PREFER_HORIZONTAL) ? system : EIGHT_STRAIGHT;
    }

    public static int resolve(int system, int dx, int dy) {
        if (dx == 0 && dy == 0) {
            return -1;
        }

        int ax = Math.abs(dx);
        int ay = Math.abs(dy);
        int normalized = normalizeSystem(system);

        boolean diagonal =
                switch (normalized) {
                    case EIGHT_STRAIGHT -> ax != 0 && ay != 0;
                    case EIGHT_DIFFUSE -> Math.min(ax, ay) > Math.max(ax, ay) * DIFFUSE_AXIS_SLOPE;
                    default -> false;
                };

        if (diagonal) {
            if (dx > 0) {
                return dy > 0 ? SOUTH_EAST : NORTH_EAST;
            }

            return dy > 0 ? SOUTH_WEST : NORTH_WEST;
        }

        boolean horizontal = (normalized == FOUR_PREFER_VERTICAL) ? ax > ay : ax >= ay;

        if (horizontal) {
            return dx > 0 ? EAST : WEST;
        }

        return dy > 0 ? SOUTH : NORTH;
    }

    public static int rotate(int direction, int eighthTurns) {
        return Math.floorMod(direction + eighthTurns, 8);
    }
}
