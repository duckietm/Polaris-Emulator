package com.eu.habbo.habbohotel.users.inventory;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.permissions.Rank;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboBadge;
import com.eu.habbo.threading.ThreadPooling;
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

public class BadgesComponent {
    private static final Logger LOGGER = LoggerFactory.getLogger(BadgesComponent.class);

    private final Set<HabboBadge> badges = new HashSet<>();

    public BadgesComponent(Habbo habbo) {
        this.badges.addAll(loadBadges(habbo));
    }

    private static Set<HabboBadge> loadBadges(Habbo habbo) {
        Set<HabboBadge> badgesList = new HashSet<>();
        Set<String> staffBadges =
                Emulator.getGameEnvironment().getPermissionsManager().getStaffBadges();
        try (Connection connection = connection();
                PreparedStatement statement =
                        connection.prepareStatement("SELECT * FROM users_badges WHERE user_id = ?")) {
            statement.setInt(1, habbo.getHabboInfo().getId());

            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    HabboBadge badge = new HabboBadge(set, habbo);

                    if (staffBadges.contains(badge.getCode())) {
                        boolean delete = true;

                        for (Rank rank : Emulator.getGameEnvironment()
                                .getPermissionsManager()
                                .getRanksByBadgeCode(badge.getCode())) {
                            if (rank.getId() == habbo.getHabboInfo().getRank().getId()) {
                                delete = false;
                                break;
                            }
                        }

                        if (delete) {
                            deleteBadge(habbo.getHabboInfo().getId(), badge.getCode());
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

    public static void resetSlots(Habbo habbo) {
        for (HabboBadge badge : habbo.getInventory().getBadgesComponent().getBadges()) {
            if (badge.getSlot() == 0) continue;

            badge.setSlot(0);
            badge.needsUpdate(true);
            threading().run(badge);
        }
    }

    public static ArrayList<HabboBadge> getBadgesOfflineHabbo(int userId) {
        ArrayList<HabboBadge> badgesList = new ArrayList<>();
        try (Connection connection = connection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT * FROM users_badges WHERE slot_id > 0 AND user_id = ? ORDER BY slot_id ASC")) {
            statement.setInt(1, userId);
            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    badgesList.add(new HabboBadge(set, null));
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
    public static void grantBadge(Collection<Habbo> habbos, String code, BiConsumer<Habbo, HabboBadge> granted) {
        if (!isValidBadgeCode(code) || habbos == null || habbos.isEmpty()) return;

        threading().runPersistence(() -> grantBadgeNow(habbos, code, granted));
    }

    static int grantBadgeNow(Collection<Habbo> habbos, String code, BiConsumer<Habbo, HabboBadge> granted) {
        int count = 0;

        try (Connection connection = connection()) {
            for (Habbo habbo : habbos) {
                if (habbo == null
                        || habbo.getInventory() == null
                        || habbo.getInventory().getBadgesComponent() == null
                        || habbo.getInventory().getBadgesComponent().hasBadge(code)) {
                    continue;
                }

                HabboBadge badge = new HabboBadge(0, code, 0, habbo);

                try {
                    badge.insert(connection);
                } catch (SQLException e) {
                    LOGGER.error(
                            "Could not give badge {} to user {}",
                            code,
                            habbo.getHabboInfo().getId(),
                            e);
                    continue;
                }

                habbo.getInventory().getBadgesComponent().addBadge(badge);
                count++;

                if (granted != null) granted.accept(habbo, badge);
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }

        return count;
    }

    public static HabboBadge createBadge(String code, Habbo habbo) {
        HabboBadge badge = new HabboBadge(0, code, 0, habbo);
        badge.run();
        habbo.getInventory().getBadgesComponent().addBadge(badge);
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

    public ArrayList<HabboBadge> getWearingBadges() {
        synchronized (this.badges) {
            ArrayList<HabboBadge> badgesList = new ArrayList<>();
            for (HabboBadge badge : this.badges) {
                if (badge.getSlot() == 0) continue;

                badgesList.add(badge);
            }

            badgesList.sort(new Comparator<HabboBadge>() {
                @Override
                public int compare(HabboBadge o1, HabboBadge o2) {
                    return o1.getSlot() - o2.getSlot();
                }
            });
            return badgesList;
        }
    }

    public Set<HabboBadge> getBadges() {
        return this.badges;
    }

    public ArrayList<HabboBadge> getBadgesSnapshot() {
        synchronized (this.badges) {
            return new ArrayList<>(this.badges);
        }
    }

    public boolean hasBadge(String badge) {
        return this.getBadge(badge) != null;
    }

    public HabboBadge getBadge(String badgeCode) {
        synchronized (this.badges) {
            for (HabboBadge badge : this.badges) {
                if (badge.getCode().equalsIgnoreCase(badgeCode)) return badge;
            }
            return null;
        }
    }

    public void addBadge(HabboBadge badge) {
        synchronized (this.badges) {
            this.badges.add(badge);
        }
    }

    public HabboBadge removeBadge(String badge) {
        synchronized (this.badges) {
            for (HabboBadge b : this.badges) {
                if (b.getCode().equalsIgnoreCase(badge)) {
                    this.badges.remove(b);
                    return b;
                }
            }
        }

        return null;
    }

    public void removeBadge(HabboBadge badge) {
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
