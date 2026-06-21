package com.eu.habbo.habbohotel.users.inventory;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboGender;
import it.unimi.dsi.fastutil.ints.IntCollection;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

public class WardrobeComponent {
    private static final Logger LOGGER = LoggerFactory.getLogger(WardrobeComponent.class);
    private final Map<Integer, WardrobeItem> looks;
    private final IntSet clothing;
    private final IntSet clothingSets;

    public WardrobeComponent(Habbo habbo) {
        this.looks = new HashMap<>();
        this.clothing = new IntOpenHashSet();
        this.clothingSets = new IntOpenHashSet();

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM users_wardrobe WHERE user_id = ?")) {
                statement.setInt(1, habbo.getHabboInfo().getId());
                try (ResultSet set = statement.executeQuery()) {
                    while (set.next()) {
                        this.looks.put(set.getInt("slot_id"), new WardrobeItem(set, habbo));
                    }
                }
            }

            try (PreparedStatement statement = connection.prepareStatement("SELECT users_clothing.*, catalog_clothing.setid FROM users_clothing LEFT JOIN catalog_clothing ON catalog_clothing.id = users_clothing.clothing_id WHERE users_clothing.user_id = ?")) {
                statement.setInt(1, habbo.getHabboInfo().getId());
                try (ResultSet set = statement.executeQuery()) {
                    while (set.next()) {
                        int value = set.getInt("clothing_id");
                        this.clothing.add(value);

                        for(String x : set.getString("setid").split(Pattern.quote(","))) {
                            try {
                                this.clothingSets.add(Integer.parseInt(x));
                            }
                            catch (Exception e) { }
                        }
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
    }

    public WardrobeItem createLook(Habbo habbo, int slotId, String look) {
        return new WardrobeItem(habbo.getHabboInfo().getGender(), look, slotId, habbo);
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

    public void dispose() {
        this.looks.values().stream().filter(item -> item.needsInsert || item.needsUpdate).forEach(item -> {
            Emulator.getThreading().run(item);
        });

        this.looks.clear();
    }

    public class WardrobeItem implements Runnable {
        private int slotId;
        private HabboGender gender;
        private Habbo habbo;
        private String look;
        private boolean needsInsert = false;
        private boolean needsUpdate = false;

        private WardrobeItem(ResultSet set, Habbo habbo) throws SQLException {
            this.gender = HabboGender.valueOf(set.getString("gender"));
            this.look = set.getString("look");
            this.slotId = set.getInt("slot_id");
            this.habbo = habbo;
        }

        private WardrobeItem(HabboGender gender, String look, int slotId, Habbo habbo) {
            this.gender = gender;
            this.look = look;
            this.slotId = slotId;
            this.habbo = habbo;
        }

        public HabboGender getGender() {
            return this.gender;
        }

        public void setGender(HabboGender gender) {
            this.gender = gender;
        }

        public Habbo getHabbo() {
            return this.habbo;
        }

        public void setHabbo(Habbo habbo) {
            this.habbo = habbo;
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
                    try (PreparedStatement statement = connection.prepareStatement("INSERT INTO users_wardrobe (slot_id, look, user_id, gender) VALUES (?, ?, ?, ?)")) {
                        statement.setInt(1, this.slotId);
                        statement.setString(2, this.look);
                        statement.setInt(3, this.habbo.getHabboInfo().getId());
                        statement.setString(4, this.gender.name());
                        statement.execute();
                    }
                }

                if (this.needsUpdate) {
                    this.needsUpdate = false;
                    try (PreparedStatement statement = connection.prepareStatement("UPDATE users_wardrobe SET look = ? WHERE slot_id = ? AND user_id = ?")) {
                        statement.setString(1, this.look);
                        statement.setInt(2, this.slotId);
                        statement.setInt(3, this.habbo.getHabboInfo().getId());
                        statement.execute();
                    }
                }
            } catch (SQLException e) {
                LOGGER.error("Caught SQL exception", e);
            }
        }
    }
}
