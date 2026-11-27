package polaris.hotel.users.profile;

/**
 * How the avatar stands in the profile picture (the 68 x 135 box of the profile window and the room infostand): the
 * direction it faces and how far it is moved from its usual place, in pixels.
 *
 * <p>The box is 68 px wide and the avatar's body about 34 px, centred, so 16 px either way keeps it inside. Its feet
 * are on row 116 of 135 and its head near row 28 (higher on a stand), so 16 px up or down keeps it inside too.
 */
public record ProfileAvatarPose(int direction, int offsetX, int offsetY) {
    /** Sent as the direction, it tells the server to keep the saved pose. */
    public static final int KEEP = -1;

    public static final int DEFAULT_DIRECTION = 2;
    public static final int MIN_DIRECTION = 0;
    public static final int MAX_DIRECTION = 7;
    public static final int MAX_OFFSET = 16;

    public static final ProfileAvatarPose DEFAULT = new ProfileAvatarPose(DEFAULT_DIRECTION, 0, 0);

    /** A stored pose made safe to draw: an unknown direction faces the default way, offsets are clamped. */
    public static ProfileAvatarPose of(int direction, int offsetX, int offsetY) {
        return new ProfileAvatarPose(
                isDirection(direction) ? direction : DEFAULT_DIRECTION, clampOffset(offsetX), clampOffset(offsetY));
    }

    /**
     * The pose a request asks for. KEEP keeps the saved pose; a direction outside 0-7 is refused and the saved one
     * stays; an offset beyond the box is clamped to its edge.
     */
    public static ProfileAvatarPose resolve(int direction, int offsetX, int offsetY, ProfileAvatarPose current) {
        if (direction == KEEP) return current;

        return new ProfileAvatarPose(
                isDirection(direction) ? direction : current.direction(), clampOffset(offsetX), clampOffset(offsetY));
    }

    public static boolean isDirection(int direction) {
        return direction >= MIN_DIRECTION && direction <= MAX_DIRECTION;
    }

    public static int clampOffset(int offset) {
        return Math.max(-MAX_OFFSET, Math.min(MAX_OFFSET, offset));
    }
}
