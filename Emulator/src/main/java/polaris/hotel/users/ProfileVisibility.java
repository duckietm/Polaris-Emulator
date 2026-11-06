package polaris.hotel.users;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.database.SqlQueries;
import polaris.hotel.permissions.Permission;

/**
 * "Hide my profile": the owner's friend count, groups, last online time and relationships are kept
 * from other users. The owner and moderators (support tool) still see the full profile.
 */
public final class ProfileVisibility {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProfileVisibility.class);

    /** Sent in place of a hidden count or time; the packet layout stays the same. */
    public static final int HIDDEN_VALUE = -1;

    private ProfileVisibility() {}

    public static boolean hidesDetails(boolean profileHidden, boolean ownProfile, boolean moderator) {
        return profileHidden && !ownProfile && !moderator;
    }

    public static boolean hidesDetailsFrom(User viewer, int ownerId, boolean profileHidden) {
        if (!profileHidden) return false;

        boolean ownProfile = viewer != null && viewer.getUserInfo().getId() == ownerId;
        boolean moderator = viewer != null && viewer.hasPermission(Permission.ACC_SUPPORTTOOL);

        return hidesDetails(true, ownProfile, moderator);
    }

    /** The owner's setting, from the online user or else from users_settings. */
    public static boolean isProfileHidden(User owner, int ownerId) {
        if (owner != null) return owner.getUserStats().hideProfile;

        try {
            return SqlQueries.queryOne(
                            "SELECT hide_profile FROM users_settings WHERE user_id = ? LIMIT 1",
                            set -> "1".equals(set.getString("hide_profile")),
                            ownerId)
                    .orElse(false);
        } catch (SqlQueries.DataAccessException e) {
            LOGGER.error("Caught SQL exception while reading the profile visibility of user {}", ownerId, e);
            return false;
        }
    }
}
