package com.eu.habbo.habbohotel.rooms;

import com.eu.habbo.habbohotel.GameEnvironment;
import com.eu.habbo.habbohotel.wired.arrays.WiredArrayDefinition;
import com.eu.habbo.habbohotel.wired.arrays.WiredArrayMutationResult;
import com.eu.habbo.habbohotel.wired.arrays.WiredArrayNumericOperation;
import com.eu.habbo.habbohotel.wired.arrays.WiredArrayRuntimeMetrics;
import com.eu.habbo.habbohotel.wired.arrays.WiredArraySettings;
import com.eu.habbo.habbohotel.wired.arrays.WiredArrayStructuralOperation;
import com.eu.habbo.habbohotel.wired.arrays.WiredArrayValue;
import com.eu.habbo.habbohotel.wired.arrays.WiredArrayVariableDefinition;
import com.eu.habbo.habbohotel.wired.arrays.WiredArrayView;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntSupplier;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Room-owned array store with optimistic publication and transactional permanent replacement. */
public final class RoomArrayVariableManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(RoomArrayVariableManager.class);
    private static final int MAX_MUTATION_ATTEMPTS = 3;
    /** Bounded locks shared by source and reference rooms; loads and invalidations use the same lock. */
    private static final Object[] STORAGE_LOCKS = java.util.stream.IntStream.range(0, 128)
            .mapToObj(ignored -> new Object())
            .toArray();

    private static final ConcurrentHashMap<SharedKey, SharedHandleReference> SHARED_VALUES = new ConcurrentHashMap<>();
    private static final ReferenceQueue<ValueHandle> RELEASED_HANDLES = new ReferenceQueue<>();

    private final Object cacheScope;
    private final Room room;
    private final RoomArrayVariableRepository repository;
    private final IntSupplier currentTimestamp;
    private final ConcurrentHashMap<Key, State> values = new ConcurrentHashMap<>();

    RoomArrayVariableManager(Room room, RoomDependencies dependencies) {
        this(room, new RoomArrayVariableRepository(dependencies.database()), dependencies.unixTime());
    }

    RoomArrayVariableManager(Room room, RoomArrayVariableRepository repository, IntSupplier currentTimestamp) {
        this.room = room;
        this.cacheScope = room.gameEnvironment() == null ? new Object() : room.gameEnvironment();
        this.repository = repository;
        this.currentTimestamp = currentTimestamp;
    }

    public WiredArrayView getValue(WiredArrayVariableDefinition definition, int ownerId) {
        synchronized (this.storageLock(definition)) {
            if (!this.isValidOwner(definition, ownerId)
                    || (definition.isArrayShared() && !definition.isArraySourceValid())) return null;
            Key key = this.key(definition, ownerId);
            State state = this.getOrLoad(key, definition);
            return state == null || !state.exists() ? null : state.value();
        }
    }

    public boolean hasValue(WiredArrayVariableDefinition definition, int ownerId) {
        synchronized (this.storageLock(definition)) {
            if (!this.isValidOwner(definition, ownerId)
                    || (definition.isArrayShared() && !definition.isArraySourceValid())) return false;
            State state = this.getOrLoad(this.key(definition, ownerId), definition);
            return state != null && state.exists();
        }
    }

    public MutationOutcome give(WiredArrayVariableDefinition definition, int ownerId, boolean overrideExisting) {
        synchronized (this.storageLock(definition)) {
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
                    return new MutationOutcome(WiredArrayMutationResult.NO_CHANGE, current.value());
                }
                WiredArrayValue candidate = WiredArrayValue.empty(
                        definition.getArrayDefinition(), WiredArraySettings.maxPopulatedCellsPerOwner());
                PublishResult published = this.publish(key, definition, current, candidate);
                if (published == PublishResult.SUCCESS) {
                    return new MutationOutcome(WiredArrayMutationResult.SUCCESS, candidate, current.value());
                }
                if (published == PublishResult.LIMIT)
                    return new MutationOutcome(WiredArrayMutationResult.POPULATED_CELL_LIMIT, current.value());
                if (published == PublishResult.FAILURE) {
                    return new MutationOutcome(WiredArrayMutationResult.PERSISTENCE_FAILED, null);
                }
            }
            return new MutationOutcome(WiredArrayMutationResult.PERSISTENCE_FAILED, null);
        }
    }

    public boolean remove(WiredArrayVariableDefinition definition, int ownerId) {
        synchronized (this.storageLock(definition)) {
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
                    this.installState(key, State.absent(current.version() + 1L));
                    return true;
                }
                long startedAt = System.nanoTime();
                try {
                    if (!this.repository.delete(key, current.version())) {
                        WiredArrayRuntimeMetrics.recordPersistence(
                                1, current.value().getOccupiedCount(), 0, System.nanoTime() - startedAt, true);
                        this.invalidateSharedState(key);
                        this.values.remove(key, current);
                        continue;
                    }
                    WiredArrayRuntimeMetrics.recordPersistence(
                            1, current.value().getOccupiedCount(), 0, System.nanoTime() - startedAt, true);
                    this.installState(key, State.absent(0L));
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
    }

    public MutationOutcome mutate(
            WiredArrayVariableDefinition definition,
            int ownerId,
            WiredArrayStructuralOperation operation,
            int firstIndex,
            int secondIndex,
            Map<Integer, Long> entryValues) {
        synchronized (this.storageLock(definition)) {
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
                if (result != WiredArrayMutationResult.SUCCESS) return new MutationOutcome(result, current.value());

                PublishResult published = this.publish(key, definition, current, candidate);
                if (published == PublishResult.SUCCESS) {
                    return new MutationOutcome(WiredArrayMutationResult.SUCCESS, candidate);
                }
                if (published == PublishResult.LIMIT)
                    return new MutationOutcome(WiredArrayMutationResult.POPULATED_CELL_LIMIT, current.value());
                if (published == PublishResult.FAILURE)
                    return new MutationOutcome(WiredArrayMutationResult.PERSISTENCE_FAILED, current.value());
            }
            return new MutationOutcome(WiredArrayMutationResult.PERSISTENCE_FAILED, null);
        }
    }

    public record StructuralMutation(
            int ownerId,
            WiredArrayStructuralOperation operation,
            int firstIndex,
            int secondIndex,
            Map<Integer, Long> values) {}

    /** Evaluate each owner's operands before calling; only successful candidates enter the transaction. */
    public List<MutationOutcome> mutateBatch(
            WiredArrayVariableDefinition definition, List<StructuralMutation> requests) {
        if (requests == null || requests.isEmpty()) return List.of();
        if (requests.size() > WiredArraySettings.maxOwnersPerExecution()
                || requests.stream().map(StructuralMutation::ownerId).distinct().count() != requests.size()) {
            return requests.stream()
                    .map(ignored -> new MutationOutcome(WiredArrayMutationResult.POPULATED_CELL_LIMIT, null))
                    .toList();
        }
        synchronized (this.storageLock(definition)) {
            if (definition == null || !definition.isArrayWritable() || !definition.isArraySourceValid()) {
                return requests.stream()
                        .map(ignored -> new MutationOutcome(WiredArrayMutationResult.MISSING_OWNER, null))
                        .toList();
            }
            for (int attempt = 0; attempt < MAX_MUTATION_ATTEMPTS; attempt++) {
                List<MutationOutcome> outcomes = new ArrayList<>();
                Map<Key, State> currentValues = new LinkedHashMap<>();
                Map<Key, WiredArrayValue> candidates = new LinkedHashMap<>();
                long pendingCells = 0;
                try {
                    var storedUsage = definition.isArrayPermanent()
                                    || this.storageManager(definition.getArrayStorageRoomId(this.room.getId()))
                                            .hasPermanentDefinitions()
                            ? this.repository.storageUsageSnapshot(definition.getArrayStorageRoomId(this.room.getId()))
                            : null;
                    for (StructuralMutation request : requests) {
                        if (!this.isValidOwner(definition, request.ownerId())) {
                            outcomes.add(new MutationOutcome(WiredArrayMutationResult.MISSING_OWNER, null));
                            continue;
                        }
                        Key key = this.key(definition, request.ownerId());
                        State current = this.getOrLoad(key, definition);
                        if (current == null || !current.exists()) {
                            outcomes.add(new MutationOutcome(
                                    current == null
                                            ? WiredArrayMutationResult.PERSISTENCE_FAILED
                                            : WiredArrayMutationResult.MISSING_OWNER,
                                    null));
                            continue;
                        }
                        WiredArrayValue candidate = current.value().copy();
                        WiredArrayMutationResult result = candidate.apply(
                                request.operation(), request.firstIndex(), request.secondIndex(), request.values());
                        if (result == WiredArrayMutationResult.SUCCESS
                                && !this.withinStorageLimits(key, current, candidate, pendingCells, storedUsage)) {
                            result = WiredArrayMutationResult.POPULATED_CELL_LIMIT;
                        }
                        if (result == WiredArrayMutationResult.SUCCESS) {
                            currentValues.put(key, current);
                            candidates.put(key, candidate);
                            pendingCells += cells(candidate) - cells(current.value());
                        }
                        outcomes.add(new MutationOutcome(
                                result, result == WiredArrayMutationResult.SUCCESS ? candidate : current.value()));
                    }
                    if (candidates.isEmpty()) return outcomes;
                    Map<Key, Long> versions = new LinkedHashMap<>();
                    if (definition.isArrayPermanent()) {
                        Map<Key, RoomArrayVariableRepository.Replacement> replacements = new LinkedHashMap<>();
                        candidates.forEach((key, candidate) -> replacements.put(
                                key,
                                new RoomArrayVariableRepository.Replacement(
                                        currentValues.get(key).version(),
                                        WiredArrayPersistenceDelta.between(
                                                currentValues.get(key).value(), candidate))));
                        versions = this.repository.replaceBatch(replacements, this.currentTimestamp.getAsInt());
                        if (versions.isEmpty()) {
                            currentValues.forEach((key, current) -> {
                                this.invalidateSharedState(key);
                                this.values.remove(key, current);
                            });
                            continue;
                        }
                    }
                    for (var entry : candidates.entrySet()) {
                        Key key = entry.getKey();
                        this.installState(
                                key,
                                new State(
                                        versions.getOrDefault(
                                                key, currentValues.get(key).version() + 1L),
                                        true,
                                        entry.getValue(),
                                        definition.isArrayPermanent()));
                        if (definition.isArrayShared()) this.invalidateOtherCaches(key);
                    }
                    this.trimCache(null);
                    return outcomes;
                } catch (SQLException | RuntimeException exception) {
                    LOGGER.error(
                            "Failed to persist wired array batch {} in room {}",
                            definition.getId(),
                            this.room.getId(),
                            exception);
                    return requests.stream()
                            .map(ignored -> new MutationOutcome(WiredArrayMutationResult.PERSISTENCE_FAILED, null))
                            .toList();
                }
            }
            return requests.stream()
                    .map(ignored -> new MutationOutcome(WiredArrayMutationResult.PERSISTENCE_FAILED, null))
                    .toList();
        }
    }

    public FieldMutationOutcome mutateField(
            WiredArrayVariableDefinition definition,
            int ownerId,
            int index,
            int fieldId,
            WiredArrayNumericOperation operation,
            long reference) {
        return this.mutateFieldInternal(definition, ownerId, index, 0L, fieldId, operation, reference)
                .mutation();
    }

    public CapturedFieldMutationOutcome mutateCapturedField(
            WiredArrayVariableDefinition definition,
            int ownerId,
            long runtimeId,
            int fieldId,
            WiredArrayNumericOperation operation,
            long reference) {
        if (runtimeId <= 0L)
            return new CapturedFieldMutationOutcome(
                    FieldMutationOutcome.failed(WiredArrayMutationResult.MISSING_ENTRY), -1);
        return this.mutateFieldInternal(definition, ownerId, -1, runtimeId, fieldId, operation, reference);
    }

    private CapturedFieldMutationOutcome mutateFieldInternal(
            WiredArrayVariableDefinition definition,
            int ownerId,
            int requestedIndex,
            long runtimeId,
            int fieldId,
            WiredArrayNumericOperation operation,
            long reference) {
        synchronized (this.storageLock(definition)) {
            boolean permitted = this.isValidOwner(definition, ownerId)
                    && definition.isArrayWritable()
                    && definition.isArraySourceValid();
            WiredArrayRuntimeMetrics.recordGuard(1, 0L, 0L, permitted);
            if (!permitted) return CapturedFieldMutationOutcome.failed(WiredArrayMutationResult.MISSING_OWNER);
            Key key = this.key(definition, ownerId);
            for (int attempt = 0; attempt < MAX_MUTATION_ATTEMPTS; attempt++) {
                State current = this.getOrLoad(key, definition);
                if (current == null)
                    return CapturedFieldMutationOutcome.failed(WiredArrayMutationResult.PERSISTENCE_FAILED);
                if (!current.exists())
                    return CapturedFieldMutationOutcome.failed(WiredArrayMutationResult.MISSING_OWNER);
                WiredArrayValue candidate = current.value().copy();
                int index = runtimeId > 0 ? candidate.findEntryIndex(runtimeId) : requestedIndex;
                if (runtimeId > 0 && index < 0)
                    return CapturedFieldMutationOutcome.failed(WiredArrayMutationResult.MISSING_ENTRY);
                WiredArrayValue.FieldMutation mutation = candidate.mutateField(index, fieldId, operation, reference);
                if (!mutation.changed())
                    return new CapturedFieldMutationOutcome(
                            new FieldMutationOutcome(
                                    mutation.result(),
                                    current.value(),
                                    mutation.previousValue(),
                                    mutation.currentValue(),
                                    false),
                            index);
                PublishResult published = this.publish(key, definition, current, candidate);
                if (published == PublishResult.SUCCESS)
                    return new CapturedFieldMutationOutcome(
                            new FieldMutationOutcome(
                                    WiredArrayMutationResult.SUCCESS,
                                    candidate,
                                    mutation.previousValue(),
                                    mutation.currentValue(),
                                    mutation.created()),
                            index);
                if (published == PublishResult.LIMIT)
                    return CapturedFieldMutationOutcome.failed(WiredArrayMutationResult.POPULATED_CELL_LIMIT);
                if (published == PublishResult.FAILURE)
                    return CapturedFieldMutationOutcome.failed(WiredArrayMutationResult.PERSISTENCE_FAILED);
            }
            return CapturedFieldMutationOutcome.failed(WiredArrayMutationResult.PERSISTENCE_FAILED);
        }
    }

    public record CapturedFieldMutationOutcome(FieldMutationOutcome mutation, int index) {
        private static CapturedFieldMutationOutcome failed(WiredArrayMutationResult result) {
            return new CapturedFieldMutationOutcome(FieldMutationOutcome.failed(result), -1);
        }
    }

    public void updateDefinition(
            WiredArrayVariableDefinition definition,
            WiredArrayDefinition replacement,
            boolean nextPermanent,
            Runnable applyDefinition) {
        synchronized (this.storageLock(definition)) {
            this.validateDefinitionChange(definition, replacement, nextPermanent);
            applyDefinition.run();
            this.handleDefinitionUpdated(definition);
        }
    }

    public void validateDefinitionChange(
            WiredArrayVariableDefinition currentDefinition, WiredArrayDefinition replacement, boolean nextPermanent) {
        synchronized (this.storageLock(currentDefinition)) {
            if (currentDefinition == null) return;
            if (currentDefinition.isArrayUnavailable()) {
                throw new IllegalArgumentException(
                        "This array definition is unavailable and cannot be changed without correcting its stored data.");
            }
            WiredArrayDefinition current = currentDefinition.getArrayDefinition();
            if (current == null && replacement == null) return;

            if (current == null && replacement != null && this.hasScalarValues(currentDefinition)) {
                throw new IllegalArgumentException(
                        "Remove existing scalar assignments before converting this variable into an array.");
            }

            if (replacement != null && current != null) {
                this.validateProjectedCells(currentDefinition, replacement);
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
    }

    public void handleDefinitionUpdated(WiredArrayVariableDefinition definition) {
        synchronized (this.storageLock(definition)) {
            if (definition == null) return;
            this.forEachSharedHandle(
                    key -> key.roomId() == this.room.getId() && key.definitionItemId() == definition.getId(),
                    (key, handle) -> {
                        State state = handle.state;
                        if (state == null || !state.exists()) return;
                        if (!definition.isArray()) {
                            handle.state = null;
                            this.values.remove(key);
                            return;
                        }
                        WiredArrayValue current = state.value();
                        WiredArrayValue replacement = current.isEmpty()
                                        && (!current.getDefinition().isShapeCompatible(definition.getArrayDefinition())
                                                || definition
                                                                .getArrayDefinition()
                                                                .getMaxEntries()
                                                        < current.getDefinition()
                                                                .getMaxEntries())
                                ? WiredArrayValue.empty(
                                        definition.getArrayDefinition(), WiredArraySettings.maxPopulatedCellsPerOwner())
                                : current.redefined(definition.getArrayDefinition());
                        this.installState(
                                key, new State(state.version(), true, replacement, definition.isArrayPermanent()));
                    });
            if (definition.isArrayUnavailable()) return;
            this.invalidateOtherDefinitionCaches(this.room.getId(), definition.getId());
            this.trimCache(null);
            if (!definition.isArrayDeclared() || !definition.isArrayPermanent()) {
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
    }

    public List<Integer> clearAllAssignments(WiredArrayVariableDefinition definition) {
        if (definition == null) return List.of();
        synchronized (this.storageLock(definition)) {
            if (!definition.isArrayWritable() || !definition.isArraySourceValid()) return List.of();
            int storageRoom = definition.getArrayStorageRoomId(this.room.getId());
            int storageDefinition = definition.getArrayStorageDefinitionItemId();
            List<Integer> owners = this.values.entrySet().stream()
                    .filter(entry -> entry.getKey().roomId() == storageRoom
                            && entry.getKey().definitionItemId() == storageDefinition
                            && entry.getValue().exists())
                    .map(entry -> entry.getKey().ownerId())
                    .toList();
            try {
                if (definition.isArrayPermanent()) this.repository.deleteDefinition(storageRoom, storageDefinition);
            } catch (SQLException exception) {
                LOGGER.error("Failed to clear wired array {} in room {}", storageDefinition, storageRoom, exception);
                return List.of();
            }
            this.invalidateSharedHandles(
                    key -> key.roomId() == storageRoom && key.definitionItemId() == storageDefinition);
            this.values
                    .keySet()
                    .removeIf(key -> key.roomId() == storageRoom && key.definitionItemId() == storageDefinition);
            this.invalidateOtherDefinitionCaches(storageRoom, storageDefinition);
            return owners;
        }
    }

    public void removeDefinition(int definitionItemId) {
        synchronized (storageLock(this.room.getId())) {
            if (definitionItemId <= 0) return;
            this.invalidateSharedHandles(
                    key -> key.roomId() == this.room.getId() && key.definitionItemId() == definitionItemId);
            this.values.keySet().removeIf(key -> key.definitionItemId() == definitionItemId);
            this.invalidateOtherDefinitionCaches(this.room.getId(), definitionItemId);
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
    }

    public void removeOwner(int ownerType, int ownerId) {
        synchronized (storageLock(this.room.getId())) {
            if (ownerId <= 0) return;
            this.invalidateSharedHandles(key ->
                    key.roomId() == this.room.getId() && key.ownerType() == ownerType && key.ownerId() == ownerId);
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
    }

    public boolean hasValues(int definitionItemId) {
        synchronized (storageLock(this.room.getId())) {
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
    }

    public void clearCache() {
        synchronized (storageLock(this.room.getId())) {
            this.forEachSharedHandle(key -> key.roomId() == this.room.getId(), (key, handle) -> {
                if (handle.state != null && !handle.state.permanent()) handle.state = null;
            });
        }
        WiredArrayRuntimeMetrics.recordCacheEviction(this.values.size());
        this.values.clear();
    }

    /** Leaving a room drops temporary assignments and cached permanent values, never persisted data. */
    public void clearAssignmentsForUser(int userId) {
        if (userId <= 0) return;
        synchronized (storageLock(this.room.getId())) {
            this.forEachSharedHandle(
                    key -> key.roomId() == this.room.getId() && key.ownerType() == 2 && key.ownerId() == userId,
                    (key, handle) -> {
                        if (handle.state != null && !handle.state.permanent()) handle.state = null;
                    });
            this.values.keySet().removeIf(key -> key.ownerType() == 2 && key.ownerId() == userId);
        }
    }

    private static Object storageLock(int roomId) {
        drainReleasedHandles();
        return STORAGE_LOCKS[Math.floorMod(roomId, STORAGE_LOCKS.length)];
    }

    private Object storageLock(WiredArrayVariableDefinition definition) {
        return storageLock(
                definition == null ? this.room.getId() : definition.getArrayStorageRoomId(this.room.getId()));
    }

    private void validateProjectedCells(WiredArrayVariableDefinition definition, WiredArrayDefinition replacement) {
        int maximumEntries = this.values.entrySet().stream()
                .filter(entry -> entry.getKey().roomId() == this.room.getId()
                        && entry.getKey().definitionItemId() == definition.getId()
                        && entry.getValue().exists())
                .mapToInt(entry -> entry.getValue().value().getOccupiedCount())
                .max()
                .orElse(0);
        if (definition.isArrayPermanent()) {
            try {
                maximumEntries = Math.max(
                        maximumEntries, this.repository.maximumOccupiedEntries(this.room.getId(), definition.getId()));
            } catch (SQLException exception) {
                throw new IllegalArgumentException("Unable to verify the stored array size.", exception);
            }
        }
        this.forEachSharedHandle(
                key -> key.roomId() == this.room.getId() && key.definitionItemId() == definition.getId(),
                (key, handle) -> {
                    State state = handle.state;
                    if (state != null
                            && state.exists()
                            && !state.value().isEmpty()
                            && state.value().getDefinition().isShapeCompatible(replacement)
                            && replacement.getMaxEntries()
                                    >= state.value().getDefinition().getMaxEntries()) {
                        state.value().redefined(replacement);
                    }
                });
        if ((long) maximumEntries * replacement.getFields().size() > WiredArraySettings.maxPopulatedCellsPerOwner()) {
            throw new IllegalArgumentException(
                    "Adding these fields would exceed the populated-data safety limit. Clear array entries first.");
        }
    }

    private void invalidateOtherDefinitionCaches(int storageRoomId, int definitionItemId) {
        GameEnvironment environment = this.room.gameEnvironment();
        if (environment == null || environment.getRoomManager() == null) return;
        for (Room activeRoom : environment.getRoomManager().getActiveRooms()) {
            if (activeRoom == null || activeRoom == this.room) continue;
            RoomArrayVariableManager manager = activeRoom.getArrayVariableManager();
            if (manager != null)
                manager.values
                        .keySet()
                        .removeIf(key -> key.roomId() == storageRoomId && key.definitionItemId() == definitionItemId);
        }
    }

    private State getOrLoad(Key key, WiredArrayVariableDefinition definition) {
        ValueHandle handle = this.sharedHandle(key);
        State cached = handle == null ? null : handle.state;
        if (cached != null) {
            WiredArrayRuntimeMetrics.recordCacheHit();
            this.values.put(key, cached);
            this.trimCache(key);
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
                                        stored.entries()),
                                true);
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

        State installed = this.installState(key, loaded);
        this.trimCache(key);
        return installed;
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

    public record MutationOutcome(WiredArrayMutationResult result, WiredArrayView value, WiredArrayView previousValue) {
        public MutationOutcome(WiredArrayMutationResult result, WiredArrayView value) {
            this(result, value, null);
        }

        public boolean changed() {
            return this.result == WiredArrayMutationResult.SUCCESS;
        }
    }

    public record FieldMutationOutcome(
            WiredArrayMutationResult result,
            WiredArrayView value,
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
        try {
            if (!this.withinStorageLimits(key, current, candidate, definition.isArrayPermanent()))
                return PublishResult.LIMIT;
        } catch (SQLException exception) {
            LOGGER.error("Unable to check wired array storage usage in room {}", key.roomId(), exception);
            return PublishResult.FAILURE;
        }
        if (!definition.isArrayPermanent()) {
            State next = new State(current.version() + 1L, true, candidate);
            this.installState(key, next);
            return PublishResult.SUCCESS;
        }
        WiredArrayPersistenceDelta delta =
                WiredArrayPersistenceDelta.between(current.exists() ? current.value() : null, candidate);
        int deletedRows = delta.removedIndexes().size();
        int upsertedRows = delta.upsertedEntries().size();
        long startedAt = System.nanoTime();
        try {
            long nextVersion = this.repository.replace(key, current.version(), delta, this.currentTimestamp.getAsInt());
            WiredArrayRuntimeMetrics.recordPersistence(
                    1, deletedRows, upsertedRows, System.nanoTime() - startedAt, true);
            if (nextVersion < 0) {
                this.invalidateSharedState(key);
                this.values.remove(key, current);
                return PublishResult.RETRY;
            }
            State next = new State(nextVersion, true, candidate, true);
            this.installState(key, next);
            if (definition.isArrayShared()) this.invalidateOtherCaches(key);
            this.trimCache(key);
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

    private boolean withinStorageLimits(Key key, State current, WiredArrayValue candidate, boolean permanent)
            throws SQLException {
        var storedUsage = permanent || this.storageManager(key.roomId()).hasPermanentDefinitions()
                ? this.repository.storageUsageSnapshot(key.roomId())
                : null;
        return this.withinStorageLimits(key, current, candidate, 0L, storedUsage);
    }

    private boolean withinStorageLimits(
            Key key,
            State current,
            WiredArrayValue candidate,
            long pendingCells,
            RoomArrayVariableRepository.StorageUsageSnapshot storedUsage) {
        long roomArrays = 0, roomCells = 0, ownerArrays = 0, ownerCells = 0;
        RoomArrayVariableManager storageManager = this.storageManager(key.roomId());
        for (var entry : storageManager.values.entrySet()) {
            State state = entry.getValue();
            Key existing = entry.getKey();
            if (!state.exists() || state.permanent() || existing.roomId() != key.roomId()) continue;
            long cells = cells(state.value());
            roomArrays++;
            roomCells += cells;
            if (existing.ownerType() == key.ownerType() && existing.ownerId() == key.ownerId()) {
                ownerArrays++;
                ownerCells += cells;
            }
        }
        if (storedUsage != null) {
            roomArrays += storedUsage.arrays();
            roomCells += storedUsage.cells();
            var owner = storedUsage.owner(key.ownerType(), key.ownerId());
            ownerArrays += owner.arrays();
            ownerCells += owner.cells();
        }
        long deltaCells = cells(candidate) - (current.exists() ? cells(current.value()) : 0L);
        long deltaArrays = current.exists() ? 0L : 1L;
        // Always permit reductions, including cleanup after an operator lowers a limit.
        if (deltaCells <= 0 && deltaArrays == 0) return true;
        return roomArrays + deltaArrays <= WiredArraySettings.maxArraysPerRoom()
                && roomCells + deltaCells + pendingCells <= WiredArraySettings.maxTotalCellsPerRoom()
                && ownerArrays + deltaArrays <= WiredArraySettings.maxArraysPerOwner()
                && ownerCells + deltaCells <= WiredArraySettings.maxTotalCellsPerOwner();
    }

    private RoomArrayVariableManager storageManager(int storageRoomId) {
        if (this.room.getId() == storageRoomId) return this;
        var environment = this.room.gameEnvironment();
        if (environment != null && environment.getRoomManager() != null) {
            Room source = environment.getRoomManager().getRoom(storageRoomId);
            if (source != null && source.getArrayVariableManager() != null) return source.getArrayVariableManager();
        }
        return this;
    }

    private boolean hasPermanentDefinitions() {
        if (this.room.getRoomSpecialTypes() == null) return false;
        return this.room.getRoomSpecialTypes().getExtras().stream()
                .anyMatch(extra -> extra instanceof WiredArrayVariableDefinition definition
                        && definition.isArrayPermanent()
                        && definition.isArrayDeclared());
    }

    private static long cells(WiredArrayValue value) {
        return (long) value.getOccupiedCount()
                * value.getDefinition().getFields().size();
    }

    /** Durable entries can be reloaded; temporary arrays are protected by admission limits instead. */
    private void trimCache(Key protectedKey) {
        synchronized (this.values) {
            long cachedCells = this.values.values().stream()
                    .filter(State::exists)
                    .mapToLong(state -> cells(state.value()))
                    .sum();
            for (var entry : this.values.entrySet()) {
                if (this.values.size() <= WiredArraySettings.maxArraysPerRoom()
                        && cachedCells <= WiredArraySettings.maxCachedCellsPerRoom()) break;
                State state = entry.getValue();
                if (entry.getKey().equals(protectedKey) || (state.exists() && !state.permanent())) continue;
                if (this.values.remove(entry.getKey(), state)) {
                    cachedCells -= state.exists() ? cells(state.value()) : 0L;
                    WiredArrayRuntimeMetrics.recordCacheEviction(1);
                }
            }
        }
    }

    private enum PublishResult {
        SUCCESS,
        RETRY,
        LIMIT,
        FAILURE
    }

    private void invalidateOtherCaches(Key key) {
        GameEnvironment environment = this.room.gameEnvironment();
        if (key == null || environment == null || environment.getRoomManager() == null) return;
        for (Room activeRoom : environment.getRoomManager().getActiveRooms()) {
            if (activeRoom == null || activeRoom == this.room) continue;
            activeRoom.getArrayVariableManager().invalidateStorageKey(key);
        }
    }

    private void invalidateStorageKey(Key key) {
        if (key != null && this.values.remove(key) != null) {
            WiredArrayRuntimeMetrics.recordCacheEviction(1);
        }
    }

    /** Opaque, scope-owned lease. It retains array identity without retaining a Room or its managers. */
    public Object retainCapturedValue(WiredArrayVariableDefinition definition, int ownerId) {
        synchronized (this.storageLock(definition)) {
            if (this.getValue(definition, ownerId) == null) return null;
            return this.sharedHandle(this.key(definition, ownerId));
        }
    }

    private ValueHandle sharedHandle(Key key) {
        SharedHandleReference reference = SHARED_VALUES.get(new SharedKey(this.cacheScope, key));
        return reference == null ? null : reference.get();
    }

    private State installState(Key key, State value) {
        ValueHandle handle = this.sharedHandle(key);
        if (handle == null) {
            handle = new ValueHandle();
            SharedKey sharedKey = new SharedKey(this.cacheScope, key);
            SHARED_VALUES.put(sharedKey, new SharedHandleReference(sharedKey, handle));
        }
        State installed = new State(value.version(), value.exists(), value.value(), value.permanent(), handle);
        handle.state = installed;
        this.values.put(key, installed);
        return installed;
    }

    private void invalidateSharedState(Key key) {
        ValueHandle handle = this.sharedHandle(key);
        if (handle != null) handle.state = null;
    }

    private void invalidateSharedHandles(Predicate<Key> matches) {
        this.forEachSharedHandle(matches, (key, handle) -> handle.state = null);
    }

    private void forEachSharedHandle(Predicate<Key> matches, java.util.function.BiConsumer<Key, ValueHandle> action) {
        SHARED_VALUES.forEach((sharedKey, reference) -> {
            if (sharedKey.scope() != this.cacheScope || !matches.test(sharedKey.key())) return;
            ValueHandle handle = reference.get();
            if (handle != null) action.accept(sharedKey.key(), handle);
        });
    }

    private static void drainReleasedHandles() {
        SharedHandleReference reference;
        while ((reference = (SharedHandleReference) RELEASED_HANDLES.poll()) != null) {
            SHARED_VALUES.remove(reference.key, reference);
        }
    }

    private record SharedKey(Object scope, Key key) {}

    private static final class ValueHandle {
        private volatile State state;
    }

    private static final class SharedHandleReference extends WeakReference<ValueHandle> {
        private final SharedKey key;

        private SharedHandleReference(SharedKey key, ValueHandle handle) {
            super(handle, RELEASED_HANDLES);
            this.key = key;
        }
    }

    private record State(long version, boolean exists, WiredArrayValue value, boolean permanent, ValueHandle handle) {
        private State(long version, boolean exists, WiredArrayValue value, boolean permanent) {
            this(version, exists, value, permanent, null);
        }

        private State(long version, boolean exists, WiredArrayValue value) {
            this(version, exists, value, false);
        }

        private State {
            if (exists != (value != null)) throw new IllegalArgumentException("Array state and value disagree.");
        }

        static State absent(long version) {
            return new State(version, false, null);
        }
    }
}
