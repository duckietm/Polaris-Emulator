package com.eu.habbo.habbohotel.soundboard;

import com.eu.habbo.Emulator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class SoundboardManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(SoundboardManager.class);

    private final List<SoundboardSound> sounds = new ArrayList<>();

    public SoundboardManager() {
        long millis = System.currentTimeMillis();
        this.bootstrap();
        this.reload();
        LOGGER.info("Soundboard Manager -> Loaded! ({} MS, {} sounds)", System.currentTimeMillis() - millis, this.sounds.size());
    }

    // Self-bootstrap: room flag column + sounds table, so the feature works even
    // before the manual migration is applied.
    private void bootstrap() {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE `rooms` ADD COLUMN IF NOT EXISTS `soundboard_enabled` TINYINT(1) NOT NULL DEFAULT 0");
            statement.execute("CREATE TABLE IF NOT EXISTS `soundboard_sounds` (" +
                    "`id` INT(11) NOT NULL AUTO_INCREMENT, `name` VARCHAR(64) NOT NULL DEFAULT '', " +
                    "`url` VARCHAR(255) NOT NULL DEFAULT '', `enabled` TINYINT(1) NOT NULL DEFAULT 1, " +
                    "`sort_order` INT(11) NOT NULL DEFAULT 0, PRIMARY KEY (`id`)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        } catch (SQLException e) {
            LOGGER.error("Failed to bootstrap soundboard schema", e);
        }
    }

    public void reload() {
        this.sounds.clear();
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT id, name, url FROM soundboard_sounds WHERE enabled = 1 ORDER BY sort_order ASC, id ASC");
             ResultSet set = statement.executeQuery()) {
            while (set.next()) {
                this.sounds.add(new SoundboardSound(set));
            }
        } catch (SQLException e) {
            LOGGER.error("Failed to load soundboard sounds", e);
        }
    }

    public List<SoundboardSound> getSounds() {
        return this.sounds;
    }

    public SoundboardSound getSound(int id) {
        for (SoundboardSound sound : this.sounds) {
            if (sound.id == id) return sound;
        }
        return null;
    }

    // Owner toggle — persists the room flag with a dedicated UPDATE (kept out of
    // the big room-settings save to avoid touching that statement).
    public void setRoomEnabled(int roomId, boolean enabled) {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement("UPDATE rooms SET soundboard_enabled = ? WHERE id = ? LIMIT 1")) {
            statement.setString(1, enabled ? "1" : "0");
            statement.setInt(2, roomId);
            statement.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("Failed to set soundboard_enabled for room {}", roomId, e);
        }
    }
}
