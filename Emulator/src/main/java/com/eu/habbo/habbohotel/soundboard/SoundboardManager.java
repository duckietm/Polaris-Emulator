package com.eu.habbo.habbohotel.soundboard;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.permissions.PermissionsManager;
import com.eu.habbo.habbohotel.permissions.Rank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntUnaryOperator;

public class SoundboardManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(SoundboardManager.class);

    private final SoundboardCooldownGate cooldownGate = new SoundboardCooldownGate();
    private final IntUnaryOperator cooldownByRank;
    private volatile List<SoundboardSound> sounds = List.of();

    public SoundboardManager() {
        this(rankId -> 60);
    }

    public SoundboardManager(PermissionsManager permissionsManager) {
        this(rankId -> loadCooldownFromPermissions(permissionsManager, rankId));
    }

    private SoundboardManager(IntUnaryOperator cooldownByRank) {
        this.cooldownByRank = cooldownByRank;
        long millis = System.currentTimeMillis();
        this.bootstrap();
        this.reload();
        LOGGER.info("Soundboard Manager -> Loaded! ({} MS, {} sounds)", System.currentTimeMillis() - millis, this.sounds.size());
    }

    SoundboardManager(List<SoundboardSound> sounds, IntUnaryOperator cooldownByRank) {
        this.sounds = List.copyOf(sounds);
        this.cooldownByRank = cooldownByRank;
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
                    "`sort_order` INT(11) NOT NULL DEFAULT 0, `min_rank` INT NOT NULL DEFAULT 1, " +
                    "PRIMARY KEY (`id`)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
            statement.execute("ALTER TABLE `soundboard_sounds` ADD COLUMN IF NOT EXISTS `min_rank` INT NOT NULL DEFAULT 1");
        } catch (SQLException e) {
            LOGGER.error("Failed to bootstrap soundboard schema", e);
        }
    }

    public void reload() {
        List<SoundboardSound> loadedSounds = new ArrayList<>();
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT id, name, url, min_rank FROM soundboard_sounds WHERE enabled = 1 ORDER BY sort_order ASC, id ASC");
             ResultSet set = statement.executeQuery()) {
            while (set.next()) {
                loadedSounds.add(new SoundboardSound(set));
            }
            this.sounds = List.copyOf(loadedSounds);
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

    public List<SoundboardSound> getSoundsForRank(int rankId) {
        return this.sounds.stream()
                .filter(sound -> sound.isAvailableTo(rankId))
                .toList();
    }

    public int getCooldownSecondsForRank(int rankId) {
        int cooldown;
        try {
            cooldown = this.cooldownByRank.applyAsInt(rankId);
        } catch (RuntimeException exception) {
            LOGGER.warn("Unable to resolve soundboard cooldown for rank {}", rankId, exception);
            return 60;
        }
        return cooldown < 0 ? 60 : cooldown;
    }

    public PlayDecision tryPlay(int userId, int rankId, int soundId, long nowMillis) {
        SoundboardSound sound = this.getSound(soundId);
        if (sound == null || !sound.isAvailableTo(rankId)) {
            return new PlayDecision(false, null, DenialReason.NOT_AVAILABLE, 0);
        }

        SoundboardCooldownGate.Decision cooldown = this.cooldownGate.tryAcquire(
                userId,
                nowMillis,
                this.getCooldownSecondsForRank(rankId));
        if (!cooldown.allowed()) {
            return new PlayDecision(false, sound, DenialReason.COOLDOWN, cooldown.remainingSeconds());
        }

        return new PlayDecision(true, sound, DenialReason.NONE, 0);
    }

    private static int loadCooldownFromPermissions(PermissionsManager permissionsManager, int rankId) {
        if (permissionsManager == null) {
            return -1;
        }

        Rank rank = permissionsManager.getRank(rankId);
        return rank == null ? -1 : rank.getSoundboardCooldownSeconds();
    }

    public enum DenialReason {
        NONE,
        NOT_AVAILABLE,
        COOLDOWN
    }

    public record PlayDecision(
            boolean allowed,
            SoundboardSound sound,
            DenialReason denialReason,
            int remainingSeconds) {
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
