package polaris.hotel.rooms;

/**
 * How a user sent to another room by a furni will arrive there. It is told before the forward and
 * read once, when the user opens that room: a teleporter puts them on its pair, a room link only
 * marks the entry. The navigator's own checks still decide whether they get in.
 */
public record PendingRoomEntry(int roomId, String method, int teleportItemId, long expiresAt) {
    public static final String METHOD_DOOR = "door";
    public static final String METHOD_TELEPORT = "teleport";
    public static final String METHOD_ROOM_NETWORK = "room_network";

    /** Long enough for the client to ask for the room, short enough not to linger past a refusal. */
    public static final long LIFETIME_MS = 15_000L;

    public static PendingRoomEntry teleport(int roomId, int teleportItemId, long now) {
        return new PendingRoomEntry(roomId, METHOD_TELEPORT, teleportItemId, now + LIFETIME_MS);
    }

    public static PendingRoomEntry roomNetwork(int roomId, long now) {
        return new PendingRoomEntry(roomId, METHOD_ROOM_NETWORK, 0, now + LIFETIME_MS);
    }

    public boolean appliesTo(int roomId, long now) {
        return this.roomId == roomId && now <= this.expiresAt;
    }
}
