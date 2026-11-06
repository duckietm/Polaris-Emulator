package polaris.hotel.rooms.pathfinding.impl;

public class Rotation {
    /**
     * The directions to move in: Left, right, up, down
     */
    protected static final short[][] DIRECTIONS = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};

    /**
     * The diagonal directions to move in: NE, SW, NW, SE
     */
    protected static final short[][] DIAGONAL_DIRECTIONS = {{1, 1}, {-1, -1}, {-1, 1}, {1, -1}};

    private Rotation() {
        throw new IllegalStateException("Utility class");
    }
}
