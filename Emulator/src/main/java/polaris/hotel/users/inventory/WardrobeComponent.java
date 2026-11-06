package polaris.hotel.users.inventory;

import it.unimi.dsi.fastutil.ints.IntCollection;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.Emulator;
import polaris.hotel.users.User;
import polaris.hotel.users.UserGender;

public class WardrobeComponent {
    private static final Logger LOGGER = LoggerFactory.getLogger(WardrobeComponent.class);
    private final Map<Integer, WardrobeItem> looks;
    private final IntSet clothing;
    private final IntSet clothingSets;
    private final IntSet collectibleSets = new IntOpenHashSet();

    public WardrobeComponent(User user) {
        this.looks = new HashMap<>();
        this.clothing = new IntOpenHashSet();
        this.clothingSets = new IntOpenHashSet();

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            try (PreparedStatement statement =
                    connection.prepareStatement("SELECT * FROM users_wardrobe WHERE user_id = ?")) {
                statement.setInt(1, user.getUserInfo().getId());
                try (ResultSet set = statement.executeQuery()) {
                    while (set.next()) {
                        this.looks.put(set.getInt("slot_id"), new WardrobeItem(set, user));
                    }
                }
            }

            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT users_clothing.*, catalog_clothing.setid FROM users_clothing LEFT JOIN catalog_clothing ON catalog_clothing.id = users_clothing.clothing_id WHERE users_clothing.user_id = ?")) {
                statement.setInt(1, user.getUserInfo().getId());
                try (ResultSet set = statement.executeQuery()) {
                    while (set.next()) {
                        int value = set.getInt("clothing_id");
                        this.clothing.add(value);

                        for (String x : set.getString("setid").split(Pattern.quote(","))) {
                            try {
                                this.clothingSets.add(Integer.parseInt(x));
                            } catch (Exception e) {
                            }
                        }
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
    }

    public WardrobeItem createLook(User user, int slotId, String look) {
        return new WardrobeItem(user.getUserInfo().getGender(), look, slotId, user);
    }

    public Map<Integer, WardrobeItem> getLooks() {
        return this.looks;
    }

    public IntCollection getClothing() {
        return this.clothing;
    }

    public IntCollection getClothingSets() {
        return this.clothingSets;
    }

    /** Figure sets the user may wear while holding a clothing collectible (no clothing furni redeemed). */
    public IntCollection getCollectibleSets() {
        return this.collectibleSets;
    }

    public void setCollectibleSets(Collection<Integer> setIds) {
        this.collectibleSets.clear();
        this.collectibleSets.addAll(setIds);
    }

    /** Every figure set the user may wear: redeemed clothing plus clothing collectibles. */
    public IntCollection getWearableSets() {
        if (this.collectibleSets.isEmpty()) return this.clothingSets;
        IntSet sets = new IntOpenHashSet(this.clothingSets);
        sets.addAll(this.collectibleSets);
        return sets;
    }

    public void dispose() {
        this.looks.values().stream()
                .filter(item -> item.needsInsert || item.needsUpdate)
                .forEach(item -> {
                    Emulator.getThreading().run(item);
                });

        this.looks.clear();
    }

    public class WardrobeItem implements Runnable {
        private int slotId;
        private UserGender gender;
        private User user;
        private String look;
        private boolean needsInsert = false;
        private boolean needsUpdate = false;

        private WardrobeItem(ResultSet set, User user) throws SQLException {
            this.gender = UserGender.valueOf(set.getString("gender"));
            this.look = set.getString("look");
            this.slotId = set.getInt("slot_id");
            this.user = user;
        }

        private WardrobeItem(UserGender gender, String look, int slotId, User user) {
            this.gender = gender;
            this.look = look;
            this.slotId = slotId;
            this.user = user;
        }

        public UserGender getGender() {
            return this.gender;
        }

        public void setGender(UserGender gender) {
            this.gender = gender;
        }

        public User getUser() {
            return this.user;
        }

        public void setUser(User user) {
            this.user = user;
        }

        public String getLook() {
            return this.look;
        }

        public void setLook(String look) {
            this.look = look;
        }

        public void setNeedsInsert(boolean needsInsert) {
            this.needsInsert = needsInsert;
        }

        public void setNeedsUpdate(boolean needsUpdate) {
            this.needsUpdate = needsUpdate;
        }

        public int getSlotId() {
            return this.slotId;
        }

        @Override
        public void run() {
            try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
                if (this.needsInsert) {
                    this.needsInsert = false;
                    this.needsUpdate = false;
                    try (PreparedStatement statement = connection.prepareStatement(
                            "INSERT INTO users_wardrobe (slot_id, look, user_id, gender) VALUES (?, ?, ?, ?)")) {
                        statement.setInt(1, this.slotId);
                        statement.setString(2, this.look);
                        statement.setInt(3, this.user.getUserInfo().getId());
                        statement.setString(4, this.gender.name());
                        statement.execute();
                    }
                }

                if (this.needsUpdate) {
                    this.needsUpdate = false;
                    try (PreparedStatement statement = connection.prepareStatement(
                            "UPDATE users_wardrobe SET look = ? WHERE slot_id = ? AND user_id = ?")) {
                        statement.setString(1, this.look);
                        statement.setInt(2, this.slotId);
                        statement.setInt(3, this.user.getUserInfo().getId());
                        statement.execute();
                    }
                }
            } catch (SQLException e) {
                LOGGER.error("Caught SQL exception", e);
            }
        }
    }
}
