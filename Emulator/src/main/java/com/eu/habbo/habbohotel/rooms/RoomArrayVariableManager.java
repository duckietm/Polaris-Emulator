package com.eu.habbo.habbohotel.rooms;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.wired.arrays.WiredArrayDefinition;
import com.eu.habbo.habbohotel.wired.arrays.WiredArrayMutationResult;
import com.eu.habbo.habbohotel.wired.arrays.WiredArrayNumericOperation;
import com.eu.habbo.habbohotel.wired.arrays.WiredArrayRuntimeMetrics;
import com.eu.habbo.habbohotel.wired.arrays.WiredArraySettings;
import com.eu.habbo.habbohotel.wired.arrays.WiredArrayStructuralOperation;
import com.eu.habbo.habbohotel.wired.arrays.WiredArrayValue;
import com.eu.habbo.habbohotel.wired.arrays.WiredArrayVariableDefinition;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Room-owned array store with optimistic publication and transactional permanent replacement. */
public final class RoomArrayVariableManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(RoomArrayVariableManager.class);
    private static final int MAX_MUTATION_ATTEMPTS = 3;

    private final Room room;
    private final RoomArrayVariableRepository repository;
    private final IntSupplier currentTimestamp;
    private final ConcurrentHashMap<Key, State> values = new ConcurrentHashMap<>();

    RoomArrayVariableManager(Room room, RoomDependencies dependencies) {
        this(room, new RoomArrayVariableRepository(dependencies.database()), dependencies.unixTime());
    }

    RoomArrayVariableManager(Room room, RoomArrayVariableRepository repository, IntSupplier currentTimestamp) {
        this.room = room;
        this.repository = repository;
        this.currentTimestamp = currentTimestamp;
    }

    public WiredArrayValue getValue(WiredArrayVariableDefinition definition, int ownerId) {
        if (!this.isValidOwner(definition, ownerId)) return null;
        Key key = this.key(definition, ownerId);
        State state = this.getOrLoad(key, definition);
        return state == null || !state.exists() ? null : state.value().copy();
    }

    public boolean hasValue(WiredArrayVariableDefinition definition, int ownerId) {
        if (!this.isValidOwner(definition, ownerId)) return false;
        State state = this.getOrLoad(this.key(definition, ownerId), definition);
        return state != null && state.exists();
    }

    public MutationOutcome give(WiredArrayVariableDefinition definition, int ownerId, boolean overrideExisting) {
        boolean permitted = this.isValidOwner(definition, ownerId)
                && definition.isArrayWritable()
                && definition.isArraySourceValid();
        WiredArrayRuntimeMetrics.recordGuard(1, 0L, 1L, permitted);
        if (!permitted) {
            return new MutationOutcome(WiredArrayMutationResult.MISSING_OWNER, null);
        }
        Key key = this.key(definition, ownerId);
        for (int attempt = 0; attempt < MAX_MUTATION_ATTEMPTS; attempt++) {
            State current = this.getOrLoad(key, definition);
            if (current == null) return new MutationOutcome(WiredArrayMutationResult.PERSISTENCE_FAILED, null);
            if (current.exists() && (!overrideExisting || current.value().isEmpty())) {
                return new MutationOutcome(
                        WiredArrayMutationResult.NO_CHANGE, current.value().copy());
            }
            WiredArrayValue candidate = WiredArrayValue.empty(
                    definition.getArrayDefinition(), WiredArraySettings.maxPopulatedCellsPerOwner());
            PublishResult published = this.publish(key, definition, current, candidate);
            if (published == PublishResult.SUCCESS) {
                return new MutationOutcome(WiredArrayMutationResult.SUCCESS, candidate.copy());
            }
            if (published == PublishResult.FAILURE) {
                return new MutationOutcome(WiredArrayMutationResult.PERSISTENCE_FAILED, null);
            }
        }
        return new MutationOutcome(WiredArrayMutationResult.PERSISTENCE_FAILED, null);
    }

    public boolean remove(WiredArrayVariableDefinition definition, int ownerId) {
        boolean permitted = this.isValidOwner(definition, ownerId)
                && definition.isArrayWritable()
                && definition.isArraySourceValid();
        WiredArrayRuntimeMetrics.recordGuard(1, 0L, 0L, permitted);
        if (!permitted) return false;
        Key key = this.key(definition, ownerId);
        for (int attempt = 0; attempt < MAX_MUTATION_ATTEMPTS; attempt++) {
            State current = this.getOrLoad(key, definition);
            if (current == null || !current.exists()) return false;
            if (!definition.isArrayPermanent()) {
                if (this.values.replace(key, current, State.absent(current.version() + 1L))) return true;
                continue;
            }
            long startedAt = System.nanoTime();
            try {
                if (!this.repository.delete(key, current.version())) {
                    WiredArrayRuntimeMetrics.recordPersistence(
                            1, current.value().getOccupiedCount(), 0, System.nanoTime() - startedAt, true);
                    this.values.remove(key, current);
                    continue;
                }
                WiredArrayRuntimeMetrics.recordPersistence(
                        1, current.value().getOccupiedCount(), 0, System.nanoTime() - startedAt, true);
                this.values.remove(key, current);
                WiredArrayRuntimeMetrics.recordCacheEviction(1);
                if (definition.isArrayShared()) this.invalidateOtherCaches(key);
                return true;
            } catch (SQLException | RuntimeException exception) {
                WiredArrayRuntimeMetrics.recordPersistence(
                        1, current.value().getOccupiedCount(), 0, System.nanoTime() - startedAt, false);
                LOGGER.error(
                        "Failed to remove wired array {} for owner {} in room {}",
                        definition.getId(),
                        ownerId,
                        this.room.getId(),
                        exception);
                return false;
            }
        }
        return false;
    }

    public MutationOutcome mutate(
            WiredArrayVariableDefinition definition,
            int ownerId,
            WiredArrayStructuralOperation operation,
            int firstIndex,
            int secondIndex,
            Map<Integer, Long> entryValues) {
        boolean permitted = this.isValidOwner(definition, ownerId)
                && definition.isArrayWritable()
                && definition.isArraySourceValid();
        WiredArrayRuntimeMetrics.recordGuard(1, 0L, 0L, permitted);
        if (!permitted) {
            return new MutationOutcome(WiredArrayMutationResult.INVALID_INDEX, null);
        }
        Key key = this.key(definition, ownerId);

        for (int attempt = 0; attempt < MAX_MUTATION_ATTEMPTS; attempt++) {
            State current = this.getOrLoad(key, definition);
            if (current == null) return new MutationOutcome(WiredArrayMutationResult.PERSISTENCE_FAILED, null);
            if (!current.exists()) return new MutationOutcome(WiredArrayMutationResult.MISSING_OWNER, null);
            WiredArrayValue candidate = current.value().copy();
            WiredArrayMutationResult result = candidate.apply(operation, firstIndex, secondIndex, entryValues);
            if (result != WiredArrayMutationResult.SUCCESS)
                return new MutationOutcome(result, current.value().copy());

            PublishResult published = this.publish(key, definition, current, candidate);
            if (published == PublishResult.SUCCESS) {
                return new MutationOutcome(WiredArrayMutationResult.SUCCESS, candidate.copy());
            }
            if (published == PublishResult.FAILURE)
                return new MutationOutcome(
                        WiredArrayMutationResult.PERSISTENCE_FAILED,
                        current.value().copy());
        }
        return new MutationOutcome(WiredArrayMutationResult.PERSISTENCE_FAILED, null);
    }

    public FieldMutationOutcome mutateField(
            WiredArrayVariableDefinition definition,
            int ownerId,
            int index,
            int fieldId,
            WiredArrayNumericOperation operation,
            long reference) {
        boolean permitted = this.isValidOwner(definition, ownerId)
                && definition.isArrayWritable()
                && definition.isArraySourceValid();
        WiredArrayRuntimeMetrics.recordGuard(1, 0L, 0L, permitted);
        if (!permitted) {
            return FieldMutationOutcome.failed(WiredArrayMutationResult.MISSING_OWNER);
        }
        Key key = this.key(definition, ownerId);
        for (int attempt = 0; attempt < MAX_MUTATION_ATTEMPTS; attempt++) {
            State current = this.getOrLoad(key, definition);
            if (current == null) return FieldMutationOutcome.failed(WiredArrayMutationResult.PERSISTENCE_FAILED);
            if (!current.exists()) return FieldMutationOutcome.failed(WiredArrayMutationResult.MISSING_OWNER);
            WiredArrayValue candidate = current.value().copy();
            WiredArrayValue.FieldMutation mutation = candidate.mutateField(index, fieldId, operation, reference);
            if (!mutation.changed()) {
                return new FieldMutationOutcome(
                        mutation.result(),
                        current.value().copy(),
                        mutation.previousValue(),
                        mutation.currentValue(),
                        false);
            }
            PublishResult published = this.publish(key, definition, current, candidate);
            if (published == PublishResult.SUCCESS) {
                return new FieldMutationOutcome(
                        WiredArrayMutationResult.SUCCESS,
                        candidate.copy(),
                        mutation.previousValue(),
                        mutation.currentValue(),
                        mutation.created());
            }
            if (published == PublishResult.FAILURE)
                return FieldMutationOutcome.failed(WiredArrayMutationResult.PERSISTENCE_FAILED);
        }
        return FieldMutationOutcome.failed(WiredArrayMutationResult.PERSISTENCE_FAILED);
    }

    public void validateDefinitionChange(
            WiredArrayVariableDefinition currentDefinition, WiredArrayDefinition replacement, boolean nextPermanent) {
        if (currentDefinition == null) return;
        WiredArrayDefinition current = currentDefinition.getArrayDefinition();
        if (current == null && replacement == null) return;

        if (current == null && replacement != null && this.hasScalarValues(currentDefinition)) {
            throw new IllegalArgumentException(
                    "Remove existing scalar assignments before converting this variable into an array.");
        }

        boolean destructive = current == null
                || replacement == null
                || !current.isShapeCompatible(replacement)
                || replacement.getMaxEntries() < current.getMaxEntries()
                || current.removesFieldsComparedWith(replacement);
        if (destructive && this.hasValues(currentDefinition.getId())) {
            throw new IllegalArgumentException(
                    "Clear every stored array value before changing its shape, fields, or maximum size.");
        }
        if (current != null
                && currentDefinition.isArrayPermanent() != nextPermanent
                && this.hasValues(currentDefinition.getId())) {
            throw new IllegalArgumentException(
                    "Clear every stored array value before changing its persistence setting.");
        }
    }

    public void handleDefinitionUpdated(WiredArrayVariableDefinition definition) {
        if (definition == null) return;
        if (!definition.isArray()) {
            this.values.keySet().removeIf(key -> key.definitionItemId() == definition.getId());
        } else {
            this.values.replaceAll((key, state) -> {
                if (key.definitionItemId() != definition.getId() || !state.exists()) return state;
                WiredArrayValue current = state.value();
                if (current.isEmpty()
                        && (!current.getDefinition().isShapeCompatible(definition.getArrayDefinition())
                                || definition.getArrayDefinition().getMaxEntries()
                                        < current.getDefinition().getMaxEntries())) {
                    return new State(
                            state.version(),
                            true,
                            WiredArrayValue.empty(
                                    definition.getArrayDefinition(), WiredArraySettings.maxPopulatedCellsPerOwner()));
                }
                return new State(state.version(), true, current.redefined(definition.getArrayDefinition()));
            });
        }
        if (!definition.isArray() || !definition.isArrayPermanent()) {
            try {
                this.repository.deleteDefinition(this.room.getId(), definition.getId());
            } catch (SQLException exception) {
                LOGGER.error(
                        "Failed to remove obsolete persisted wired array {} in room {}",
                        definition.getId(),
                        this.room.getId(),
                        exception);
            }
        }
    }

    public void removeDefinition(int definitionItemId) {
        if (definitionItemId <= 0) return;
        this.values.keySet().removeIf(key -> key.definitionItemId() == definitionItemId);
        try {
            this.repository.deleteDefinition(this.room.getId(), definitionItemId);
        } catch (SQLException exception) {
            LOGGER.error(
                    "Failed to remove wired array definition {} in room {}",
                    definitionItemId,
                    this.room.getId(),
                    exception);
        }
    }

    public void removeOwner(int ownerType, int ownerId) {
        if (ownerId <= 0) return;
        this.values.keySet().removeIf(key -> key.ownerType() == ownerType && key.ownerId() == ownerId);
        try {
            this.repository.deleteOwner(this.room.getId(), ownerType, ownerId);
        } catch (SQLException exception) {
            LOGGER.error(
                    "Failed to remove wired arrays for owner {} of type {} in room {}",
                    ownerId,
                    ownerType,
                    this.room.getId(),
                    exception);
        }
    }

    public boolean hasValues(int definitionItemId) {
        if (definitionItemId <= 0) return false;
        if (this.values.entrySet().stream()
                .anyMatch(entry -> entry.getKey().definitionItemId() == definitionItemId
                        && entry.getValue().exists()
                        && !entry.getValue().value().isEmpty())) {
            return true;
        }
        try {
            return this.repository.hasDefinition(this.room.getId(), definitionItemId);
        } catch (SQLException exception) {
            LOGGER.error(
                    "Failed to inspect wired array definition {} in room {}",
                    definitionItemId,
                    this.room.getId(),
                    exception);
            throw new IllegalArgumentException("Unable to verify whether this array contains stored values.");
        }
    }

    public void clearCache() {
        WiredArrayRuntimeMetrics.recordCacheEviction(this.values.size());
        this.values.clear();
    }

    private State getOrLoad(Key key, WiredArrayVariableDefinition definition) {
        State cached = this.values.get(key);
        if (cached != null) {
            WiredArrayRuntimeMetrics.recordCacheHit();
            return cached;
        }
        WiredArrayRuntimeMetrics.recordCacheMiss();

        State loaded;
        if (!definition.isArrayPermanent()) {
            loaded = State.absent(0L);
        } else {
            try {
                RoomArrayVariableRepository.StoredValue stored = this.repository.load(key);
                loaded = stored == null
                        ? State.absent(0L)
                        : new State(
                                stored.version(),
                                true,
                                WiredArrayValue.loaded(
                                        definition.getArrayDefinition(),
                                        stored.logicalLength(),
                                        WiredArraySettings.maxPopulatedCellsPerOwner(),
                                        stored.entries()));
            } catch (SQLException | RuntimeException exception) {
                LOGGER.error(
                        "Failed to restore wired array {} for owner {} in room {}",
                        definition.getId(),
                        key.ownerId(),
                        this.room.getId(),
                        exception);
                return null;
            }
        }

        State raced = this.values.putIfAbsent(key, loaded);
        return raced == null ? loaded : raced;
    }

    private boolean isValidOwner(WiredArrayVariableDefinition definition, int ownerId) {
        if (definition == null || !definition.isArray() || ownerId <= 0) return false;
        if (definition.getArrayStorageRoomId(this.room.getId()) <= 0
                || definition.getArrayStorageDefinitionItemId() <= 0) return false;
        return switch (definition.getArrayVariableType()) {
            case ROOM -> ownerId == definition.getArrayStorageRoomId(this.room.getId());
            case USER -> true;
            case FURNI -> this.room.getHabboItem(ownerId) != null;
            case CONTEXT -> false;
        };
    }

    private boolean hasScalarValues(WiredArrayVariableDefinition definition) {
        return switch (definition.getArrayVariableType()) {
            case ROOM -> this.room.getRoomVariableManager().hasAssignmentsForDefinition(definition.getId());
            case USER -> this.room.getUserVariableManager().hasAssignmentsForDefinition(definition.getId());
            case FURNI -> this.room.getFurniVariableManager().hasAssignmentsForDefinition(definition.getId());
            case CONTEXT -> false;
        };
    }

    private Key key(WiredArrayVariableDefinition definition, int ownerId) {
        return new Key(
                definition.getArrayStorageRoomId(this.room.getId()),
                definition.getArrayStorageDefinitionItemId(),
                definition.getArrayVariableType().code(),
                ownerId);
    }

    public record MutationOutcome(WiredArrayMutationResult result, WiredArrayValue value) {
        public boolean changed() {
            return this.result == WiredArrayMutationResult.SUCCESS;
        }
    }

    public record FieldMutationOutcome(
            WiredArrayMutationResult result,
            WiredArrayValue value,
            long previousValue,
            long currentValue,
            boolean created) {
        static FieldMutationOutcome failed(WiredArrayMutationResult result) {
            return new FieldMutationOutcome(result, null, 0L, 0L, false);
        }

        public boolean changed() {
            return this.result == WiredArrayMutationResult.SUCCESS;
        }
    }

    record Key(int roomId, int definitionItemId, int ownerType, int ownerId) {}

    private PublishResult publish(
            Key key, WiredArrayVariableDefinition definition, State current, WiredArrayValue candidate) {
        if (!definition.isArrayPermanent()) {
            State next = new State(current.version() + 1L, true, candidate);
            return this.values.replace(key, current, next) ? PublishResult.SUCCESS : PublishResult.RETRY;
        }
        int deletedRows = current.exists() ? current.value().getOccupiedCount() : 0;
        int upsertedRows = candidate.getOccupiedCount();
        long startedAt = System.nanoTime();
        try {
            long nextVersion =
                    this.repository.replace(key, current.version(), candidate, this.currentTimestamp.getAsInt());
            WiredArrayRuntimeMetrics.recordPersistence(
                    1, deletedRows, upsertedRows, System.nanoTime() - startedAt, true);
            if (nextVersion < 0) {
                this.values.remove(key, current);
                return PublishResult.RETRY;
            }
            State next = new State(nextVersion, true, candidate);
            this.values.compute(
                    key,
                    (ignored, existing) -> existing == null || existing.version() <= next.version() ? next : existing);
            if (definition.isArrayShared()) this.invalidateOtherCaches(key);
            return PublishResult.SUCCESS;
        } catch (SQLException | RuntimeException exception) {
            WiredArrayRuntimeMetrics.recordPersistence(
                    1, deletedRows, upsertedRows, System.nanoTime() - startedAt, false);
            LOGGER.error(
                    "Failed to persist wired array {} for owner {} in room {}",
                    definition.getId(),
                    key.ownerId(),
                    this.room.getId(),
                    exception);
            return PublishResult.FAILURE;
        }
    }

    private enum PublishResult {
        SUCCESS,
        RETRY,
        FAILURE
    }

    private void invalidateOtherCaches(Key key) {
        if (key == null || Emulator.getGameEnvironment() == null) return;
        for (Room activeRoom : Emulator.getGameEnvironment().getRoomManager().getActiveRooms()) {
            if (activeRoom == null || activeRoom == this.room) continue;
            activeRoom.getArrayVariableManager().invalidateStorageKey(key);
        }
    }

    private void invalidateStorageKey(Key key) {
        if (key != null && this.values.remove(key) != null) {
            WiredArrayRuntimeMetrics.recordCacheEviction(1);
        }
    }

    private record State(long version, boolean exists, WiredArrayValue value) {
        private State {
            if (exists != (value != null)) throw new IllegalArgumentException("Array state and value disagree.");
        }

        static State absent(long version) {
            return new State(version, false, null);
        }
    }
}
