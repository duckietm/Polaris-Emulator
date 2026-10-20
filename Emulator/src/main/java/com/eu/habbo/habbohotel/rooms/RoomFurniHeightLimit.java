package com.eu.habbo.habbohotel.rooms;

/**
 * How high a furni move with an explicit altitude may go. Everything stays under {@link Room#MAXIMUM_FURNI_HEIGHT}
 * (the build and stacking ceiling the client is told about) except a wired altitude move: AIR's Set Furni Altitude
 * slider runs to 80.00 tiles, so a move started inside {@link #beginWiredAltitude()} may go up to {@link
 * #WIRED_ALTITUDE_MAX}. Moves are carried out on the calling thread, so the scope covers exactly the box's moves.
 */
public final class RoomFurniHeightLimit {
    /** AIR's Set Furni Altitude range: 0..8000 hundredths of a tile. */
    public static final double WIRED_ALTITUDE_MAX = 80d;

    private static final ThreadLocal<Integer> WIRED_ALTITUDE_DEPTH = new ThreadLocal<>();

    private RoomFurniHeightLimit() {}

    /** The highest altitude a move with an explicit altitude may reach right now. */
    public static double current() {
        Integer depth = WIRED_ALTITUDE_DEPTH.get();
        return depth != null && depth > 0
                ? Math.max(Room.MAXIMUM_FURNI_HEIGHT, WIRED_ALTITUDE_MAX)
                : Room.MAXIMUM_FURNI_HEIGHT;
    }

    /** The highest altitude a wired altitude move may ask for. */
    public static double wiredAltitudeMax() {
        return Math.max(Room.MAXIMUM_FURNI_HEIGHT, WIRED_ALTITUDE_MAX);
    }

    /** Lets the moves made until {@link Scope#close()} go up to {@link #wiredAltitudeMax()}. */
    public static Scope beginWiredAltitude() {
        Integer depth = WIRED_ALTITUDE_DEPTH.get();
        WIRED_ALTITUDE_DEPTH.set(depth == null ? 1 : depth + 1);
        return () -> {
            Integer current = WIRED_ALTITUDE_DEPTH.get();
            if (current == null || current <= 1) {
                WIRED_ALTITUDE_DEPTH.remove();
            } else {
                WIRED_ALTITUDE_DEPTH.set(current - 1);
            }
        };
    }

    /** A wired altitude scope, closed once and in the order the scopes were opened. */
    @FunctionalInterface
    public interface Scope extends AutoCloseable {
        @Override
        void close();
    }
}
