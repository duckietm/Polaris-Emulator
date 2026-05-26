package com.eu.habbo.habbohotel.rooms;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraRoomVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraVariableEcho;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraVariableReference;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredVariableReferenceSupport;
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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class RoomVariableManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(RoomVariableManager.class);

    private final Room room;
    private final ConcurrentHashMap<Integer, VariableAssignment> activeAssignmentsByDefinitionId;
    private volatile boolean persistentValuesLoaded;
    private final java.util.concurrent.atomic.AtomicBoolean broadcastRequested = new java.util.concurrent.atomic.AtomicBoolean(false);

    public RoomVariableManager(Room room) {
        this.room = room;
        this.activeAssignmentsByDefinitionId = new ConcurrentHashMap<>();
        this.persistentValuesLoaded = false;
    }

    public void ensurePersistentValuesLoaded() {
        if (this.persistentValuesLoaded) {
            return;
        }

        synchronized (this) {
            if (this.persistentValuesLoaded) {
                return;
            }

            List<Integer> staleDefinitionIds = new ArrayList<>();

            try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                 PreparedStatement statement = connection.prepareStatement("SELECT variable_item_id, value, created_at, updated_at FROM room_wired_variables WHERE room_id = ?")) {
                statement.setInt(1, this.room.getId());

                try (ResultSet set = statement.executeQuery()) {
                    while (set.next()) {
                        int definitionItemId = set.getInt("variable_item_id");
                        WiredExtraRoomVariable definition = this.getDefinition(definitionItemId);

                        if (definition == null || !definition.isPermanentAvailability()) {
                            staleDefinitionIds.add(definitionItemId);
                            continue;
                        }

                        int updatedAt = normalizeTimestamp(set.getInt("updated_at"), 0);

                        this.activeAssignmentsByDefinitionId.put(definitionItemId, new VariableAssignment(set.getInt("value"), 0, updatedAt));
                    }
                }
            } catch (SQLException e) {
                LOGGER.error("Failed to restore wired room variables for room {}", this.room.getId(), e);
            }

            for (Integer definitionItemId : staleDefinitionIds) {
                this.deletePersistentAssignment(definitionItemId);
            }

            this.persistentValuesLoaded = true;
        }
    }

    public int getCurrentValue(int definitionItemId) {
        this.ensurePersistentValuesLoaded();

        WiredVariableLevelSystemSupport.DerivedDefinition derivedDefinition = WiredVariableLevelSystemSupport.resolveDerivedDefinition(this.room, WiredVariableLevelSystemSupport.TARGET_ROOM, definitionItemId);
        if (derivedDefinition != null) {
            Integer baseValue = this.getRawValue(derivedDefinition.getBaseDefinitionItemId());
            Integer derivedValue = WiredVariableLevelSystemSupport.getDerivedValue(derivedDefinition.getLevelSystem(), derivedDefinition.getSubvariableType(), baseValue);
            return (derivedValue != null) ? derivedValue : 0;
        }

        InteractionWiredExtra extra = this.getDefinitionExtra(definitionItemId);
        if (extra instanceof WiredExtraVariableEcho) {
            return ((WiredExtraVariableEcho) extra).getCurrentValue(this.room, this.room.getId());
        }

        if (extra instanceof WiredExtraVariableReference) {
            WiredVariableReferenceSupport.SharedRoomAssignment assignment = WiredVariableReferenceSupport.getSharedRoomAssignment((WiredExtraVariableReference) extra);
            return assignment != null ? assignment.getValue() : 0;
        }

        VariableAssignment assignment = this.activeAssignmentsByDefinitionId.get(definitionItemId);

        return (assignment != null) ? assignment.getValue() : 0;
    }

    public int getCreatedAt(int definitionItemId) {
        this.ensurePersistentValuesLoaded();

        WiredVariableLevelSystemSupport.DerivedDefinition derivedDefinition = WiredVariableLevelSystemSupport.resolveDerivedDefinition(this.room, WiredVariableLevelSystemSupport.TARGET_ROOM, definitionItemId);
        if (derivedDefinition != null) {
            VariableAssignment assignment = this.getRawAssignment(derivedDefinition.getBaseDefinitionItemId());
            return (assignment != null) ? assignment.getCreatedAt() : 0;
        }

        InteractionWiredExtra extra = this.getDefinitionExtra(definitionItemId);
        if (extra instanceof WiredExtraVariableEcho) {
            return ((WiredExtraVariableEcho) extra).getCreatedAt(this.room, this.room.getId());
        }

        if (extra instanceof WiredExtraVariableReference) {
            return 0;
        }

        VariableAssignment assignment = this.activeAssignmentsByDefinitionId.get(definitionItemId);
        return (assignment != null) ? assignment.getCreatedAt() : 0;
    }

    public int getUpdatedAt(int definitionItemId) {
        this.ensurePersistentValuesLoaded();

        WiredVariableLevelSystemSupport.DerivedDefinition derivedDefinition = WiredVariableLevelSystemSupport.resolveDerivedDefinition(this.room, WiredVariableLevelSystemSupport.TARGET_ROOM, definitionItemId);
        if (derivedDefinition != null) {
            VariableAssignment assignment = this.getRawAssignment(derivedDefinition.getBaseDefinitionItemId());
            return (assignment != null) ? assignment.getUpdatedAt() : 0;
        }

        InteractionWiredExtra extra = this.getDefinitionExtra(definitionItemId);
        if (extra instanceof WiredExtraVariableEcho) {
            return ((WiredExtraVariableEcho) extra).getUpdatedAt(this.room, this.room.getId());
        }

        if (extra instanceof WiredExtraVariableReference) {
            WiredVariableReferenceSupport.SharedRoomAssignment assignment = WiredVariableReferenceSupport.getSharedRoomAssignment((WiredExtraVariableReference) extra);
            return assignment != null ? assignment.getUpdatedAt() : 0;
        }

        VariableAssignment assignment = this.activeAssignmentsByDefinitionId.get(definitionItemId);
        return (assignment != null) ? assignment.getUpdatedAt() : 0;
    }

    public boolean hasVariable(int definitionItemId) {
        if (definitionItemId <= 0) {
            return false;
        }

        WiredVariableLevelSystemSupport.DerivedDefinition derivedDefinition = WiredVariableLevelSystemSupport.resolveDerivedDefinition(this.room, WiredVariableLevelSystemSupport.TARGET_ROOM, definitionItemId);
        if (derivedDefinition != null) {
            return this.getRawAssignment(derivedDefinition.getBaseDefinitionItemId()) != null;
        }

        InteractionWiredExtra extra = this.getDefinitionExtra(definitionItemId);
        if (extra instanceof WiredExtraVariableEcho) {
            return ((WiredExtraVariableEcho) extra).hasVariable(this.room, this.room.getId());
        }

        return this.getDefinitionInfo(definitionItemId) != null;
    }

    public boolean updateVariableValue(int definitionItemId, int value) {
        this.ensurePersistentValuesLoaded();

        InteractionWiredExtra extra = this.getDefinitionExtra(definitionItemId);
        WiredVariableDefinitionInfo definitionInfo = this.getDefinitionInfo(definitionItemId);

        if (definitionInfo == null || definitionInfo.isReadOnly()) {
            return false;
        }

        Integer previousValue = definitionInfo.hasValue() ? this.getCurrentValue(definitionItemId) : null;

        if (extra instanceof WiredExtraVariableEcho) {
            boolean changed = ((WiredExtraVariableEcho) extra).updateValue(this.room, this.room.getId(), value);
            boolean shouldEmit = changed || (definitionInfo.hasValue() && previousValue != null && previousValue == value);

            if (shouldEmit) {
                Integer currentValue = definitionInfo.hasValue() ? this.getCurrentValue(definitionItemId) : null;
                this.emitVariableChangedEvents(extra, definitionInfo, previousValue, currentValue);
            }

            if (changed) {
                this.broadcastSnapshot();
            }

            return changed;
        }

        if (extra instanceof WiredExtraVariableReference) {
            boolean changed = WiredVariableReferenceSupport.updateSharedRoomVariable((WiredExtraVariableReference) extra, value);
            boolean shouldEmit = changed || (definitionInfo.hasValue() && previousValue != null && previousValue == value);

            if (shouldEmit) {
                Integer currentValue = definitionInfo.hasValue() ? this.getCurrentValue(definitionItemId) : null;
                this.emitVariableChangedEvents(extra, definitionInfo, previousValue, currentValue);
            }

            if (changed) {
                this.broadcastSnapshot();
            }

            return changed;
        }

        VariableAssignment assignment = this.activeAssignmentsByDefinitionId.get(definitionItemId);

        if (assignment == null) {
            assignment = new VariableAssignment(value, 0, Emulator.getIntUnixTimestamp());
            this.activeAssignmentsByDefinitionId.put(definitionItemId, assignment);
        } else if (assignment.getValue() == value) {
            this.emitVariableChangedEvents(extra, definitionInfo, previousValue, assignment.getValue());
            return false;
        } else {
            assignment.setValue(value, Emulator.getIntUnixTimestamp());
        }

        WiredExtraRoomVariable definition = (WiredExtraRoomVariable) extra;

        if (definition.isPermanentAvailability()) {
            this.upsertPersistentAssignment(definitionItemId, assignment);
        }

        if (definition.isSharedAvailability()) {
            WiredVariableReferenceSupport.cacheSharedRoomAssignment(this.room.getId(), definitionItemId, assignment.getValue(), assignment.getUpdatedAt());
        } else {
            WiredVariableReferenceSupport.clearSharedRoomDefinition(this.room.getId(), definitionItemId);
        }

        this.emitVariableChangedEvents(extra, definitionInfo, previousValue, assignment.getValue());
        this.broadcastSnapshot();
        return true;
    }

    public boolean removeVariable(int definitionItemId) {
        this.ensurePersistentValuesLoaded();

        if (definitionItemId <= 0) {
            return false;
        }

        WiredVariableDefinitionInfo definitionInfo = this.getDefinitionInfo(definitionItemId);
        if (definitionInfo == null || definitionInfo.isReadOnly()) {
            return false;
        }

        Integer previousValue = definitionInfo.hasValue() ? this.getCurrentValue(definitionItemId) : null;

        InteractionWiredExtra extra = this.getDefinitionExtra(definitionItemId);
        if (extra instanceof WiredExtraVariableEcho) {
            boolean changed = ((WiredExtraVariableEcho) extra).removeValue(this.room, this.room.getId());

            if (changed) {
                Integer currentValue = definitionInfo.hasValue() ? this.getCurrentValue(definitionItemId) : null;
                this.emitVariableChangedEvents(extra, definitionInfo, previousValue, currentValue);
            }

            if (changed) {
                this.broadcastSnapshot();
            }

            return changed;
        }

        if (extra instanceof WiredExtraVariableReference) {
            boolean changed = WiredVariableReferenceSupport.removeSharedRoomVariable((WiredExtraVariableReference) extra);

            if (changed) {
                Integer currentValue = definitionInfo.hasValue() ? this.getCurrentValue(definitionItemId) : null;
                this.emitVariableChangedEvents(extra, definitionInfo, previousValue, currentValue);
            }

            if (changed) {
                this.broadcastSnapshot();
            }

            return changed;
        }

        VariableAssignment removed = this.activeAssignmentsByDefinitionId.remove(definitionItemId);
        this.deletePersistentAssignment(definitionItemId);

        WiredExtraRoomVariable definition = this.getDefinition(definitionItemId);
        if (definition != null && definition.isSharedAvailability()) {
            WiredVariableReferenceSupport.clearSharedRoomDefinition(this.room.getId(), definitionItemId);
        }

        if (removed != null) {
            Integer currentValue = definitionInfo.hasValue() ? this.getCurrentValue(definitionItemId) : null;
            this.emitVariableChangedEvents(extra, definitionInfo, previousValue, currentValue);
            this.broadcastSnapshot();
            return true;
        }

        return false;
    }

    public void clearTransientAssignments() {
        this.ensurePersistentValuesLoaded();

        boolean changed = false;

        for (Integer definitionItemId : new ArrayList<>(this.activeAssignmentsByDefinitionId.keySet())) {
            WiredExtraRoomVariable definition = this.getDefinition(definitionItemId);

            if (definition != null && definition.isPermanentAvailability()) {
                continue;
            }

            if (this.activeAssignmentsByDefinitionId.remove(definitionItemId) != null) {
                changed = true;
            }
        }

        if (changed) {
            this.broadcastSnapshot();
        }
    }

    public void removeDefinition(int definitionItemId) {
        this.ensurePersistentValuesLoaded();
        this.activeAssignmentsByDefinitionId.remove(definitionItemId);
        this.deletePersistentAssignment(definitionItemId);
        WiredExtraRoomVariable definition = this.getDefinition(definitionItemId);
        if (definition != null && definition.isSharedAvailability()) {
            WiredVariableReferenceSupport.clearSharedRoomDefinition(this.room.getId(), definitionItemId);
        }
        this.broadcastSnapshot();
    }

    public void handleDefinitionUpdated(WiredExtraRoomVariable definition) {
        if (definition == null) {
            return;
        }

        this.ensurePersistentValuesLoaded();

        if (!definition.isPermanentAvailability()) {
            this.deletePersistentAssignment(definition.getId());
        } else {
            VariableAssignment assignment = this.activeAssignmentsByDefinitionId.get(definition.getId());

            if (assignment == null) {
                return;
            }

            this.upsertPersistentAssignment(definition.getId(), assignment);
        }

        if (definition.isSharedAvailability()) {
            VariableAssignment assignment = this.activeAssignmentsByDefinitionId.get(definition.getId());

            if (assignment != null) {
                WiredVariableReferenceSupport.cacheSharedRoomAssignment(this.room.getId(), definition.getId(), assignment.getValue(), assignment.getUpdatedAt());
            }
        } else {
            WiredVariableReferenceSupport.clearSharedRoomDefinition(this.room.getId(), definition.getId());
        }

        this.broadcastSnapshot();
    }

    public Snapshot createSnapshot() {
        this.ensurePersistentValuesLoaded();

        List<DefinitionEntry> definitions = new ArrayList<>();
        List<AssignmentEntry> assignments = new ArrayList<>();
        List<Integer> derivedDefinitionIds = new ArrayList<>();
        List<WiredExtraVariableEcho> roomEchoes = this.getRoomEchoes();

        for (WiredVariableDefinitionInfo definition : this.getAllDefinitionInfos()) {
            definitions.add(new DefinitionEntry(definition.getItemId(), definition.getName(), definition.hasValue(), definition.getAvailability(), definition.isTextConnected(), definition.isReadOnly()));

            if (WiredVariableLevelSystemSupport.resolveDerivedDefinition(this.room, WiredVariableLevelSystemSupport.TARGET_ROOM, definition.getItemId()) != null) {
                derivedDefinitionIds.add(definition.getItemId());
            }

            if (this.isReferenceDefinition(definition.getItemId())) {
                WiredExtraVariableReference reference = (WiredExtraVariableReference) this.getDefinitionExtra(definition.getItemId());
                WiredVariableReferenceSupport.SharedRoomAssignment assignment = WiredVariableReferenceSupport.getSharedRoomAssignment(reference);
                assignments.add(new AssignmentEntry(definition.getItemId(), (assignment != null) ? assignment.getValue() : 0, 0, (assignment != null) ? assignment.getUpdatedAt() : 0));
                continue;
            }

            if (derivedDefinitionIds.contains(definition.getItemId())) {
                assignments.add(new AssignmentEntry(
                    definition.getItemId(),
                    this.getCurrentValue(definition.getItemId()),
                    this.getCreatedAt(definition.getItemId()),
                    this.getUpdatedAt(definition.getItemId())
                ));
                continue;
            }

            if (roomEchoes.stream().anyMatch(echo -> echo.getId() == definition.getItemId())) {
                assignments.add(new AssignmentEntry(
                    definition.getItemId(),
                    this.getCurrentValue(definition.getItemId()),
                    this.getCreatedAt(definition.getItemId()),
                    this.getUpdatedAt(definition.getItemId())
                ));
                continue;
            }

            VariableAssignment assignment = this.activeAssignmentsByDefinitionId.get(definition.getItemId());
            assignments.add(new AssignmentEntry(definition.getItemId(), (assignment != null) ? assignment.getValue() : 0, 0, (assignment != null) ? assignment.getUpdatedAt() : 0));
        }

        assignments.sort(Comparator.comparingInt(AssignmentEntry::getVariableItemId));

        return new Snapshot(this.room.getId(), definitions, assignments);
    }

    public void sendSnapshot(Habbo habbo) {
        if (habbo == null || habbo.getClient() == null || !this.room.canInspectWired(habbo)) {
            return;
        }

        habbo.getClient().sendResponse(new WiredUserVariablesDataComposer(this.room.getUserVariableManager().createSnapshot(), this.room.getFurniVariableManager().createSnapshot(), this.createSnapshot()));
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
        RoomUserVariableManager.Snapshot userSnapshot = this.room.getUserVariableManager().createSnapshot();
        RoomFurniVariableManager.Snapshot furniSnapshot = this.room.getFurniVariableManager().createSnapshot();
        Snapshot roomSnapshot = this.createSnapshot();

        for (Habbo habbo : this.room.getCurrentHabbos().values()) {
            if (habbo == null || habbo.getClient() == null || !this.room.canInspectWired(habbo)) {
                continue;
            }

            habbo.getClient().sendResponse(new WiredUserVariablesDataComposer(userSnapshot, furniSnapshot, roomSnapshot));
        }
    }

    public Collection<WiredExtraRoomVariable> getDefinitions() {
        if (this.room.getRoomSpecialTypes() == null) {
            return Collections.emptyList();
        }

        THashSet<InteractionWiredExtra> extras = this.room.getRoomSpecialTypes().getExtras();
        List<WiredExtraRoomVariable> result = new ArrayList<>();

        for (InteractionWiredExtra extra : extras) {
            if (extra instanceof WiredExtraRoomVariable) {
                WiredExtraRoomVariable definition = (WiredExtraRoomVariable) extra;

                if (!hasVisibleDefinitionName(definition.getVariableName())) {
                    continue;
                }

                result.add(definition);
            }
        }

        result.sort(Comparator.comparing(WiredExtraRoomVariable::getVariableName, String.CASE_INSENSITIVE_ORDER).thenComparingInt(WiredExtraRoomVariable::getId));
        return result;
    }

    public Collection<WiredVariableDefinitionInfo> getAllDefinitionInfos() {
        List<WiredVariableDefinitionInfo> result = new ArrayList<>();
        List<WiredVariableDefinitionInfo> baseDefinitions = new ArrayList<>();

        for (WiredExtraRoomVariable definition : this.getDefinitions()) {
            baseDefinitions.add(new WiredVariableDefinitionInfo(
                definition.getId(),
                definition.getVariableName(),
                definition.hasValue(),
                definition.getAvailability(),
                WiredVariableTextConnectorSupport.isTextConnected(this.room, definition),
                false
            ));
        }

        for (WiredExtraVariableReference reference : this.getRoomReferences()) {
            baseDefinitions.add(new WiredVariableDefinitionInfo(reference.getId(), reference.getVariableName(), reference.hasValue(), reference.getAvailability(), false, reference.isReadOnly()));
        }

        for (WiredExtraVariableEcho echo : this.getRoomEchoes()) {
            baseDefinitions.add(echo.createDefinitionInfo(this.room));
        }

        result.addAll(baseDefinitions);

        for (WiredVariableDefinitionInfo definition : baseDefinitions) {
            result.addAll(WiredVariableLevelSystemSupport.getDerivedDefinitions(this.room, WiredVariableLevelSystemSupport.TARGET_ROOM, this.getDefinitionExtra(definition.getItemId()), definition));
        }

        result.sort(Comparator.comparing(WiredVariableDefinitionInfo::getName, String.CASE_INSENSITIVE_ORDER).thenComparingInt(WiredVariableDefinitionInfo::getItemId));
        return result;
    }

    public WiredVariableDefinitionInfo getDefinitionInfo(int definitionItemId) {
        InteractionWiredExtra extra = this.getDefinitionExtra(definitionItemId);

        if (extra instanceof WiredExtraRoomVariable) {
            WiredExtraRoomVariable definition = (WiredExtraRoomVariable) extra;

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

        if (extra instanceof WiredExtraVariableReference && ((WiredExtraVariableReference) extra).isRoomReference()) {
            WiredExtraVariableReference reference = (WiredExtraVariableReference) extra;

            if (!hasVisibleDefinitionName(reference.getVariableName())) {
                return null;
            }

            return new WiredVariableDefinitionInfo(reference.getId(), reference.getVariableName(), reference.hasValue(), reference.getAvailability(), false, reference.isReadOnly());
        }

        if (extra instanceof WiredExtraVariableEcho && ((WiredExtraVariableEcho) extra).isRoomEcho()) {
            WiredVariableDefinitionInfo info = ((WiredExtraVariableEcho) extra).createDefinitionInfo(this.room);
            return (info != null && hasVisibleDefinitionName(info.getName())) ? info : null;
        }

        return WiredVariableLevelSystemSupport.getDerivedDefinitionInfo(this.room, WiredVariableLevelSystemSupport.TARGET_ROOM, definitionItemId);
    }

    private WiredExtraRoomVariable getDefinition(int definitionItemId) {
        InteractionWiredExtra extra = this.getDefinitionExtra(definitionItemId);

        if (!(extra instanceof WiredExtraRoomVariable)) {
            return null;
        }

        return (WiredExtraRoomVariable) extra;
    }

    private InteractionWiredExtra getDefinitionExtra(int definitionItemId) {
        if (this.room.getRoomSpecialTypes() == null || definitionItemId <= 0) {
            return null;
        }

        return this.room.getRoomSpecialTypes().getExtra(definitionItemId);
    }

    private boolean isReferenceDefinition(int definitionItemId) {
        InteractionWiredExtra extra = this.getDefinitionExtra(definitionItemId);
        return extra instanceof WiredExtraVariableReference && ((WiredExtraVariableReference) extra).isRoomReference();
    }

    private List<WiredExtraVariableReference> getRoomReferences() {
        if (this.room.getRoomSpecialTypes() == null) {
            return Collections.emptyList();
        }

        List<WiredExtraVariableReference> result = new ArrayList<>();

        for (InteractionWiredExtra extra : this.room.getRoomSpecialTypes().getExtras()) {
            if (extra instanceof WiredExtraVariableReference && ((WiredExtraVariableReference) extra).isRoomReference()) {
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

    private List<WiredExtraVariableEcho> getRoomEchoes() {
        if (this.room.getRoomSpecialTypes() == null) {
            return Collections.emptyList();
        }

        List<WiredExtraVariableEcho> result = new ArrayList<>();

        for (InteractionWiredExtra extra : this.room.getRoomSpecialTypes().getExtras()) {
            if (extra instanceof WiredExtraVariableEcho && ((WiredExtraVariableEcho) extra).isRoomEcho()) {
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

    private VariableAssignment getRawAssignment(int definitionItemId) {
        if (definitionItemId <= 0) {
            return null;
        }

        InteractionWiredExtra extra = this.getDefinitionExtra(definitionItemId);
        if (extra instanceof WiredExtraVariableReference) {
            WiredVariableReferenceSupport.SharedRoomAssignment assignment = WiredVariableReferenceSupport.getSharedRoomAssignment((WiredExtraVariableReference) extra);
            return (assignment != null) ? new VariableAssignment(assignment.getValue(), 0, assignment.getUpdatedAt()) : null;
        }

        if (extra instanceof WiredExtraVariableEcho) {
            WiredExtraVariableEcho echo = (WiredExtraVariableEcho) extra;
            if (!echo.hasVariable(this.room, this.room.getId())) {
                return null;
            }

            return new VariableAssignment(echo.getCurrentValue(this.room, this.room.getId()), echo.getCreatedAt(this.room, this.room.getId()), echo.getUpdatedAt(this.room, this.room.getId()));
        }

        return this.activeAssignmentsByDefinitionId.get(definitionItemId);
    }

    private Integer getRawValue(int definitionItemId) {
        VariableAssignment assignment = this.getRawAssignment(definitionItemId);
        return (assignment != null) ? assignment.getValue() : null;
    }

    private void emitVariableChangedEvents(InteractionWiredExtra definitionExtra, WiredVariableDefinitionInfo definitionInfo, Integer previousValue, Integer currentValue) {
        if (definitionInfo == null) {
            return;
        }

        this.emitVariableChangedEvent(definitionInfo.getItemId(), definitionInfo.hasValue(), previousValue, currentValue);

        for (WiredVariableDefinitionInfo derivedDefinition : WiredVariableLevelSystemSupport.getDerivedDefinitions(this.room, WiredVariableLevelSystemSupport.TARGET_ROOM, definitionExtra, definitionInfo)) {
            WiredVariableLevelSystemSupport.DerivedDefinition resolvedDefinition = WiredVariableLevelSystemSupport.resolveDerivedDefinition(this.room, WiredVariableLevelSystemSupport.TARGET_ROOM, derivedDefinition.getItemId());

            if (resolvedDefinition == null) {
                continue;
            }

            Integer derivedPreviousValue = WiredVariableLevelSystemSupport.getDerivedValue(resolvedDefinition.getLevelSystem(), resolvedDefinition.getSubvariableType(), previousValue);
            Integer derivedCurrentValue = WiredVariableLevelSystemSupport.getDerivedValue(resolvedDefinition.getLevelSystem(), resolvedDefinition.getSubvariableType(), currentValue);

            this.emitVariableChangedEvent(derivedDefinition.getItemId(), true, derivedPreviousValue, derivedCurrentValue);
        }
    }

    private void emitVariableChangedEvent(int definitionItemId, boolean hasValue, Integer previousValue, Integer currentValue) {
        WiredEvent.VariableChangeKind changeKind = resolveVariableChangeKind(hasValue, previousValue, currentValue);

        if (changeKind == WiredEvent.VariableChangeKind.NONE) {
            return;
        }

        WiredManager.triggerRoomVariableChanged(this.room, definitionItemId, changeKind);
    }

    private static WiredEvent.VariableChangeKind resolveVariableChangeKind(boolean hasValue, Integer previousValue, Integer currentValue) {
        if (!hasValue) {
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

    private void upsertPersistentAssignment(int definitionItemId, VariableAssignment assignment) {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement("INSERT INTO room_wired_variables (room_id, variable_item_id, value, created_at, updated_at) VALUES (?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE value = VALUES(value), updated_at = VALUES(updated_at)")) {
            statement.setInt(1, this.room.getId());
            statement.setInt(2, definitionItemId);
            statement.setInt(3, (assignment != null) ? assignment.getValue() : 0);

            int now = Emulator.getIntUnixTimestamp();
            statement.setInt(4, 0);
            statement.setInt(5, (assignment != null) ? assignment.getUpdatedAt() : now);
            statement.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("Failed to store permanent wired room variable for room {} and item {}", this.room.getId(), definitionItemId, e);
        }
    }

    private void deletePersistentAssignment(int definitionItemId) {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM room_wired_variables WHERE room_id = ? AND variable_item_id = ?")) {
            statement.setInt(1, this.room.getId());
            statement.setInt(2, definitionItemId);
            statement.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("Failed to delete permanent wired room variable for room {} and item {}", this.room.getId(), definitionItemId, e);
        }
    }

    private static int normalizeTimestamp(int value, int fallback) {
        if (value > 0) {
            return value;
        }

        if (fallback > 0) {
            return fallback;
        }
        return 0;
    }

    private static boolean hasVisibleDefinitionName(String name) {
        return name != null && !name.trim().isEmpty();
    }

    public static class Snapshot {
        private final int roomId;
        private final List<DefinitionEntry> definitions;
        private final List<AssignmentEntry> assignments;

        public Snapshot(int roomId, List<DefinitionEntry> definitions, List<AssignmentEntry> assignments) {
            this.roomId = roomId;
            this.definitions = definitions;
            this.assignments = assignments;
        }

        public int getRoomId() {
            return this.roomId;
        }

        public List<DefinitionEntry> getDefinitions() {
            return this.definitions;
        }

        public List<AssignmentEntry> getAssignments() {
            return this.assignments;
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
            return this.itemId;
        }

        public String getName() {
            return this.name;
        }

        public boolean hasValue() {
            return this.hasValue;
        }

        public int getAvailability() {
            return this.availability;
        }

        public boolean isTextConnected() {
            return this.textConnected;
        }

        public boolean isReadOnly() {
            return this.readOnly;
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
            return this.variableItemId;
        }

        public Integer getValue() {
            return this.value;
        }

        public boolean hasValue() {
            return this.value != null;
        }

        public int getCreatedAt() {
            return this.createdAt;
        }

        public int getUpdatedAt() {
            return this.updatedAt;
        }
    }

    private static class VariableAssignment {
        private int value;
        private final int createdAt;
        private int updatedAt;

        public VariableAssignment(int value, int createdAt, int updatedAt) {
            this.value = value;
            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
        }

        public int getValue() {
            return this.value;
        }

        public void setValue(int value, int updatedAt) {
            this.value = value;
            this.updatedAt = updatedAt;
        }

        public int getCreatedAt() {
            return this.createdAt;
        }

        public int getUpdatedAt() {
            return this.updatedAt;
        }
    }
}
