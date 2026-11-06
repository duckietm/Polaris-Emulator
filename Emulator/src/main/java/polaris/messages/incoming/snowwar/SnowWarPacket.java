package polaris.messages.incoming.snowwar;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.hotel.games.snowwar.SnowWarConstants;
import polaris.hotel.games.snowwar.SnowWarGame;
import polaris.hotel.games.snowwar.SnowWarManager;

/**
 * Strict reading of the SnowWar gameplay packets.
 *
 * <p>{@code ClientMessage.readInt()} returns 0 when a packet is too short, so a
 * truncated walk used to become a walk to (0,0). These checks only accept a
 * payload of exactly the expected fields, optionally followed by the AIR/Octane
 * turn and subturn ints (which the server-authoritative game ignores), and
 * report anything else as malformed so the handler drops it.
 */
final class SnowWarPacket {

    private static final Logger LOGGER = LoggerFactory.getLogger(SnowWarPacket.class);

    /** Clients may append turn and subturn after the action fields. */
    static final int MAX_TRAILING_TURN_INTS = 2;

    /** Game chat is cut to 100 characters; 4 UTF-8 bytes each is the most a real message needs. */
    static final int MAX_CHAT_BYTES = 400;

    private SnowWarPacket() {}

    /**
     * True when the payload is exactly {@code count} ints, optionally followed
     * by up to two turn ints. Handlers then read the ints themselves, so the
     * packet contract extractor still sees every field.
     */
    static boolean hasInts(int bytesAvailable, int count) {
        int extra = bytesAvailable - (count * Integer.BYTES);
        return extra >= 0 && extra % Integer.BYTES == 0 && extra / Integer.BYTES <= MAX_TRAILING_TURN_INTS;
    }

    /**
     * True when a length-prefixed string of {@code declaredLength} bytes is the
     * whole payload and no longer than {@code maxBytes}. Handlers read the string
     * themselves so the packet contract extractor still sees the field.
     */
    static boolean isWholeString(int bytesAvailable, int declaredLength, int maxBytes) {
        return declaredLength >= 0 && declaredLength <= maxBytes && bytesAvailable == Short.BYTES + declaredLength;
    }

    /** AIR's throw trajectories: quick, short lob, long lob and the adaptive default. */
    static boolean isTrajectory(int trajectory) {
        return trajectory >= SnowWarConstants.TRAJECTORY_QUICK && trajectory <= SnowWarConstants.TRAJECTORY_DEFAULT;
    }

    /** A world coordinate pair inside the arena (tile 0 up to the far edge of the last tile). */
    static boolean isInArena(SnowWarGame game, int worldX, int worldY) {
        if (game.getMap() == null) {
            return false;
        }
        return isInArena(game.getMap().getSizeX(), game.getMap().getSizeY(), worldX, worldY);
    }

    static boolean isInArena(int sizeX, int sizeY, int worldX, int worldY) {
        long maxX = (long) sizeX * SnowWarConstants.TILE_SIZE;
        long maxY = (long) sizeY * SnowWarConstants.TILE_SIZE;
        return worldX >= 0 && worldY >= 0 && worldX <= maxX && worldY <= maxY;
    }

    /** Drops a malformed packet, logging it at most once a minute per user. */
    static void reject(int userId, String packetName, String reason) {
        if (SnowWarManager.getInstance().shouldReportMalformed(userId)) {
            LOGGER.warn("SnowWar: dropped malformed {} from user {} ({}).", packetName, userId, reason);
        }
    }
}
