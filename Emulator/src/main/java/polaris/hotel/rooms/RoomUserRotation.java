package polaris.hotel.rooms;

public enum RoomUserRotation {
    NORTH(0),
    NORTH_EAST(1),
    EAST(2),
    SOUTH_EAST(3),
    SOUTH(4),
    SOUTH_WEST(5),
    WEST(6),
    NORTH_WEST(7);

    private final int direction;

    RoomUserRotation(int direction) {
        this.direction = direction;
    }

    public static RoomUserRotation fromValue(int rotation) {
        rotation %= 8;
        for (RoomUserRotation rot : values()) {
            if (rot.getValue() == rotation) {
                return rot;
            }
        }

        return NORTH;
    }

    public static RoomUserRotation counterClockwise(RoomUserRotation rotation) {
        return fromValue(rotation.getValue() + 7);
    }

    public static RoomUserRotation clockwise(RoomUserRotation rotation) {
        return fromValue(rotation.getValue() + 9);
    }

    public int getValue() {
        return this.direction;
    }

    public RoomUserRotation getOpposite() {
        switch (this) {
            case NORTH:
                return RoomUserRotation.SOUTH;
            case NORTH_EAST:
                return RoomUserRotation.SOUTH_WEST;
            case EAST:
                return RoomUserRotation.WEST;
            case SOUTH_EAST:
                return RoomUserRotation.NORTH_WEST;
            case SOUTH:
                return RoomUserRotation.NORTH;
            case SOUTH_WEST:
                return RoomUserRotation.NORTH_EAST;
            case WEST:
                return RoomUserRotation.EAST;
            case NORTH_WEST:
                return RoomUserRotation.SOUTH_EAST;
        }
        return null;
    }

    public static int rotationDistance(int a, int b) {
        int diff = Math.abs(a - b);
        return Math.min(diff, 8 - diff);
    }
}
