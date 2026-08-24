package com.eu.habbo.habbohotel.soundboard;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.permissions.PermissionsManager;
import com.eu.habbo.habbohotel.permissions.Rank;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntPredicate;
import java.util.function.IntUnaryOperator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SoundboardManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(SoundboardManager.class);
    private static final int MAX_REORDER_SIZE = SoundboardCatalogRepository.MAX_CATALOG_SIZE;

    private final SoundboardCooldownGate cooldownGate = new SoundboardCooldownGate();
    private final IntUnaryOperator cooldownByRank;
    private final IntPredicate rankExists;
    private final SoundboardCatalogRepository repository;
    private volatile SoundSnapshot snapshot = SoundSnapshot.empty();

    public SoundboardManager() {
        this(
                rankId -> 60,
                rankId -> rankId > 0,
                new SoundboardCatalogRepository(Emulator.getDatabase().getDataSource()));
    }

    public SoundboardManager(PermissionsManager permissionsManager) {
        this(
                rankId -> loadCooldownFromPermissions(permissionsManager, rankId),
                rankId -> permissionsManager != null && permissionsManager.getRank(rankId) != null,
                new SoundboardCatalogRepository(Emulator.getDatabase().getDataSource()));
    }

    private SoundboardManager(
            IntUnaryOperator cooldownByRank, IntPredicate rankExists, SoundboardCatalogRepository repository) {
        this.cooldownByRank = cooldownByRank;
        this.rankExists = rankExists;
        this.repository = repository;
        long millis = System.currentTimeMillis();
        this.reload();
        LOGGER.info(
                "Soundboard Manager -> Loaded! ({} MS, {} sounds)",
                System.currentTimeMillis() - millis,
                this.snapshot.playableOrdered().size());
    }

    SoundboardManager(List<SoundboardSound> sounds, IntUnaryOperator cooldownByRank) {
        this(sounds, cooldownByRank, rankId -> rankId > 0, null);
    }

    SoundboardManager(
            List<SoundboardSound> sounds,
            IntUnaryOperator cooldownByRank,
            IntPredicate rankExists,
            SoundboardCatalogRepository repository) {
        this.snapshot = SoundSnapshot.from(sounds);
        this.cooldownByRank = cooldownByRank;
        this.rankExists = rankExists;
        this.repository = repository;
    }

    public void reload() {
        if (this.repository == null) {
            return;
        }

        try {
            this.snapshot = SoundSnapshot.from(this.repository.loadAll());
        } catch (SQLException exception) {
            LOGGER.error("Failed to load Soundboard catalog", exception);
        }
    }

    public List<SoundboardSound> getSounds() {
        return this.snapshot.playableOrdered();
    }

    public List<SoundboardSound> getCatalog() {
        return this.snapshot.catalogOrdered();
    }

    public SoundboardSound getSound(int id) {
        return this.snapshot.playableById().get(id);
    }

    public List<SoundboardSound> getSoundsForRank(int rankId) {
        return this.snapshot.playableOrdered().stream()
                .filter(sound -> sound.isAvailableTo(rankId))
                .toList();
    }

    public int getCooldownSecondsForRank(int rankId) {
        int cooldown;
        try {
            cooldown = this.cooldownByRank.applyAsInt(rankId);
        } catch (RuntimeException exception) {
            LOGGER.warn("Unable to resolve Soundboard cooldown for rank {}", rankId, exception);
            return 60;
        }
        return cooldown < 0 ? 60 : cooldown;
    }

    public PlayDecision tryPlay(int userId, int rankId, int soundId, long nowMillis) {
        SoundboardSound sound = this.getSound(soundId);
        if (sound == null || !sound.isAvailableTo(rankId)) {
            return new PlayDecision(false, null, DenialReason.NOT_AVAILABLE, 0);
        }

        SoundboardCooldownGate.Decision cooldown =
                this.cooldownGate.tryAcquire(userId, nowMillis, this.getCooldownSecondsForRank(rankId));
        if (!cooldown.allowed()) {
            return new PlayDecision(false, sound, DenialReason.COOLDOWN, cooldown.remainingSeconds());
        }

        return new PlayDecision(true, sound, DenialReason.NONE, 0);
    }

    public SoundboardCatalogResult upsert(int staffUserId, SoundboardCatalogCommand command) {
        if (command == null) {
            return SoundboardCatalogResult.failure(SoundboardCatalogResult.Code.INVALID_NAME);
        }

        String name = command.name() == null ? "" : command.name().trim();
        if (name.isEmpty() || name.length() > 64) {
            return SoundboardCatalogResult.failure(SoundboardCatalogResult.Code.INVALID_NAME);
        }

        String classname =
                command.classname() == null ? "" : command.classname().trim().toLowerCase();
        String url = command.url() == null ? "" : command.url().trim();

        // A pad resolves through the asset manifest (classname) or through an
        // explicit URL for clips hosted outside the asset tree. One or the
        // other, otherwise the client has nothing to play.
        if (!SoundboardClassnamePolicy.isAddressable(classname, url)) {
            return SoundboardCatalogResult.failure(SoundboardCatalogResult.Code.INVALID_URL);
        }

        if (command.minRank() < 1 || !this.rankExists.test(command.minRank())) {
            return SoundboardCatalogResult.failure(SoundboardCatalogResult.Code.INVALID_RANK);
        }

        if (command.id() < 0) {
            return SoundboardCatalogResult.failure(SoundboardCatalogResult.Code.NOT_FOUND);
        }

        if (this.repository == null) {
            return SoundboardCatalogResult.failure(SoundboardCatalogResult.Code.PERSISTENCE_FAILURE);
        }

        SoundboardCatalogResult result = this.repository.upsert(
                staffUserId,
                new SoundboardCatalogCommand(command.id(), name, classname, url, command.minRank(), command.enabled()));
        if (result.successful()) {
            this.reload();
        }
        return result;
    }

    public SoundboardCatalogResult reorder(int staffUserId, List<Integer> orderedIds) {
        if (orderedIds == null || orderedIds.size() > MAX_REORDER_SIZE) {
            return SoundboardCatalogResult.failure(SoundboardCatalogResult.Code.INVALID_ORDER);
        }

        Set<Integer> submitted = new LinkedHashSet<>(orderedIds);
        Set<Integer> expected = this.snapshot.catalogOrdered().stream()
                .map(sound -> sound.id)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (submitted.size() != orderedIds.size() || !submitted.equals(expected)) {
            return SoundboardCatalogResult.failure(SoundboardCatalogResult.Code.INVALID_ORDER);
        }

        if (this.repository == null) {
            return SoundboardCatalogResult.failure(SoundboardCatalogResult.Code.PERSISTENCE_FAILURE);
        }

        SoundboardCatalogResult result = this.repository.reorder(staffUserId, List.copyOf(orderedIds));
        if (result.successful()) {
            this.reload();
        }
        return result;
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
            boolean allowed, SoundboardSound sound, DenialReason denialReason, int remainingSeconds) {}

    private record SoundSnapshot(
            List<SoundboardSound> catalogOrdered,
            List<SoundboardSound> playableOrdered,
            Map<Integer, SoundboardSound> playableById) {
        private static SoundSnapshot empty() {
            return new SoundSnapshot(List.of(), List.of(), Map.of());
        }

        private static SoundSnapshot from(List<SoundboardSound> sounds) {
            List<SoundboardSound> catalog = List.copyOf(sounds);
            List<SoundboardSound> playable =
                    catalog.stream().filter(sound -> sound.enabled).toList();
            Map<Integer, SoundboardSound> byId = new LinkedHashMap<>();
            for (SoundboardSound sound : playable) {
                byId.putIfAbsent(sound.id, sound);
            }
            return new SoundSnapshot(catalog, playable, Map.copyOf(byId));
        }
    }

    public void setRoomEnabled(int roomId, boolean enabled) {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement =
                        connection.prepareStatement("UPDATE rooms SET soundboard_enabled = ? WHERE id = ? LIMIT 1")) {
            statement.setString(1, enabled ? "1" : "0");
            statement.setInt(2, roomId);
            statement.executeUpdate();
        } catch (SQLException exception) {
            LOGGER.error("Failed to set soundboard_enabled for room {}", roomId, exception);
        }
    }
}
