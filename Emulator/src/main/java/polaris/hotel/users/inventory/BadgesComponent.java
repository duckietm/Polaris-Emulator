package polaris.hotel.users.inventory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.Emulator;
import polaris.hotel.permissions.Rank;
import polaris.hotel.users.User;
import polaris.hotel.users.UserBadge;
import polaris.threading.ThreadPooling;

public class BadgesComponent {
    private static final Logger LOGGER = LoggerFactory.getLogger(BadgesComponent.class);

    private final Set<UserBadge> badges = new HashSet<>();

    public BadgesComponent(User user) {
        this.badges.addAll(loadBadges(user));
    }

    private static Set<UserBadge> loadBadges(User user) {
        Set<UserBadge> badgesList = new HashSet<>();
        Set<String> staffBadges =
                Emulator.getGameEnvironment().getPermissionsManager().getStaffBadges();
        try (Connection connection = connection();
                PreparedStatement statement =
                        connection.prepareStatement("SELECT * FROM users_badges WHERE user_id = ?")) {
            statement.setInt(1, user.getUserInfo().getId());

            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    UserBadge badge = new UserBadge(set, user);

                    if (staffBadges.contains(badge.getCode())) {
                        boolean delete = true;

                        for (Rank rank : Emulator.getGameEnvironment()
                                .getPermissionsManager()
                                .getRanksByBadgeCode(badge.getCode())) {
                            if (rank.getId() == user.getUserInfo().getRank().getId()) {
                                delete = false;
                                break;
                            }
                        }

                        if (delete) {
                            deleteBadge(user.getUserInfo().getId(), badge.getCode());
                            continue;
                        }
                    }

                    badgesList.add(badge);
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }

        return badgesList;
    }

    public static void resetSlots(User user) {
        for (UserBadge badge : user.getInventory().getBadgesComponent().getBadges()) {
            if (badge.getSlot() == 0) continue;

            badge.setSlot(0);
            badge.needsUpdate(true);
            threading().run(badge);
        }
    }

    public static ArrayList<UserBadge> getBadgesOfflineUser(int userId) {
        ArrayList<UserBadge> badgesList = new ArrayList<>();
        try (Connection connection = connection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT * FROM users_badges WHERE slot_id > 0 AND user_id = ? ORDER BY slot_id ASC")) {
            statement.setInt(1, userId);
            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    badgesList.add(new UserBadge(set, null));
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
        return badgesList;
    }

    // users_badges.badge_code is varchar(32). A longer or odd code failed every insert while the badge was still
    // added in memory and shown to the client: a ghost badge gone at the next login.
    private static final Pattern BADGE_CODE = Pattern.compile("[A-Za-z0-9_]{1,32}");

    public static boolean isValidBadgeCode(String code) {
        return code != null && BADGE_CODE.matcher(code).matches();
    }

    /**
     * Gives a badge to many users on the persistence pool, one connection for all, each badge handed to its user only
     * once its row exists. The badge commands used to insert one row per user online synchronously on the thread
     * handling the chat, and to show the badge even when the insert failed.
     */
    public static void grantBadge(Collection<User> users, String code, BiConsumer<User, UserBadge> granted) {
        if (!isValidBadgeCode(code) || users == null || users.isEmpty()) return;

        threading().runPersistence(() -> grantBadgeNow(users, code, granted));
    }

    static int grantBadgeNow(Collection<User> users, String code, BiConsumer<User, UserBadge> granted) {
        int count = 0;

        try (Connection connection = connection()) {
            for (User user : users) {
                if (user == null
                        || user.getInventory() == null
                        || user.getInventory().getBadgesComponent() == null
                        || user.getInventory().getBadgesComponent().hasBadge(code)) {
                    continue;
                }

                UserBadge badge = new UserBadge(0, code, 0, user);

                try {
                    badge.insert(connection);
                } catch (SQLException e) {
                    LOGGER.error(
                            "Could not give badge {} to user {}",
                            code,
                            user.getUserInfo().getId(),
                            e);
                    continue;
                }

                user.getInventory().getBadgesComponent().addBadge(badge);
                count++;

                if (granted != null) granted.accept(user, badge);
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }

        return count;
    }

    public static UserBadge createBadge(String code, User user) {
        UserBadge badge = new UserBadge(0, code, 0, user);
        badge.run();
        user.getInventory().getBadgesComponent().addBadge(badge);
        return badge;
    }

    /**
     * Deletes one badge code of a user. The code is compared with {@code =}: it used to go to a
     * {@code LIKE} unescaped, so a code of {@code %} deleted every badge an offline user had. The
     * column's collation keeps the match case-insensitive, as {@link #removeBadge(String)} is.
     */
    public static void deleteBadge(int userId, String badge) {
        try (Connection connection = connection();
                PreparedStatement statement = connection.prepareStatement(
                        "DELETE users_badges FROM users_badges WHERE user_id = ? AND badge_code = ?")) {
            statement.setInt(1, userId);
            statement.setString(2, badge);
            statement.execute();
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
    }

    public ArrayList<UserBadge> getWearingBadges() {
        synchronized (this.badges) {
            ArrayList<UserBadge> badgesList = new ArrayList<>();
            for (UserBadge badge : this.badges) {
                if (badge.getSlot() == 0) continue;

                badgesList.add(badge);
            }

            badgesList.sort(new Comparator<UserBadge>() {
                @Override
                public int compare(UserBadge o1, UserBadge o2) {
                    return o1.getSlot() - o2.getSlot();
                }
            });
            return badgesList;
        }
    }

    public Set<UserBadge> getBadges() {
        return this.badges;
    }

    public ArrayList<UserBadge> getBadgesSnapshot() {
        synchronized (this.badges) {
            return new ArrayList<>(this.badges);
        }
    }

    public boolean hasBadge(String badge) {
        return this.getBadge(badge) != null;
    }

    public UserBadge getBadge(String badgeCode) {
        synchronized (this.badges) {
            for (UserBadge badge : this.badges) {
                if (badge.getCode().equalsIgnoreCase(badgeCode)) return badge;
            }
            return null;
        }
    }

    public void addBadge(UserBadge badge) {
        synchronized (this.badges) {
            this.badges.add(badge);
        }
    }

    public UserBadge removeBadge(String badge) {
        synchronized (this.badges) {
            for (UserBadge b : this.badges) {
                if (b.getCode().equalsIgnoreCase(badge)) {
                    this.badges.remove(b);
                    return b;
                }
            }
        }

        return null;
    }

    public void removeBadge(UserBadge badge) {
        synchronized (this.badges) {
            this.badges.remove(badge);
        }
    }

    public void dispose() {
        synchronized (this.badges) {
            this.badges.clear();
        }
    }

    private static Connection connection() throws SQLException {
        return Emulator.getDatabase().getDataSource().getConnection();
    }

    private static ThreadPooling threading() {
        return Emulator.getThreading();
    }
}
