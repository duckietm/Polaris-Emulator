package polaris.util.pathfinding;

/**
 * 8-directional movement utility for ball physics.
 * Ported from the Rebug soccer plugin.
 */
public class Direction8 {
    public static final Direction8[] DIRECTIONS = new Direction8[8];

    public static final Direction8 N = new Direction8(0, "N", 0, -1);
    public static final Direction8 NE = new Direction8(1, "NE", 1, -1);
    public static final Direction8 E = new Direction8(2, "E", 1, 0);
    public static final Direction8 SE = new Direction8(3, "SE", 1, 1);
    public static final Direction8 S = new Direction8(4, "S", 0, 1);
    public static final Direction8 SW = new Direction8(5, "SW", -1, 1);
    public static final Direction8 W = new Direction8(6, "W", -1, 0);
    public static final Direction8 NW = new Direction8(7, "NW", -1, -1);

    private final int rot;
    private final String rotName;
    private final int xDiff;
    private final int yDiff;

    public Direction8(int rot, String rotName, int diffX, int diffY) {
        this.rot = rot;
        this.rotName = rotName;
        this.xDiff = diffX;
        this.yDiff = diffY;
        DIRECTIONS[rot] = this;
    }

    public static Direction8 fromDelta(int deltaX, int deltaY) {
        if (deltaX == 0) {
            if (deltaY < 0) return N;
            if (deltaY > 0) return S;
        }
        if (deltaX > 0) {
            if (deltaY < 0) return NE;
            if (deltaY == 0) return E;
            if (deltaY > 0) return SE;
        }
        if (deltaX < 0) {
            if (deltaY < 0) return NW;
            if (deltaY == 0) return W;
            if (deltaY > 0) return SW;
        }
        return N;
    }

    public static Direction8 getDirection(int dir) {
        if (dir < 0 || dir > 7) return N;
        return DIRECTIONS[dir];
    }

    public static int validateDirection8Value(int dir) {
        return dir & 0x7;
    }

    public int getRot() {
        return this.rot;
    }

    public String getRotName() {
        return this.rotName;
    }

    public int getDiffX() {
        return this.xDiff;
    }

    public int getDiffY() {
        return this.yDiff;
    }

    public Direction8 rotateDirection180Degrees() {
        return getDirectionAtRot(4);
    }

    public Direction8 rotateDirection90Degrees(boolean clockwise) {
        return getDirectionAtRot(clockwise ? 2 : -2);
    }

    public Direction8 rotateDirection45Degrees(boolean clockwise) {
        return getDirectionAtRot(clockwise ? 1 : -1);
    }

    public Direction8 getDirectionAtRot(int diff) {
        return DIRECTIONS[validateDirection8Value(this.rot + diff)];
    }

    @Override
    public String toString() {
        return this.rotName + "(" + this.rot + ")";
    }
}
