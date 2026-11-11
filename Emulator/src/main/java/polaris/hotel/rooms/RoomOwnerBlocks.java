package polaris.hotel.rooms;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.WiredPlatform;
import polaris.hotel.permissions.Permission;
import polaris.hotel.users.User;

/**
 * The official client's "blocked by the room owner" entry refusal: a player on the room owner's block list
 * ({@code users_blocked}, the list the profile's block button fills) cannot enter that owner's rooms. The owner's list
 * is read from the owner when they are online and from the table otherwise. Staff who may enter any room are let in,
 * as past a room ban. A player who is in one of the owner's rooms at the moment they are blocked is shown out.
 */
public final class RoomOwnerBlocks {
    private static final Logger LOGGER = LoggerFactory.getLogger(RoomOwnerBlocks.class);

    private final IntFunction<User> onlineUser;
    private final Supplier<DataSource> dataSource;

    RoomOwnerBlocks(IntFunction<User> onlineUser, Supplier<DataSource> dataSource) {
        this.onlineUser = Objects.requireNonNull(onlineUser, "onlineUser");
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    /** The check against the running hotel. */
    static RoomOwnerBlocks live() {
        return new RoomOwnerBlocks(
                id -> WiredPlatform.gameEnvironment().getUserManager().getUser(id),
                () -> WiredPlatform.database().getDataSource());
    }

    /** Whether the room's owner blocked this visitor, so the visitor is refused at the door. */
    boolean refusesEntry(User visitor, Room room) {
        if (visitor == null || room == null) return false;

        int ownerId = room.getOwnerId();
        int visitorId = visitor.getUserInfo().getId();

        if (!mayBeRefused(ownerId, visitorId)) return false;
        if (visitor.hasPermission(Permission.ACC_ANYROOMOWNER) || visitor.hasPermission(Permission.ACC_ENTERANYROOM)) {
            return false;
        }

        User owner = this.onlineUser.apply(ownerId);
        if (owner != null) return owner.getUserStats().userBlocked(visitorId);

        return this.storedBlock(ownerId, visitorId);
    }

    /**
     * Shows a player out of the blocker's room, when the block finds them there: they could no longer enter it, so
     * they do not stay either. Like a room kick: the player walks to the door and is told they were removed. Staff who
     * may enter any room, and players who cannot be kicked, stay.
     *
     * @return whether the player was shown out
     */
    public static boolean showOut(User blocker, User blocked) {
        if (blocker == null || blocked == null) return false;

        Room room = blocked.getUserInfo().getCurrentRoom();
        if (room == null
                || !leavesOnBlock(
                        blocker.getUserInfo().getId(),
                        room.getOwnerId(),
                        blocked.hasPermission(Permission.ACC_ANYROOMOWNER)
                                || blocked.hasPermission(Permission.ACC_ENTERANYROOM)
                                || blocked.hasPermission(Permission.ACC_UNKICKABLE))) {
            return false;
        }

        room.kickUser(blocked, true);
        return true;
    }

    /** A block removes the player from a room only when the blocker owns it and the player has no exemption. */
    static boolean leavesOnBlock(int blockerId, int roomOwnerId, boolean exempt) {
        return blockerId > 0 && blockerId == roomOwnerId && !exempt;
    }

    /** A room without an owner refuses nobody, and nobody is refused at their own room. */
    static boolean mayBeRefused(int ownerId, int visitorId) {
        return ownerId > 0 && visitorId > 0 && ownerId != visitorId;
    }

    /** The owner's block as stored, for an owner who is offline. A failed lookup lets the visitor in. */
    private boolean storedBlock(int ownerId, int visitorId) {
        try (Connection connection = this.dataSource.get().getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT 1 FROM users_blocked WHERE user_id = ? AND target_id = ? LIMIT 1")) {
            statement.setInt(1, ownerId);
            statement.setInt(2, visitorId);
            try (ResultSet set = statement.executeQuery()) {
                return set.next();
            }
        } catch (SQLException e) {
            LOGGER.error("Could not read the block list of room owner {}", ownerId, e);
            return false;
        }
    }
}
