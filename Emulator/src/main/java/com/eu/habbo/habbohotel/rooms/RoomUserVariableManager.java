package com.eu.habbo.habbohotel.rooms;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraVariableEcho;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraVariableReference;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredVariableReferenceSupport;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraUserVariable;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.wired.core.WiredEvent;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredVariableLevelSystemSupport;
import com.eu.habbo.habbohotel.wired.core.WiredVariableTextConnectorSupport;
import com.eu.habbo.messages.outgoing.wired.WiredUserVariablesDataComposer;
import gnu.trove.set.hash.THashSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class RoomUserVariableManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(RoomUserVariableManager.class);

    private final Room room;
    private final ConcurrentHashMap<Integer, ConcurrentHashMap<Integer, VariableAssignment>> activeAssignmentsByUserId;
    private final java.util.concurrent.atomic.AtomicBoolean broadcastRequested = new java.util.concurrent.atomic.AtomicBoolean(false);

    public RoomUserVariableManager(Room room) {
        this.room = room;
        this.activeAssignmentsByUserId = new ConcurrentHashMap<>();
    }

    public void restorePermanentAssignments(Habbo habbo) {
        if (habbo == null) {
            return;
        }

        int userId = habbo.getHabboInfo().getId();
        ConcurrentHashMap<Integer, VariableAssignment> restoredAssignments = new ConcurrentHashMap<>();
        List<Integer> staleDefinitionIds = new ArrayList<>();

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT variable_item_id, value, created_at, updated_at FROM room_user_wired_variables WHERE room_id = ? AND user_id = ?")) {
            statement.setInt(1, this.room.getId());
            statement.setInt(2, userId);

            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    int definitionItemId = set.getInt("variable_item_id");
                    WiredExtraUserVariable definition = this.getDefinition(definitionItemId);

                    if (definition == null || !definition.isPermanentAvailability()) {
                        staleDefinitionIds.add(definitionItemId);
                        continue;
                    }

                    Integer value = null;
                    int rawValue = set.getInt("value");
                    if (!set.wasNull()) {
                        value = rawValue;
                    }

                    int createdAt = normalizeTimestamp(set.getInt("created_at"), 0);
                    int updatedAt = normalizeTimestamp(set.getInt("updated_at"), createdAt);

                    restoredAssignments.put(definitionItemId, new VariableAssignment(value, createdAt, updatedAt));
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Failed to restore wired user variables for room {} and user {}", this.room.getId(), userId, e);
        }

        if (!staleDefinitionIds.isEmpty()) {
            for (Integer definitionItemId : staleDefinitionIds) {
                this.deletePersistentAssignment(userId, definitionItemId);
            }
        }

        if (restoredAssignments.isEmpty()) {
            this.activeAssignmentsByUserId.remove(userId);
        } else {
            this.activeAssignmentsByUserId.put(userId, restoredAssignments);
        }

        this.broadcastSnapshot();
    }

    public boolean assignVariable(Habbo habbo, WiredExtraUserVariable definition, Integer value, boolean overrideExisting) {
        return definition != null && this.assignVariable(habbo, definition.getId(), value, overrideExisting);
    }

    public boolean assignVariable(Habbo habbo, int definitionItemId, Integer value, boolean overrideExisting) {
        if (habbo == null || definitionItemId <= 0) {
            return false;
        }

        InteractionWiredExtra extra = this.getDefinitionExtra(definitionItemId);
        WiredVariableDefinitionInfo definitionInfo = this.getDefinitionInfo(definitionItemId);

        if (definitionInfo == null || definitionInfo.isReadOnly()) {
            return false;
        }

        int userId = habbo.getHabboInfo().getId();
        Integer normalizedValue = definitionInfo.hasValue() ? value : null;
        boolean hadBefore = this.hasVariable(userId, definitionItemId);
        Integer previousValue = (definitionInfo.hasValue() && hadBefore) ? this.getCurrentValue(userId, definitionItemId) : null;

        if (extra instanceof WiredExtraVariableReference) {
            boolean changed = WiredVariableReferenceSupport.assignSharedUserVariable((WiredExtraVariableReference) extra, userId, normalizedValue, overrideExisting);
            boolean shouldEmit = changed || (definitionInfo.hasValue() && hadBefore && overrideExisting && Objects.equals(previousValue, normalizedValue));

            if (shouldEmit) {
                boolean hasAfter = this.hasVariable(userId, definitionItemId);
                Integer currentValue = (definitionInfo.hasValue() && hasAfter) ? this.getCurrentValue(userId, definitionItemId) : null;
                this.emitVariableChangedEvents(userId, extra, definitionInfo, hadBefore, previousValue, hasAfter, currentValue);
            }

            if (changed) {
                this.broadcastSnapshot();
            }

            return changed;
        }

        if (extra instanceof WiredExtraVariableEcho) {
            boolean changed = ((WiredExtraVariableEcho) extra).assignValue(this.room, userId, normalizedValue, overrideExisting);
            boolean shouldEmit = changed || (definitionInfo.hasValue() && hadBefore && overrideExisting && Objects.equals(previousValue, normalizedValue));

            if (shouldEmit) {
                boolean hasAfter = this.hasVariable(userId, definitionItemId);
                Integer currentValue = (definitionInfo.hasValue() && hasAfter) ? this.getCurrentValue(userId, definitionItemId) : null;
                this.emitVariableChangedEvents(userId, extra, definitionInfo, hadBefore, previousValue, hasAfter, currentValue);
            }

            if (changed) {
                this.broadcastSnapshot();
            }

            return changed;
        }

        ConcurrentHashMap<Integer, VariableAssignment> assignments = this.activeAssignmentsByUserId.computeIfAbsent(userId, key -> new ConcurrentHashMap<>());
        VariableAssignment existingAssignment = assignments.get(definitionItemId);

        if (existingAssignment != null && !overrideExisting) {
            return false;
        }

        boolean overwritten = existingAssignment != null && overrideExisting;
        boolean valueChanged = existingAssignment == null || !Objects.equals(existingAssignment.getValue(), normalizedValue);
        boolean changed = overwritten || valueChanged;

        if (existingAssignment == null || overwritten) {
            int now = Emulator.getIntUnixTimestamp();
            assignments.put(definitionItemId, new VariableAssignment(normalizedValue, now, now));
        } else if (valueChanged) {
            existingAssignment.setValue(normalizedValue, Emulator.getIntUnixTimestamp());
        }

        WiredExtraUserVariable definition = (WiredExtraUserVariable) extra;

        if (definition.isPermanentAvailability()) {
            this.upsertPersistentAssignment(userId, definitionItemId, assignments.get(definitionItemId));
        } else {
            this.deletePersistentAssignment(userId, definitionItemId);
        }

        if (changed) {
            if (definition.isSharedAvailability()) {
                VariableAssignment assignment = assignments.get(definitionItemId);
                if (assignment != null) {
                    WiredVariableReferenceSupport.cacheSharedUserAssignment(this.room.getId(), definitionItemId, userId, assignment.getValue(), assignment.getCreatedAt(), assignment.getUpdatedAt());
                }
            } else {
                WiredVariableReferenceSupport.clearSharedUserAssignment(this.room.getId(), definitionItemId, userId);
            }
        }

        if (changed || (definitionInfo.hasValue() && hadBefore && overrideExisting && Objects.equals(previousValue, normalizedValue))) {
            boolean hasAfter = this.hasVariable(userId, definitionItemId);
            Integer currentValue = (definitionInfo.hasValue() && hasAfter) ? this.getCurrentValue(userId, definitionItemId) : null;
            this.emitVariableChangedEvents(userId, extra, definitionInfo, hadBefore, previousValue, hasAfter, currentValue);
        }

        if (changed) {
            this.broadcastSnapshot();
        }

        return changed;
    }

    public boolean updateVariableValue(int userId, int definitionItemId, Integer value) {
        if (userId <= 0 || definitionItemId <= 0) {
            return false;
        }

        InteractionWiredExtra extra = this.getDefinitionExtra(definitionItemId);
        WiredVariableDefinitionInfo definitionInfo = this.getDefinitionInfo(definitionItemId);

        if (definitionInfo == null || !definitionInfo.hasValue() || definitionInfo.isReadOnly()) {
            return false;
        }

        boolean hadBefore = this.hasVariable(userId, definitionItemId);
        Integer previousValue = hadBefore ? this.getCurrentValue(userId, definitionItemId) : null;

        if (extra instanceof WiredExtraVariableReference) {
            boolean changed = WiredVariableReferenceSupport.updateSharedUserVariable((WiredExtraVariableReference) extra, userId, value);
            boolean shouldEmit = changed || (hadBefore && Objects.equals(previousValue, value));

            if (shouldEmit) {
                boolean hasAfter = this.hasVariable(userId, definitionItemId);
                Integer currentValue = hasAfter ? this.getCurrentValue(userId, definitionItemId) : null;
                this.emitVariableChangedEvents(userId, extra, definitionInfo, hadBefore, previousValue, hasAfter, currentValue);
            }

            if (changed) {
                this.broadcastSnapshot();
            }

            return changed;
        }

        if (extra instanceof WiredExtraVariableEcho) {
            boolean changed = ((WiredExtraVariableEcho) extra).updateValue(this.room, userId, value);
            boolean shouldEmit = changed || (hadBefore && Objects.equals(previousValue, value));

            if (shouldEmit) {
                boolean hasAfter = this.hasVariable(userId, definitionItemId);
                Integer currentValue = hasAfter ? this.getCurrentValue(userId, definitionItemId) : null;
                this.emitVariableChangedEvents(userId, extra, definitionInfo, hadBefore, previousValue, hasAfter, currentValue);
            }

            if (changed) {
                this.broadcastSnapshot();
            }

            return changed;
        }

        ConcurrentHashMap<Integer, VariableAssignment> assignments = this.activeAssignmentsByUserId.get(userId);

        if (assignments == null) {
            return false;
        }

        VariableAssignment assignment = assignments.get(definitionItemId);

        if (assignment == null) {
            return false;
        }

        Integer normalizedValue = value;
        if (Objects.equals(assignment.getValue(), normalizedValue)) {
            this.emitVariableChangedEvents(userId, extra, definitionInfo, true, previousValue, true, assignment.getValue());
            return false;
        }

        assignment.setValue(normalizedValue, Emulator.getIntUnixTimestamp());

        WiredExtraUserVariable definition = (WiredExtraUserVariable) extra;

        if (definition.isPermanentAvailability()) {
            this.upsertPersistentAssignment(userId, definitionItemId, assignment);
        }

        if (definition.isSharedAvailability()) {
            WiredVariableReferenceSupport.cacheSharedUserAssignment(this.room.getId(), definitionItemId, userId, assignment.getValue(), assignment.getCreatedAt(), assignment.getUpdatedAt());
        }

        this.emitVariableChangedEvents(userId, extra, definitionInfo, hadBefore, previousValue, true, assignment.getValue());
        this.broadcastSnapshot();
        return true;
    }

    public int getCurrentValue(int userId, int definitionItemId) {
        if (userId <= 0 || definitionItemId <= 0) {
            return 0;
        }

        WiredVariableLevelSystemSupport.DerivedDefinition derivedDefinition = WiredVariableLevelSystemSupport.resolveDerivedDefinition(this.room, WiredVariableLevelSystemSupport.TARGET_USER, definitionItemId);
        if (derivedDefinition != null) {
            Integer baseValue = this.getRawValue(userId, derivedDefinition.getBaseDefinitionItemId());
            Integer derivedValue = WiredVariableLevelSystemSupport.getDerivedValue(derivedDefinition.getLevelSystem(), derivedDefinition.getSubvariableType(), baseValue);
            return (derivedValue != null) ? derivedValue : 0;
        }

        InteractionWiredExtra extra = this.getDefinitionExtra(definitionItemId);
        if (extra instanceof WiredExtraVariableReference) {
            WiredVariableReferenceSupport.SharedUserAssignment assignment = WiredVariableReferenceSupport.getSharedUserAssignment((WiredExtraVariableReference) extra, userId);
            return (assignment != null && assignment.getValue() != null) ? assignment.getValue() : 0;
        }

        if (extra instanceof WiredExtraVariableEcho) {
            return ((WiredExtraVariableEcho) extra).getCurrentValue(this.room, userId);
        }

        ConcurrentHashMap<Integer, VariableAssignment> assignments = this.activeAssignmentsByUserId.get(userId);

        if (assignments == null) {
            return 0;
        }

        VariableAssignment assignment = assignments.get(definitionItemId);

        if (assignment == null || assignment.getValue() == null) {
            return 0;
        }

        return assignment.getValue();
    }

    public int getCreatedAt(int userId, int definitionItemId) {
        if (userId <= 0 || definitionItemId <= 0) {
            return 0;
        }

        WiredVariableLevelSystemSupport.DerivedDefinition derivedDefinition = WiredVariableLevelSystemSupport.resolveDerivedDefinition(this.room, WiredVariableLevelSystemSupport.TARGET_USER, definitionItemId);
        if (derivedDefinition != null) {
            VariableAssignment assignment = this.getRawAssignment(userId, derivedDefinition.getBaseDefinitionItemId());
            return (assignment != null) ? assignment.getCreatedAt() : 0;
        }

        InteractionWiredExtra extra = this.getDefinitionExtra(definitionItemId);
        if (extra instanceof WiredExtraVariableReference) {
            WiredVariableReferenceSupport.SharedUserAssignment assignment = WiredVariableReferenceSupport.getSharedUserAssignment((WiredExtraVariableReference) extra, userId);
            return assignment != null ? assignment.getCreatedAt() : 0;
        }

        if (extra instanceof WiredExtraVariableEcho) {
            return ((WiredExtraVariableEcho) extra).getCreatedAt(this.room, userId);
        }

        ConcurrentHashMap<Integer, VariableAssignment> assignments = this.activeAssignmentsByUserId.get(userId);

        if (assignments == null) {
            return 0;
        }

        VariableAssignment assignment = assignments.get(definitionItemId);
        return assignment != null ? assignment.getCreatedAt() : 0;
    }

    public int getUpdatedAt(int userId, int definitionItemId) {
        if (userId <= 0 || definitionItemId <= 0) {
            return 0;
        }

        WiredVariableLevelSystemSupport.DerivedDefinition derivedDefinition = WiredVariableLevelSystemSupport.resolveDerivedDefinition(this.room, WiredVariableLevelSystemSupport.TARGET_USER, definitionItemId);
        if (derivedDefinition != null) {
            VariableAssignment assignment = this.getRawAssignment(userId, derivedDefinition.getBaseDefinitionItemId());
            return (assignment != null) ? assignment.getUpdatedAt() : 0;
        }

        InteractionWiredExtra extra = this.getDefinitionExtra(definitionItemId);
        if (extra instanceof WiredExtraVariableReference) {
            WiredVariableReferenceSupport.SharedUserAssignment assignment = WiredVariableReferenceSupport.getSharedUserAssignment((WiredExtraVariableReference) extra, userId);
            return assignment != null ? assignment.getUpdatedAt() : 0;
        }

        if (extra instanceof WiredExtraVariableEcho) {
            return ((WiredExtraVariableEcho) extra).getUpdatedAt(this.room, userId);
        }

        ConcurrentHashMap<Integer, VariableAssignment> assignments = this.activeAssignmentsByUserId.get(userId);

        if (assignments == null) {
            return 0;
        }

        VariableAssignment assignment = assignments.get(definitionItemId);
        return assignment != null ? assignment.getUpdatedAt() : 0;
    }

    public boolean hasVariable(int userId, int definitionItemId) {
        if (userId <= 0 || definitionItemId <= 0) {
            return false;
        }

        WiredVariableLevelSystemSupport.DerivedDefinition derivedDefinition = WiredVariableLevelSystemSupport.resolveDerivedDefinition(this.room, WiredVariableLevelSystemSupport.TARGET_USER, definitionItemId);
        if (derivedDefinition != null) {
            return this.getRawAssignment(userId, derivedDefinition.getBaseDefinitionItemId()) != null;
        }

        InteractionWiredExtra extra = this.getDefinitionExtra(definitionItemId);
        if (extra instanceof WiredExtraVariableReference) {
            return WiredVariableReferenceSupport.getSharedUserAssignment((WiredExtraVariableReference) extra, userId) != null;
        }

        if (extra instanceof WiredExtraVariableEcho) {
            return ((WiredExtraVariableEcho) extra).hasVariable(this.room, userId);
        }

        ConcurrentHashMap<Integer, VariableAssignment> assignments = this.activeAssignmentsByUserId.get(userId);

        return assignments != null && assignments.containsKey(definitionItemId);
    }

    public boolean removeVariable(int userId, int definitionItemId) {
        if (userId <= 0 || definitionItemId <= 0) {
            return false;
        }

        WiredVariableDefinitionInfo definitionInfo = this.getDefinitionInfo(definitionItemId);
        if (definitionInfo == null || definitionInfo.isReadOnly()) {
            return false;
        }

        boolean hadBefore = this.hasVariable(userId, definitionItemId);
        Integer previousValue = (definitionInfo.hasValue() && hadBefore) ? this.getCurrentValue(userId, definitionItemId) : null;

        InteractionWiredExtra extra = this.getDefinitionExtra(definitionItemId);
        if (extra instanceof WiredExtraVariableReference) {
            boolean changed = WiredVariableReferenceSupport.removeSharedUserVariable((WiredExtraVariableReference) extra, userId);

            if (changed) {
                this.emitVariableChangedEvents(userId, extra, definitionInfo, hadBefore, previousValue, false, null);
            }

            if (changed) {
                this.broadcastSnapshot();
            }

            return changed;
        }

        if (extra instanceof WiredExtraVariableEcho) {
            boolean changed = ((WiredExtraVariableEcho) extra).removeValue(this.room, userId);

            if (changed) {
                boolean hasAfter = this.hasVariable(userId, definitionItemId);
                Integer currentValue = (definitionInfo.hasValue() && hasAfter) ? this.getCurrentValue(userId, definitionItemId) : null;
                this.emitVariableChangedEvents(userId, extra, definitionInfo, hadBefore, previousValue, hasAfter, currentValue);
            }

            if (changed) {
                this.broadcastSnapshot();
            }

            return changed;
        }

        ConcurrentHashMap<Integer, VariableAssignment> assignments = this.activeAssignmentsByUserId.get(userId);

        if (assignments == null) {
            return false;
        }

        if (assignments.remove(definitionItemId) == null) {
            return false;
        }

        if (assignments.isEmpty()) {
            this.activeAssignmentsByUserId.remove(userId, assignments);
        }

        this.deletePersistentAssignment(userId, definitionItemId);

        WiredExtraUserVariable definition = this.getDefinition(definitionItemId);
        if (definition != null && definition.isSharedAvailability()) {
            WiredVariableReferenceSupport.clearSharedUserAssignment(this.room.getId(), definitionItemId, userId);
        }

        this.emitVariableChangedEvents(userId, extra, definitionInfo, hadBefore, previousValue, false, null);
        this.broadcastSnapshot();

        return true;
    }

    public void clearAssignmentsForUser(int userId) {
        if (userId <= 0) {
            return;
        }

        if (this.activeAssignmentsByUserId.remove(userId) != null) {
            this.broadcastSnapshot();
        }
    }

    public void removeDefinition(int definitionItemId) {
        boolean changed = false;

        for (Map.Entry<Integer, ConcurrentHashMap<Integer, VariableAssignment>> entry : this.activeAssignmentsByUserId.entrySet()) {
            ConcurrentHashMap<Integer, VariableAssignment> assignments = entry.getValue();
            if (assignments.remove(definitionItemId) != null) {
                changed = true;
            }

            if (assignments.isEmpty()) {
                this.activeAssignmentsByUserId.remove(entry.getKey(), assignments);
            }
        }

        this.deletePersistentAssignmentsForDefinition(definitionItemId);
        WiredExtraUserVariable definition = this.getDefinition(definitionItemId);
        if (definition != null && definition.isSharedAvailability()) {
            WiredVariableReferenceSupport.clearSharedUserDefinition(this.room.getId(), definitionItemId);
        }

        if (changed) {
            this.broadcastSnapshot();
            return;
        }

        this.broadcastSnapshot();
    }

    public void handleDefinitionUpdated(WiredExtraUserVariable definition) {
        if (definition == null) {
            return;
        }

        if (!definition.isPermanentAvailability()) {
            this.deletePersistentAssignmentsForDefinition(definition.getId());
        } else {
            for (Map.Entry<Integer, ConcurrentHashMap<Integer, VariableAssignment>> entry : this.activeAssignmentsByUserId.entrySet()) {
                VariableAssignment assignment = entry.getValue().get(definition.getId());

                if (assignment == null) continue;

                this.upsertPersistentAssignment(entry.getKey(), definition.getId(), assignment);
            }
        }

        if (definition.isSharedAvailability()) {
            for (Map.Entry<Integer, ConcurrentHashMap<Integer, VariableAssignment>> entry : this.activeAssignmentsByUserId.entrySet()) {
                VariableAssignment assignment = entry.getValue().get(definition.getId());

                if (assignment == null) {
                    continue;
                }

                WiredVariableReferenceSupport.cacheSharedUserAssignment(this.room.getId(), definition.getId(), entry.getKey(), assignment.getValue(), assignment.getCreatedAt(), assignment.getUpdatedAt());
            }
        } else {
            WiredVariableReferenceSupport.clearSharedUserDefinition(this.room.getId(), definition.getId());
        }

        this.broadcastSnapshot();
    }

    public Snapshot createSnapshot() {
        List<DefinitionEntry> definitions = new ArrayList<>();
        Map<Integer, DefinitionEntry> definitionsById = new LinkedHashMap<>();
        List<Integer> derivedDefinitionIds = new ArrayList<>();

        for (WiredVariableDefinitionInfo definition : this.getAllDefinitionInfos()) {
            DefinitionEntry entry = new DefinitionEntry(definition.getItemId(), definition.getName(), definition.hasValue(), definition.getAvailability(), definition.isTextConnected(), definition.isReadOnly());
            definitions.add(entry);
            definitionsById.put(entry.getItemId(), entry);

            if (WiredVariableLevelSystemSupport.resolveDerivedDefinition(this.room, WiredVariableLevelSystemSupport.TARGET_USER, definition.getItemId()) != null) {
                derivedDefinitionIds.add(definition.getItemId());
            }
        }

        List<UserAssignmentsEntry> users = new ArrayList<>();
        List<WiredExtraVariableReference> userReferences = this.getUserReferences();
        List<WiredExtraVariableEcho> userEchoes = this.getUserEchoes();
        THashSet<Integer> userIds = new THashSet<>();
        userIds.addAll(this.activeAssignmentsByUserId.keySet());

        for (Habbo habbo : this.room.getCurrentHabbos().values()) {
            if (habbo != null) {
                userIds.add(habbo.getHabboInfo().getId());
            }
        }

        for (Integer userId : userIds) {
            List<AssignmentEntry> assignments = new ArrayList<>();
            ConcurrentHashMap<Integer, VariableAssignment> localAssignments = this.activeAssignmentsByUserId.get(userId);

            if (localAssignments != null) {
                for (Map.Entry<Integer, VariableAssignment> assignmentEntry : localAssignments.entrySet()) {
                    if (!definitionsById.containsKey(assignmentEntry.getKey())) {
                        continue;
                    }

                    assignments.add(new AssignmentEntry(
                        assignmentEntry.getKey(),
                        assignmentEntry.getValue().getValue(),
                        assignmentEntry.getValue().getCreatedAt(),
                        assignmentEntry.getValue().getUpdatedAt()
                    ));
                }
            }

            for (WiredExtraVariableReference reference : userReferences) {
                if (!definitionsById.containsKey(reference.getId())) {
                    continue;
                }

                WiredVariableReferenceSupport.SharedUserAssignment assignment = WiredVariableReferenceSupport.getSharedUserAssignment(reference, userId);
                if (assignment == null) {
                    continue;
                }

                assignments.add(new AssignmentEntry(reference.getId(), assignment.getValue(), assignment.getCreatedAt(), assignment.getUpdatedAt()));
            }

            for (WiredExtraVariableEcho echo : userEchoes) {
                if (!definitionsById.containsKey(echo.getId()) || !echo.hasVariable(this.room, userId)) {
                    continue;
                }

                assignments.add(new AssignmentEntry(
                    echo.getId(),
                    echo.getCurrentValue(this.room, userId),
                    echo.getCreatedAt(this.room, userId),
                    echo.getUpdatedAt(this.room, userId)
                ));
            }

            for (Integer derivedDefinitionId : derivedDefinitionIds) {
                if (!this.hasVariable(userId, derivedDefinitionId)) {
                    continue;
                }

                assignments.add(new AssignmentEntry(
                    derivedDefinitionId,
                    this.getCurrentValue(userId, derivedDefinitionId),
                    this.getCreatedAt(userId, derivedDefinitionId),
                    this.getUpdatedAt(userId, derivedDefinitionId)
                ));
            }

            assignments.sort(Comparator.comparingInt(AssignmentEntry::getVariableItemId));

            if (!assignments.isEmpty()) {
                users.add(new UserAssignmentsEntry(userId, assignments));
            }
        }

        users.sort(Comparator.comparingInt(UserAssignmentsEntry::getUserId));

        return new Snapshot(this.room.getId(), definitions, users);
    }

    public void sendSnapshot(Habbo habbo) {
        if (habbo == null || habbo.getClient() == null) {
            return;
        }

        if (!this.room.canInspectWired(habbo)) {
            return;
        }

        habbo.getClient().sendResponse(new WiredUserVariablesDataComposer(this.createSnapshot(), this.room.getFurniVariableManager().createSnapshot(), this.room.getRoomVariableManager().createSnapshot()));
    }

    public void requestBroadcast() {
        if (this.broadcastRequested.compareAndSet(false, true)) {
            Emulator.getThreading().run(() -> {
                this.broadcastRequested.set(false);
                if (this.room.isLoaded()) {
                    this.broadcastSnapshotRaw();
                }
            }, 50);
        }
    }

    public void broadcastSnapshot() {
        this.requestBroadcast();
    }

    public void broadcastSnapshotRaw() {
        Snapshot userSnapshot = this.createSnapshot();
        RoomFurniVariableManager.Snapshot furniSnapshot = this.room.getFurniVariableManager().createSnapshot();
        RoomVariableManager.Snapshot roomSnapshot = this.room.getRoomVariableManager().createSnapshot();

        for (Habbo habbo : this.room.getCurrentHabbos().values()) {
            if (habbo == null || habbo.getClient() == null) {
                continue;
            }

            if (!this.room.canInspectWired(habbo)) {
                continue;
            }

            habbo.getClient().sendResponse(new WiredUserVariablesDataComposer(userSnapshot, furniSnapshot, roomSnapshot));
        }
    }

    public Collection<WiredExtraUserVariable> getDefinitions() {
        if (this.room.getRoomSpecialTypes() == null) {
            return Collections.emptyList();
        }

        THashSet<InteractionWiredExtra> extras = this.room.getRoomSpecialTypes().getExtras();
        List<WiredExtraUserVariable> result = new ArrayList<>();

        for (InteractionWiredExtra extra : extras) {
            if (extra instanceof WiredExtraUserVariable) {
                WiredExtraUserVariable definition = (WiredExtraUserVariable) extra;

                if (!hasVisibleDefinitionName(definition.getVariableName())) {
                    continue;
                }

                result.add(definition);
            }
        }

        result.sort(Comparator.comparing(WiredExtraUserVariable::getVariableName, String.CASE_INSENSITIVE_ORDER).thenComparingInt(WiredExtraUserVariable::getId));
        return result;
    }

    public Collection<WiredVariableDefinitionInfo> getAllDefinitionInfos() {
        List<WiredVariableDefinitionInfo> result = new ArrayList<>();
        List<WiredVariableDefinitionInfo> baseDefinitions = new ArrayList<>();

        for (WiredExtraUserVariable definition : this.getDefinitions()) {
            baseDefinitions.add(new WiredVariableDefinitionInfo(
                definition.getId(),
                definition.getVariableName(),
                definition.hasValue(),
                definition.getAvailability(),
                WiredVariableTextConnectorSupport.isTextConnected(this.room, definition),
                false
            ));
        }

        for (WiredExtraVariableReference reference : this.getUserReferences()) {
            baseDefinitions.add(new WiredVariableDefinitionInfo(
                reference.getId(),
                reference.getVariableName(),
                reference.hasValue(),
                reference.getAvailability(),
                false,
                reference.isReadOnly()
            ));
        }

        for (WiredExtraVariableEcho echo : this.getUserEchoes()) {
            baseDefinitions.add(echo.createDefinitionInfo(this.room));
        }

        result.addAll(baseDefinitions);

        for (WiredVariableDefinitionInfo definition : baseDefinitions) {
            result.addAll(WiredVariableLevelSystemSupport.getDerivedDefinitions(this.room, WiredVariableLevelSystemSupport.TARGET_USER, this.getDefinitionExtra(definition.getItemId()), definition));
        }

        result.sort(Comparator.comparing(WiredVariableDefinitionInfo::getName, String.CASE_INSENSITIVE_ORDER).thenComparingInt(WiredVariableDefinitionInfo::getItemId));
        return result;
    }

    public boolean hasDefinition(int definitionItemId) {
        return this.getDefinitionInfo(definitionItemId) != null;
    }

    public WiredVariableDefinitionInfo getDefinitionInfo(int definitionItemId) {
        InteractionWiredExtra extra = this.getDefinitionExtra(definitionItemId);

        if (extra instanceof WiredExtraUserVariable) {
            WiredExtraUserVariable definition = (WiredExtraUserVariable) extra;

            if (!hasVisibleDefinitionName(definition.getVariableName())) {
                return null;
            }

            return new WiredVariableDefinitionInfo(
                definition.getId(),
                definition.getVariableName(),
                definition.hasValue(),
                definition.getAvailability(),
                WiredVariableTextConnectorSupport.isTextConnected(this.room, definition),
                false
            );
        }

        if (extra instanceof WiredExtraVariableReference && ((WiredExtraVariableReference) extra).isUserReference()) {
            WiredExtraVariableReference reference = (WiredExtraVariableReference) extra;

            if (!hasVisibleDefinitionName(reference.getVariableName())) {
                return null;
            }

            return new WiredVariableDefinitionInfo(reference.getId(), reference.getVariableName(), reference.hasValue(), reference.getAvailability(), false, reference.isReadOnly());
        }

        if (extra instanceof WiredExtraVariableEcho && ((WiredExtraVariableEcho) extra).isUserEcho()) {
            WiredVariableDefinitionInfo info = ((WiredExtraVariableEcho) extra).createDefinitionInfo(this.room);
            return (info != null && hasVisibleDefinitionName(info.getName())) ? info : null;
        }

        return WiredVariableLevelSystemSupport.getDerivedDefinitionInfo(this.room, WiredVariableLevelSystemSupport.TARGET_USER, definitionItemId);
    }

    private WiredExtraUserVariable getDefinition(int definitionItemId) {
        InteractionWiredExtra extra = this.getDefinitionExtra(definitionItemId);

        if (!(extra instanceof WiredExtraUserVariable)) {
            return null;
        }

        return (WiredExtraUserVariable) extra;
    }

    private InteractionWiredExtra getDefinitionExtra(int definitionItemId) {
        if (this.room.getRoomSpecialTypes() == null || definitionItemId <= 0) {
            return null;
        }

        return this.room.getRoomSpecialTypes().getExtra(definitionItemId);
    }

    private List<WiredExtraVariableReference> getUserReferences() {
        if (this.room.getRoomSpecialTypes() == null) {
            return Collections.emptyList();
        }

        List<WiredExtraVariableReference> result = new ArrayList<>();

        for (InteractionWiredExtra extra : this.room.getRoomSpecialTypes().getExtras()) {
            if (extra instanceof WiredExtraVariableReference && ((WiredExtraVariableReference) extra).isUserReference()) {
                WiredExtraVariableReference reference = (WiredExtraVariableReference) extra;

                if (!hasVisibleDefinitionName(reference.getVariableName())) {
                    continue;
                }

                result.add(reference);
            }
        }

        result.sort(Comparator.comparing(WiredExtraVariableReference::getVariableName, String.CASE_INSENSITIVE_ORDER).thenComparingInt(WiredExtraVariableReference::getId));
        return result;
    }

    private List<WiredExtraVariableEcho> getUserEchoes() {
        if (this.room.getRoomSpecialTypes() == null) {
            return Collections.emptyList();
        }

        List<WiredExtraVariableEcho> result = new ArrayList<>();

        for (InteractionWiredExtra extra : this.room.getRoomSpecialTypes().getExtras()) {
            if (extra instanceof WiredExtraVariableEcho && ((WiredExtraVariableEcho) extra).isUserEcho()) {
                WiredExtraVariableEcho echo = (WiredExtraVariableEcho) extra;

                if (!hasVisibleDefinitionName(echo.getVariableName())) {
                    continue;
                }

                result.add(echo);
            }
        }

        result.sort(Comparator.comparing(WiredExtraVariableEcho::getVariableName, String.CASE_INSENSITIVE_ORDER).thenComparingInt(WiredExtraVariableEcho::getId));
        return result;
    }

    private static boolean hasVisibleDefinitionName(String name) {
        return name != null && !name.trim().isEmpty();
    }

    private VariableAssignment getRawAssignment(int userId, int definitionItemId) {
        if (userId <= 0 || definitionItemId <= 0) {
            return null;
        }

        InteractionWiredExtra extra = this.getDefinitionExtra(definitionItemId);
        if (extra instanceof WiredExtraVariableReference) {
            WiredVariableReferenceSupport.SharedUserAssignment assignment = WiredVariableReferenceSupport.getSharedUserAssignment((WiredExtraVariableReference) extra, userId);
            return (assignment != null) ? new VariableAssignment(assignment.getValue(), assignment.getCreatedAt(), assignment.getUpdatedAt()) : null;
        }

        if (extra instanceof WiredExtraVariableEcho) {
            WiredExtraVariableEcho echo = (WiredExtraVariableEcho) extra;
            if (!echo.hasVariable(this.room, userId)) {
                return null;
            }

            return new VariableAssignment(echo.getCurrentValue(this.room, userId), echo.getCreatedAt(this.room, userId), echo.getUpdatedAt(this.room, userId));
        }

        ConcurrentHashMap<Integer, VariableAssignment> assignments = this.activeAssignmentsByUserId.get(userId);
        return (assignments != null) ? assignments.get(definitionItemId) : null;
    }

    private Integer getRawValue(int userId, int definitionItemId) {
        VariableAssignment assignment = this.getRawAssignment(userId, definitionItemId);
        return (assignment != null) ? assignment.getValue() : null;
    }

    private void emitVariableChangedEvents(int userId, InteractionWiredExtra definitionExtra, WiredVariableDefinitionInfo definitionInfo, boolean existedBefore, Integer previousValue, boolean existsAfter, Integer currentValue) {
        if (definitionInfo == null) {
            return;
        }

        this.emitVariableChangedEvent(userId, definitionInfo.getItemId(), definitionInfo.hasValue(), existedBefore, previousValue, existsAfter, currentValue);

        for (WiredVariableDefinitionInfo derivedDefinition : WiredVariableLevelSystemSupport.getDerivedDefinitions(this.room, WiredVariableLevelSystemSupport.TARGET_USER, definitionExtra, definitionInfo)) {
            WiredVariableLevelSystemSupport.DerivedDefinition resolvedDefinition = WiredVariableLevelSystemSupport.resolveDerivedDefinition(this.room, WiredVariableLevelSystemSupport.TARGET_USER, derivedDefinition.getItemId());

            if (resolvedDefinition == null) {
                continue;
            }

            Integer derivedPreviousValue = existedBefore
                    ? WiredVariableLevelSystemSupport.getDerivedValue(resolvedDefinition.getLevelSystem(), resolvedDefinition.getSubvariableType(), previousValue)
                    : null;
            Integer derivedCurrentValue = existsAfter
                    ? WiredVariableLevelSystemSupport.getDerivedValue(resolvedDefinition.getLevelSystem(), resolvedDefinition.getSubvariableType(), currentValue)
                    : null;

            this.emitVariableChangedEvent(userId, derivedDefinition.getItemId(), true, existedBefore, derivedPreviousValue, existsAfter, derivedCurrentValue);
        }
    }

    private void emitVariableChangedEvent(int userId, int definitionItemId, boolean hasValue, boolean existedBefore, Integer previousValue, boolean existsAfter, Integer currentValue) {
        boolean created = !existedBefore && existsAfter;
        boolean deleted = existedBefore && !existsAfter;
        WiredEvent.VariableChangeKind changeKind = resolveVariableChangeKind(hasValue, existedBefore, previousValue, existsAfter, currentValue);

        if (!created && !deleted && changeKind == WiredEvent.VariableChangeKind.NONE) {
            return;
        }

        WiredManager.triggerUserVariableChanged(this.room, userId, definitionItemId, created, deleted, changeKind);
    }

    private static WiredEvent.VariableChangeKind resolveVariableChangeKind(boolean hasValue, boolean existedBefore, Integer previousValue, boolean existsAfter, Integer currentValue) {
        if (!hasValue || !existedBefore || !existsAfter) {
            return WiredEvent.VariableChangeKind.NONE;
        }

        if (Objects.equals(previousValue, currentValue)) {
            return WiredEvent.VariableChangeKind.UNCHANGED;
        }

        int previousNumericValue = (previousValue != null) ? previousValue : 0;
        int currentNumericValue = (currentValue != null) ? currentValue : 0;

        return (currentNumericValue > previousNumericValue)
                ? WiredEvent.VariableChangeKind.INCREASED
                : WiredEvent.VariableChangeKind.DECREASED;
    }

    private void upsertPersistentAssignment(int userId, int definitionItemId, VariableAssignment assignment) {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement("INSERT INTO room_user_wired_variables (room_id, user_id, variable_item_id, value, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE value = VALUES(value), updated_at = VALUES(updated_at)")) {
            statement.setInt(1, this.room.getId());
            statement.setInt(2, userId);
            statement.setInt(3, definitionItemId);

            if (assignment == null || assignment.getValue() == null) {
                statement.setNull(4, java.sql.Types.INTEGER);
            } else {
                statement.setInt(4, assignment.getValue());
            }

            int now = Emulator.getIntUnixTimestamp();
            statement.setInt(5, (assignment != null) ? assignment.getCreatedAt() : now);
            statement.setInt(6, (assignment != null) ? assignment.getUpdatedAt() : now);

            statement.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("Failed to store permanent wired user variable for room {}, user {}, item {}", this.room.getId(), userId, definitionItemId, e);
        }
    }

    private void deletePersistentAssignment(int userId, int definitionItemId) {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM room_user_wired_variables WHERE room_id = ? AND user_id = ? AND variable_item_id = ?")) {
            statement.setInt(1, this.room.getId());
            statement.setInt(2, userId);
            statement.setInt(3, definitionItemId);
            statement.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("Failed to delete permanent wired user variable for room {}, user {}, item {}", this.room.getId(), userId, definitionItemId, e);
        }
    }

    private void deletePersistentAssignmentsForDefinition(int definitionItemId) {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM room_user_wired_variables WHERE room_id = ? AND variable_item_id = ?")) {
            statement.setInt(1, this.room.getId());
            statement.setInt(2, definitionItemId);
            statement.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("Failed to delete permanent wired user variables for room {} and item {}", this.room.getId(), definitionItemId, e);
        }
    }

    public static class Snapshot {
        private final int roomId;
        private final List<DefinitionEntry> definitions;
        private final List<UserAssignmentsEntry> users;

        public Snapshot(int roomId, List<DefinitionEntry> definitions, List<UserAssignmentsEntry> users) {
            this.roomId = roomId;
            this.definitions = definitions;
            this.users = users;
        }

        public int getRoomId() {
            return roomId;
        }

        public List<DefinitionEntry> getDefinitions() {
            return definitions;
        }

        public List<UserAssignmentsEntry> getUsers() {
            return users;
        }
    }

    public static class DefinitionEntry {
        private final int itemId;
        private final String name;
        private final boolean hasValue;
        private final int availability;
        private final boolean textConnected;
        private final boolean readOnly;

        public DefinitionEntry(int itemId, String name, boolean hasValue, int availability, boolean textConnected, boolean readOnly) {
            this.itemId = itemId;
            this.name = name;
            this.hasValue = hasValue;
            this.availability = availability;
            this.textConnected = textConnected;
            this.readOnly = readOnly;
        }

        public int getItemId() {
            return itemId;
        }

        public String getName() {
            return name;
        }

        public boolean hasValue() {
            return hasValue;
        }

        public int getAvailability() {
            return availability;
        }

        public boolean isTextConnected() {
            return this.textConnected;
        }

        public boolean isReadOnly() {
            return this.readOnly;
        }
    }

    public static class UserAssignmentsEntry {
        private final int userId;
        private final List<AssignmentEntry> assignments;

        public UserAssignmentsEntry(int userId, List<AssignmentEntry> assignments) {
            this.userId = userId;
            this.assignments = assignments;
        }

        public int getUserId() {
            return userId;
        }

        public List<AssignmentEntry> getAssignments() {
            return assignments;
        }
    }

    public static class AssignmentEntry {
        private final int variableItemId;
        private final Integer value;
        private final int createdAt;
        private final int updatedAt;

        public AssignmentEntry(int variableItemId, Integer value, int createdAt, int updatedAt) {
            this.variableItemId = variableItemId;
            this.value = value;
            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
        }

        public int getVariableItemId() {
            return variableItemId;
        }

        public boolean hasValue() {
            return value != null;
        }

        public Integer getValue() {
            return value;
        }

        public int getCreatedAt() {
            return createdAt;
        }

        public int getUpdatedAt() {
            return updatedAt;
        }
    }

    private static class VariableAssignment {
        private Integer value;
        private final int createdAt;
        private int updatedAt;

        public VariableAssignment(Integer value, int createdAt, int updatedAt) {
            this.value = value;
            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
        }

        public Integer getValue() {
            return value;
        }

        public void setValue(Integer value, int updatedAt) {
            this.value = value;
            this.updatedAt = updatedAt;
        }

        public int getCreatedAt() {
            return createdAt;
        }

        public int getUpdatedAt() {
            return updatedAt;
        }
    }

    private static int normalizeTimestamp(int value, int fallback) {
        if (value > 0) return value;
        if (fallback > 0) return fallback;
        return Emulator.getIntUnixTimestamp();
    }
}
