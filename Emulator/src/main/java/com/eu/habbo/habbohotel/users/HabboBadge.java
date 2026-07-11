package com.eu.habbo.habbohotel.users;

import com.eu.habbo.Emulator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;

public class HabboBadge implements Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger(HabboBadge.class);
    private int id;
    private String code;
    private int slot;
    private Habbo habbo;
    private boolean needsUpdate;
    private boolean needsInsert;

    public HabboBadge(ResultSet set, Habbo habbo) throws SQLException {
        this.id = set.getInt("id");
        this.code = set.getString("badge_code");
        this.slot = set.getInt("slot_id");
        this.habbo = habbo;
        this.needsUpdate = false;
        this.needsInsert = false;
    }

    public HabboBadge(int id, String code, int slot, Habbo habbo) {
        this.id = id;
        this.code = code;
        this.slot = slot;
        this.habbo = habbo;
        this.needsUpdate = false;
        this.needsInsert = true;
    }

    public int getId() {
        return this.id;
    }

    public String getCode() {
        return this.code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public int getSlot() {
        return this.slot;
    }

    public void setSlot(int slot) {
        this.slot = slot;
    }

    @Override
    public void run() {
        try {
            if (this.needsInsert) {
                try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
                    this.insert(connection);
                }
            } else if (this.needsUpdate) {
                try (Connection connection = Emulator.getDatabase().getDataSource().getConnection(); PreparedStatement statement = connection.prepareStatement("UPDATE users_badges SET slot_id = ?, badge_code = ? WHERE id = ? AND user_id = ?")) {
                    statement.setInt(1, this.slot);
                    statement.setString(2, this.code);
                    statement.setInt(3, this.id);
                    statement.setInt(4, this.habbo.getHabboInfo().getId());
                    statement.execute();
                }
                this.needsUpdate = false;
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
    }

    public void insert(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO users_badges (user_id, slot_id, badge_code) VALUES (?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            statement.setInt(1, this.habbo.getHabboInfo().getId());
            statement.setInt(2, this.slot);
            statement.setString(3, this.code);
            statement.executeUpdate();
            try (ResultSet set = statement.getGeneratedKeys()) {
                if (!set.next()) throw new SQLException("Unable to create user badge");
                this.id = set.getInt(1);
            }
        }
        this.needsInsert = false;
    }

    public void needsUpdate(boolean needsUpdate) {
        this.needsUpdate = needsUpdate;
    }

    public void needsInsert(boolean needsInsert) {
        this.needsInsert = needsInsert;
    }
}
