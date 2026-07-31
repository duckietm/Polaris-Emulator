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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntUnaryOperator;

public class SoundboardManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(SoundboardManager.class);

    private final SoundboardCooldownGate cooldownGate = new SoundboardCooldownGate();
    private final IntUnaryOperator cooldownByRank;
    private volatile SoundSnapshot snapshot = SoundSnapshot.empty();

    public SoundboardManager() {
        this(rankId -> 60);
    }

    public SoundboardManager(PermissionsManager permissionsManager) {
        this(rankId -> loadCooldownFromPermissions(permissionsManager, rankId));
    }

    private SoundboardManager(IntUnaryOperator cooldownByRank) {
        this.cooldownByRank = cooldownByRank;
        long millis = System.currentTimeMillis();
        this.reload();
        LOGGER.info("Soundboard Manager -> Loaded! ({} MS, {} sounds)", System.currentTimeMillis() - millis, this.snapshot.ordered().size());
    }

    SoundboardManager(List<SoundboardSound> sounds, IntUnaryOperator cooldownByRank) {
        this.snapshot = SoundSnapshot.from(sounds);
        this.cooldownByRank = cooldownByRank;
    }

    public void reload() {
        List<SoundboardSound> loadedSounds = new ArrayList<>();
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT id, name, url, min_rank FROM soundboard_sounds WHERE enabled = 1 ORDER BY sort_order ASC, id ASC");
             ResultSet set = statement.executeQuery()) {
            while (set.next()) {
                loadedSounds.add(new SoundboardSound(set));
            }
            this.snapshot = SoundSnapshot.from(loadedSounds);
        } catch (SQLException e) {
            LOGGER.error("Failed to load soundboard sounds", e);
        }
    }

    public List<SoundboardSound> getSounds() {
        return this.snapshot.ordered();
    }

    public SoundboardSound getSound(int id) {
        return this.snapshot.byId().get(id);
    }

    public List<SoundboardSound> getSoundsForRank(int rankId) {
        return this.snapshot.ordered().stream()
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

    private record SoundSnapshot(List<SoundboardSound> ordered, Map<Integer, SoundboardSound> byId) {
        private static SoundSnapshot empty() {
            return new SoundSnapshot(List.of(), Map.of());
        }

        private static SoundSnapshot from(List<SoundboardSound> sounds) {
            List<SoundboardSound> ordered = List.copyOf(sounds);
            Map<Integer, SoundboardSound> byId = new LinkedHashMap<>();
            for (SoundboardSound sound : ordered) {
                byId.putIfAbsent(sound.id, sound);
            }
            return new SoundSnapshot(ordered, Map.copyOf(byId));
        }
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
