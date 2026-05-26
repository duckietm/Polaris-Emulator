package com.eu.habbo.habbohotel.rooms;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraVariableEcho;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraFurniVariable;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
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

public class RoomFurniVariableManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(RoomFurniVariableManager.class);

    private final Room room;
    private final ConcurrentHashMap<Integer, ConcurrentHashMap<Integer, VariableAssignment>> activeAssignmentsByFurniId;
    private volatile boolean permanentAssignmentsLoaded;
    private final java.util.concurrent.atomic.AtomicBoolean broadcastRequested = new java.util.concurrent.atomic.AtomicBoolean(false);

    public RoomFurniVariableManager(Room room) {
        this.room = room;
        this.activeAssignmentsByFurniId = new ConcurrentHashMap<>();
        this.permanentAssignmentsLoaded = false;
    }

    public void ensurePermanentAssignmentsLoaded() {
        if (this.permanentAssignmentsLoaded) {
            return;
        }

        synchronized (this) {
            if (this.permanentAssignmentsLoaded) {
                return;
            }

            List<int[]> staleRows = new ArrayList<>();

            try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                 PreparedStatement statement = connection.prepareStatement("SELECT furni_id, variable_item_id, value, created_at, updated_at FROM room_furni_wired_variables WHERE room_id = ?")) {
                statement.setInt(1, this.room.getId());

                try (ResultSet set = statement.executeQuery()) {
                    while (set.next()) {
                        int furniId = set.getInt("furni_id");
                        int definitionItemId = set.getInt("variable_item_id");
                        HabboItem furni = this.room.getHabboItem(furniId);
                        WiredExtraFurniVariable definition = this.getDefinition(definitionItemId);

                        if (furni == null || definition == null || !definition.isPermanentAvailability()) {
                            staleRows.add(new int[]{furniId, definitionItemId});
                            continue;
                        }

                        Integer value = null;
                        int rawValue = set.getInt("value");
                        if (!set.wasNull()) {
                            value = rawValue;
                        }

                        int createdAt = normalizeTimestamp(set.getInt("created_at"), 0);
                        int updatedAt = normalizeTimestamp(set.getInt("updated_at"), createdAt);

                        this.activeAssignmentsByFurniId
                            .computeIfAbsent(furniId, key -> new ConcurrentHashMap<>())
                            .put(definitionItemId, new VariableAssignment(value, createdAt, updatedAt));
                    }
                }
            } catch (SQLException e) {
                LOGGER.error("Failed to restore wired furni variables for room {}", this.room.getId(), e);
            }

            for (int[] staleRow : staleRows) {
                this.deletePersistentAssignment(staleRow[0], staleRow[1]);
            }

            this.permanentAssignmentsLoaded = true;
        }
    }

    public boolean assignVariable(HabboItem furni, WiredExtraFurniVariable definition, Integer value, boolean overrideExisting) {
        return definition != null && this.assignVariable(furni, definition.getId(), value, overrideExisting);
    }

    public boolean assignVariable(HabboItem furni, int definitionItemId, Integer value, boolean overrideExisting) {
        if (furni == null || definitionItemId <= 0) {
            return false;
        }

        WiredVariableDefinitionInfo definitionInfo = this.getDefinitionInfo(definitionItemId);
        if (definitionInfo == null || definitionInfo.isReadOnly()) {
            return false;
        }

        InteractionWiredExtra extra = this.getDefinitionExtra(definitionItemId);
        boolean hadBefore = this.hasVariable(furni.getId(), definitionItemId);
        Integer previousValue = (definitionInfo.hasValue() && hadBefore) ? this.getCurrentValue(furni.getId(), definitionItemId) : null;

        if (extra instanceof WiredExtraVariableEcho) {
            boolean changed = ((WiredExtraVariableEcho) extra).assignValue(this.room, furni.getId(), definitionInfo.hasValue() ? value : null, overrideExisting);
            boolean shouldEmit = changed || (definitionInfo.hasValue() && hadBefore && overrideExisting && Objects.equals(previousValue, value));

            if (shouldEmit) {
                boolean hasAfter = this.hasVariable(furni.getId(), definitionItemId);
                Integer currentValue = (definitionInfo.hasValue() && hasAfter) ? this.getCurrentValue(furni.getId(), definitionItemId) : null;
                this.emitVariableChangedEvents(furni.getId(), extra, definitionInfo, hadBefore, previousValue, hasAfter, currentValue);
            }

            if (changed) {
                this.broadcastSnapshot();
            }

            return changed;
        }

        WiredExtraFurniVariable definition = this.getDefinition(definitionItemId);
        if (definition == null) {
            return false;
        }

        this.ensurePermanentAssignmentsLoaded();

        int furniId = furni.getId();
        Integer normalizedValue = definition.hasValue() ? value : null;
        ConcurrentHashMap<Integer, VariableAssignment> assignments = this.activeAssignmentsByFurniId.computeIfAbsent(furniId, key -> new ConcurrentHashMap<>());
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

        if (definition.isPermanentAvailability()) {
            this.upsertPersistentAssignment(furniId, definitionItemId, assignments.get(definitionItemId));
        } else {
            this.deletePersistentAssignment(furniId, definitionItemId);
        }

        if (changed || (definitionInfo.hasValue() && hadBefore && overrideExisting && Objects.equals(previousValue, normalizedValue))) {
            boolean hasAfter = this.hasVariable(furniId, definitionItemId);
            Integer currentValue = (definitionInfo.hasValue() && hasAfter) ? this.getCurrentValue(furniId, definitionItemId) : null;
            this.emitVariableChangedEvents(furniId, extra, definitionInfo, hadBefore, previousValue, hasAfter, currentValue);
        }

        if (changed) {
            this.broadcastSnapshot();
        }

        return changed;
    }

    public boolean updateVariableValue(int furniId, int definitionItemId, Integer value) {
        this.ensurePermanentAssignmentsLoaded();

        WiredVariableDefinitionInfo definitionInfo = this.getDefinitionInfo(definitionItemId);
        if (definitionInfo == null || !definitionInfo.hasValue() || definitionInfo.isReadOnly()) {
            return false;
        }

        boolean hadBefore = this.hasVariable(furniId, definitionItemId);
        Integer previousValue = hadBefore ? this.getCurrentValue(furniId, definitionItemId) : null;

        InteractionWiredExtra extra = this.getDefinitionExtra(definitionItemId);
        if (extra instanceof WiredExtraVariableEcho) {
            boolean changed = ((WiredExtraVariableEcho) extra).updateValue(this.room, furniId, value);
            boolean shouldEmit = changed || (hadBefore && Objects.equals(previousValue, value));

            if (shouldEmit) {
                boolean hasAfter = this.hasVariable(furniId, definitionItemId);
                Integer currentValue = hasAfter ? this.getCurrentValue(furniId, definitionItemId) : null;
                this.emitVariableChangedEvents(furniId, extra, definitionInfo, hadBefore, previousValue, hasAfter, currentValue);
            }

            if (changed) {
                this.broadcastSnapshot();
            }

            return changed;
        }

        WiredExtraFurniVariable definition = this.getDefinition(definitionItemId);
        if (definition == null) {
            return false;
        }

        ConcurrentHashMap<Integer, VariableAssignment> assignments = this.activeAssignmentsByFurniId.get(furniId);

        if (assignments == null) {
            return false;
        }

        VariableAssignment assignment = assignments.get(definitionItemId);

        if (assignment == null) {
            return false;
        }

        if (Objects.equals(assignment.getValue(), value)) {
            this.emitVariableChangedEvents(furniId, extra, definitionInfo, true, previousValue, true, assignment.getValue());
            return false;
        }

        assignment.setValue(value, Emulator.getIntUnixTimestamp());

        if (definition.isPermanentAvailability()) {
            this.upsertPersistentAssignment(furniId, definitionItemId, assignment);
        }

        this.emitVariableChangedEvents(furniId, extra, definitionInfo, hadBefore, previousValue, true, assignment.getValue());
        this.broadcastSnapshot();
        return true;
    }

    public int getCurrentValue(int furniId, int definitionItemId) {
        this.ensurePermanentAssignmentsLoaded();

        if (furniId <= 0 || definitionItemId <= 0) {
            return 0;
        }

        WiredVariableLevelSystemSupport.DerivedDefinition derivedDefinition = WiredVariableLevelSystemSupport.resolveDerivedDefinition(this.room, WiredVariableLevelSystemSupport.TARGET_FURNI, definitionItemId);
        if (derivedDefinition != null) {
            Integer baseValue = this.getRawValue(furniId, derivedDefinition.getBaseDefinitionItemId());
            Integer derivedValue = WiredVariableLevelSystemSupport.getDerivedValue(derivedDefinition.getLevelSystem(), derivedDefinition.getSubvariableType(), baseValue);
            return (derivedValue != null) ? derivedValue : 0;
        }

        InteractionWiredExtra extra = this.getDefinitionExtra(definitionItemId);
        if (extra instanceof WiredExtraVariableEcho) {
            return ((WiredExtraVariableEcho) extra).getCurrentValue(this.room, furniId);
        }

        ConcurrentHashMap<Integer, VariableAssignment> assignments = this.activeAssignmentsByFurniId.get(furniId);

        if (assignments == null) {
            return 0;
        }

        VariableAssignment assignment = assignments.get(definitionItemId);

        if (assignment == null || assignment.getValue() == null) {
            return 0;
        }

        return assignment.getValue();
    }

    public int getCreatedAt(int furniId, int definitionItemId) {
        this.ensurePermanentAssignmentsLoaded();

        if (furniId <= 0 || definitionItemId <= 0) {
            return 0;
        }

        WiredVariableLevelSystemSupport.DerivedDefinition derivedDefinition = WiredVariableLevelSystemSupport.resolveDerivedDefinition(this.room, WiredVariableLevelSystemSupport.TARGET_FURNI, definitionItemId);
        if (derivedDefinition != null) {
            VariableAssignment assignment = this.getRawAssignment(furniId, derivedDefinition.getBaseDefinitionItemId());
            return (assignment != null) ? assignment.getCreatedAt() : 0;
        }

        InteractionWiredExtra extra = this.getDefinitionExtra(definitionItemId);
        if (extra instanceof WiredExtraVariableEcho) {
            return ((WiredExtraVariableEcho) extra).getCreatedAt(this.room, furniId);
        }

        ConcurrentHashMap<Integer, VariableAssignment> assignments = this.activeAssignmentsByFurniId.get(furniId);

        if (assignments == null) {
            return 0;
        }

        VariableAssignment assignment = assignments.get(definitionItemId);
        return assignment != null ? assignment.getCreatedAt() : 0;
    }

    public int getUpdatedAt(int furniId, int definitionItemId) {
        this.ensurePermanentAssignmentsLoaded();

        if (furniId <= 0 || definitionItemId <= 0) {
            return 0;
        }

        WiredVariableLevelSystemSupport.DerivedDefinition derivedDefinition = WiredVariableLevelSystemSupport.resolveDerivedDefinition(this.room, WiredVariableLevelSystemSupport.TARGET_FURNI, definitionItemId);
        if (derivedDefinition != null) {
            VariableAssignment assignment = this.getRawAssignment(furniId, derivedDefinition.getBaseDefinitionItemId());
            return (assignment != null) ? assignment.getUpdatedAt() : 0;
        }

        InteractionWiredExtra extra = this.getDefinitionExtra(definitionItemId);
        if (extra instanceof WiredExtraVariableEcho) {
            return ((WiredExtraVariableEcho) extra).getUpdatedAt(this.room, furniId);
        }

        ConcurrentHashMap<Integer, VariableAssignment> assignments = this.activeAssignmentsByFurniId.get(furniId);

        if (assignments == null) {
            return 0;
        }

        VariableAssignment assignment = assignments.get(definitionItemId);
        return assignment != null ? assignment.getUpdatedAt() : 0;
    }

    public boolean hasVariable(int furniId, int definitionItemId) {
        this.ensurePermanentAssignmentsLoaded();

        if (furniId <= 0 || definitionItemId <= 0) {
            return false;
        }

        WiredVariableLevelSystemSupport.DerivedDefinition derivedDefinition = WiredVariableLevelSystemSupport.resolveDerivedDefinition(this.room, WiredVariableLevelSystemSupport.TARGET_FURNI, definitionItemId);
        if (derivedDefinition != null) {
            return this.getRawAssignment(furniId, derivedDefinition.getBaseDefinitionItemId()) != null;
        }

        InteractionWiredExtra extra = this.getDefinitionExtra(definitionItemId);
        if (extra instanceof WiredExtraVariableEcho) {
            return ((WiredExtraVariableEcho) extra).hasVariable(this.room, furniId);
        }

        ConcurrentHashMap<Integer, VariableAssignment> assignments = this.activeAssignmentsByFurniId.get(furniId);

        return assignments != null && assignments.containsKey(definitionItemId);
    }

    public boolean removeVariable(int furniId, int definitionItemId) {
        this.ensurePermanentAssignmentsLoaded();

        if (furniId <= 0 || definitionItemId <= 0) {
            return false;
        }

        WiredVariableDefinitionInfo definitionInfo = this.getDefinitionInfo(definitionItemId);
        if (definitionInfo == null || definitionInfo.isReadOnly()) {
            return false;
        }

        boolean hadBefore = this.hasVariable(furniId, definitionItemId);
        Integer previousValue = (definitionInfo.hasValue() && hadBefore) ? this.getCurrentValue(furniId, definitionItemId) : null;

        InteractionWiredExtra extra = this.getDefinitionExtra(definitionItemId);
        if (extra instanceof WiredExtraVariableEcho) {
            boolean changed = ((WiredExtraVariableEcho) extra).removeValue(this.room, furniId);

            if (changed) {
                boolean hasAfter = this.hasVariable(furniId, definitionItemId);
                Integer currentValue = (definitionInfo.hasValue() && hasAfter) ? this.getCurrentValue(furniId, definitionItemId) : null;
                this.emitVariableChangedEvents(furniId, extra, definitionInfo, hadBefore, previousValue, hasAfter, currentValue);
            }

            if (changed) {
                this.broadcastSnapshot();
            }

            return changed;
        }

        ConcurrentHashMap<Integer, VariableAssignment> assignments = this.activeAssignmentsByFurniId.get(furniId);

        if (assignments == null) {
            return false;
        }

        if (assignments.remove(definitionItemId) == null) {
            return false;
        }

        if (assignments.isEmpty()) {
            this.activeAssignmentsByFurniId.remove(furniId, assignments);
        }

        this.deletePersistentAssignment(furniId, definitionItemId);
        this.emitVariableChangedEvents(furniId, extra, definitionInfo, hadBefore, previousValue, false, null);
        this.broadcastSnapshot();

        return true;
    }

    public void removeAssignmentsForFurni(int furniId) {
        this.ensurePermanentAssignmentsLoaded();

        if (furniId <= 0) {
            return;
        }

        boolean changed = (this.activeAssignmentsByFurniId.remove(furniId) != null);
        this.deletePersistentAssignmentsForFurni(furniId);

        if (changed) {
            this.broadcastSnapshot();
        }
    }

    public void clearTransientAssignments() {
        this.ensurePermanentAssignmentsLoaded();

        boolean changed = false;

        for (Map.Entry<Integer, ConcurrentHashMap<Integer, VariableAssignment>> entry : this.activeAssignmentsByFurniId.entrySet()) {
            ConcurrentHashMap<Integer, VariableAssignment> assignments = entry.getValue();

            for (Integer definitionItemId : new ArrayList<>(assignments.keySet())) {
                WiredExtraFurniVariable definition = this.getDefinition(definitionItemId);

                if (definition != null && definition.isPermanentAvailability()) {
                    continue;
                }

                if (assignments.remove(definitionItemId) != null) {
                    changed = true;
                }
            }

            if (assignments.isEmpty()) {
                this.activeAssignmentsByFurniId.remove(entry.getKey(), assignments);
            }
        }

        if (changed) {
            this.broadcastSnapshot();
        }
    }

    public void removeDefinition(int definitionItemId) {
        this.ensurePermanentAssignmentsLoaded();

        boolean changed = false;

        for (Map.Entry<Integer, ConcurrentHashMap<Integer, VariableAssignment>> entry : this.activeAssignmentsByFurniId.entrySet()) {
            ConcurrentHashMap<Integer, VariableAssignment> assignments = entry.getValue();

            if (assignments.remove(definitionItemId) != null) {
                changed = true;
            }

            if (assignments.isEmpty()) {
                this.activeAssignmentsByFurniId.remove(entry.getKey(), assignments);
            }
        }

        this.deletePersistentAssignmentsForDefinition(definitionItemId);
        this.broadcastSnapshot();
    }

    public void handleDefinitionUpdated(WiredExtraFurniVariable definition) {
        if (definition == null) {
            return;
        }

        this.ensurePermanentAssignmentsLoaded();

        if (!definition.isPermanentAvailability()) {
            this.deletePersistentAssignmentsForDefinition(definition.getId());
        } else {
            for (Map.Entry<Integer, ConcurrentHashMap<Integer, VariableAssignment>> entry : this.activeAssignmentsByFurniId.entrySet()) {
                VariableAssignment assignment = entry.getValue().get(definition.getId());

                if (assignment == null) continue;

                this.upsertPersistentAssignment(entry.getKey(), definition.getId(), assignment);
            }
        }

        this.broadcastSnapshot();
    }

    public Snapshot createSnapshot() {
        this.ensurePermanentAssignmentsLoaded();

        List<DefinitionEntry> definitions = new ArrayList<>();
        Map<Integer, DefinitionEntry> definitionsById = new LinkedHashMap<>();
        List<Integer> derivedDefinitionIds = new ArrayList<>();
        List<WiredExtraVariableEcho> furniEchoes = this.getFurniEchoes();

        for (WiredVariableDefinitionInfo definition : this.getAllDefinitionInfos()) {
            DefinitionEntry entry = new DefinitionEntry(definition.getItemId(), definition.getName(), definition.hasValue(), definition.getAvailability(), definition.isTextConnected(), definition.isReadOnly());
            definitions.add(entry);
            definitionsById.put(entry.getItemId(), entry);

            if (WiredVariableLevelSystemSupport.resolveDerivedDefinition(this.room, WiredVariableLevelSystemSupport.TARGET_FURNI, definition.getItemId()) != null) {
                derivedDefinitionIds.add(definition.getItemId());
            }
        }

        List<FurniAssignmentsEntry> furnis = new ArrayList<>();
        THashSet<Integer> furniIds = new THashSet<>();
        furniIds.addAll(this.activeAssignmentsByFurniId.keySet());

        for (HabboItem item : this.room.getFloorItems()) {
            if (item != null) furniIds.add(item.getId());
        }

        for (HabboItem item : this.room.getWallItems()) {
            if (item != null) furniIds.add(item.getId());
        }

        for (Integer furniId : furniIds) {
            if (this.room.getHabboItem(furniId) == null) {
                continue;
            }

            List<AssignmentEntry> assignments = new ArrayList<>();
            ConcurrentHashMap<Integer, VariableAssignment> localAssignments = this.activeAssignmentsByFurniId.get(furniId);

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

            for (WiredExtraVariableEcho echo : furniEchoes) {
                if (!definitionsById.containsKey(echo.getId()) || !echo.hasVariable(this.room, furniId)) {
                    continue;
                }

                assignments.add(new AssignmentEntry(
                    echo.getId(),
                    echo.getCurrentValue(this.room, furniId),
                    echo.getCreatedAt(this.room, furniId),
                    echo.getUpdatedAt(this.room, furniId)
                ));
            }

            for (Integer derivedDefinitionId : derivedDefinitionIds) {
                if (!this.hasVariable(furniId, derivedDefinitionId)) {
                    continue;
                }

                assignments.add(new AssignmentEntry(
                    derivedDefinitionId,
                    this.getCurrentValue(furniId, derivedDefinitionId),
                    this.getCreatedAt(furniId, derivedDefinitionId),
                    this.getUpdatedAt(furniId, derivedDefinitionId)
                ));
            }

            assignments.sort(Comparator.comparingInt(AssignmentEntry::getVariableItemId));

            if (!assignments.isEmpty()) {
                furnis.add(new FurniAssignmentsEntry(furniId, assignments));
            }
        }

        furnis.sort(Comparator.comparingInt(FurniAssignmentsEntry::getFurniId));

        return new Snapshot(this.room.getId(), definitions, furnis);
    }

    public void sendSnapshot(Habbo habbo) {
        if (habbo == null || habbo.getClient() == null || !this.room.canInspectWired(habbo)) {
            return;
        }

        habbo.getClient().sendResponse(new WiredUserVariablesDataComposer(this.room.getUserVariableManager().createSnapshot(), this.createSnapshot(), this.room.getRoomVariableManager().createSnapshot()));
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
        Snapshot furniSnapshot = this.createSnapshot();
        RoomVariableManager.Snapshot roomSnapshot = this.room.getRoomVariableManager().createSnapshot();

        for (Habbo habbo : this.room.getCurrentHabbos().values()) {
            if (habbo == null || habbo.getClient() == null || !this.room.canInspectWired(habbo)) {
                continue;
            }

            habbo.getClient().sendResponse(new WiredUserVariablesDataComposer(userSnapshot, furniSnapshot, roomSnapshot));
        }
    }

    public Collection<WiredExtraFurniVariable> getDefinitions() {
        if (this.room.getRoomSpecialTypes() == null) {
            return Collections.emptyList();
        }

        THashSet<InteractionWiredExtra> extras = this.room.getRoomSpecialTypes().getExtras();
        List<WiredExtraFurniVariable> result = new ArrayList<>();

        for (InteractionWiredExtra extra : extras) {
            if (extra instanceof WiredExtraFurniVariable) {
                WiredExtraFurniVariable definition = (WiredExtraFurniVariable) extra;

                if (!hasVisibleDefinitionName(definition.getVariableName())) {
                    continue;
                }

                result.add(definition);
            }
        }

        result.sort(Comparator.comparing(WiredExtraFurniVariable::getVariableName, String.CASE_INSENSITIVE_ORDER).thenComparingInt(WiredExtraFurniVariable::getId));
        return result;
    }

    public Collection<WiredVariableDefinitionInfo> getAllDefinitionInfos() {
        List<WiredVariableDefinitionInfo> result = new ArrayList<>();
        List<WiredVariableDefinitionInfo> baseDefinitions = new ArrayList<>();

        for (WiredExtraFurniVariable definition : this.getDefinitions()) {
            baseDefinitions.add(new WiredVariableDefinitionInfo(
                definition.getId(),
                definition.getVariableName(),
                definition.hasValue(),
                definition.getAvailability(),
                WiredVariableTextConnectorSupport.isTextConnected(this.room, definition),
                false
            ));
        }

        for (WiredExtraVariableEcho echo : this.getFurniEchoes()) {
            baseDefinitions.add(echo.createDefinitionInfo(this.room));
        }

        result.addAll(baseDefinitions);

        for (WiredVariableDefinitionInfo definition : baseDefinitions) {
            result.addAll(WiredVariableLevelSystemSupport.getDerivedDefinitions(this.room, WiredVariableLevelSystemSupport.TARGET_FURNI, this.getDefinitionExtra(definition.getItemId()), definition));
        }

        result.sort(Comparator.comparing(WiredVariableDefinitionInfo::getName, String.CASE_INSENSITIVE_ORDER).thenComparingInt(WiredVariableDefinitionInfo::getItemId));
        return result;
    }

    public boolean hasDefinition(int definitionItemId) {
        return this.getDefinitionInfo(definitionItemId) != null;
    }

    public WiredVariableDefinitionInfo getDefinitionInfo(int definitionItemId) {
        InteractionWiredExtra extra = this.getDefinitionExtra(definitionItemId);

        if (extra instanceof WiredExtraFurniVariable) {
            WiredExtraFurniVariable definition = (WiredExtraFurniVariable) extra;

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

        if (extra instanceof WiredExtraVariableEcho && ((WiredExtraVariableEcho) extra).isFurniEcho()) {
            WiredVariableDefinitionInfo info = ((WiredExtraVariableEcho) extra).createDefinitionInfo(this.room);
            return (info != null && hasVisibleDefinitionName(info.getName())) ? info : null;
        }

        return WiredVariableLevelSystemSupport.getDerivedDefinitionInfo(this.room, WiredVariableLevelSystemSupport.TARGET_FURNI, definitionItemId);
    }

    private WiredExtraFurniVariable getDefinition(int definitionItemId) {
        InteractionWiredExtra extra = this.getDefinitionExtra(definitionItemId);

        if (!(extra instanceof WiredExtraFurniVariable)) {
            return null;
        }

        return (WiredExtraFurniVariable) extra;
    }

    private InteractionWiredExtra getDefinitionExtra(int definitionItemId) {
        if (this.room.getRoomSpecialTypes() == null) {
            return null;
        }

        return this.room.getRoomSpecialTypes().getExtra(definitionItemId);
    }

    private List<WiredExtraVariableEcho> getFurniEchoes() {
        if (this.room.getRoomSpecialTypes() == null) {
            return Collections.emptyList();
        }

        List<WiredExtraVariableEcho> result = new ArrayList<>();

        for (InteractionWiredExtra extra : this.room.getRoomSpecialTypes().getExtras()) {
            if (extra instanceof WiredExtraVariableEcho && ((WiredExtraVariableEcho) extra).isFurniEcho()) {
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

    private VariableAssignment getRawAssignment(int furniId, int definitionItemId) {
        if (furniId <= 0 || definitionItemId <= 0) {
            return null;
        }

        InteractionWiredExtra extra = this.getDefinitionExtra(definitionItemId);
        if (extra instanceof WiredExtraVariableEcho) {
            WiredExtraVariableEcho echo = (WiredExtraVariableEcho) extra;
            if (!echo.hasVariable(this.room, furniId)) {
                return null;
            }

            return new VariableAssignment(echo.getCurrentValue(this.room, furniId), echo.getCreatedAt(this.room, furniId), echo.getUpdatedAt(this.room, furniId));
        }

        ConcurrentHashMap<Integer, VariableAssignment> assignments = this.activeAssignmentsByFurniId.get(furniId);
        return (assignments != null) ? assignments.get(definitionItemId) : null;
    }

    private Integer getRawValue(int furniId, int definitionItemId) {
        VariableAssignment assignment = this.getRawAssignment(furniId, definitionItemId);
        return (assignment != null) ? assignment.getValue() : null;
    }

    private void emitVariableChangedEvents(int furniId, InteractionWiredExtra definitionExtra, WiredVariableDefinitionInfo definitionInfo, boolean existedBefore, Integer previousValue, boolean existsAfter, Integer currentValue) {
        if (definitionInfo == null) {
            return;
        }

        this.emitVariableChangedEvent(furniId, definitionInfo.getItemId(), definitionInfo.hasValue(), existedBefore, previousValue, existsAfter, currentValue);

        for (WiredVariableDefinitionInfo derivedDefinition : WiredVariableLevelSystemSupport.getDerivedDefinitions(this.room, WiredVariableLevelSystemSupport.TARGET_FURNI, definitionExtra, definitionInfo)) {
            WiredVariableLevelSystemSupport.DerivedDefinition resolvedDefinition = WiredVariableLevelSystemSupport.resolveDerivedDefinition(this.room, WiredVariableLevelSystemSupport.TARGET_FURNI, derivedDefinition.getItemId());

            if (resolvedDefinition == null) {
                continue;
            }

            Integer derivedPreviousValue = existedBefore
                    ? WiredVariableLevelSystemSupport.getDerivedValue(resolvedDefinition.getLevelSystem(), resolvedDefinition.getSubvariableType(), previousValue)
                    : null;
            Integer derivedCurrentValue = existsAfter
                    ? WiredVariableLevelSystemSupport.getDerivedValue(resolvedDefinition.getLevelSystem(), resolvedDefinition.getSubvariableType(), currentValue)
                    : null;

            this.emitVariableChangedEvent(furniId, derivedDefinition.getItemId(), true, existedBefore, derivedPreviousValue, existsAfter, derivedCurrentValue);
        }
    }

    private void emitVariableChangedEvent(int furniId, int definitionItemId, boolean hasValue, boolean existedBefore, Integer previousValue, boolean existsAfter, Integer currentValue) {
        boolean created = !existedBefore && existsAfter;
        boolean deleted = existedBefore && !existsAfter;
        WiredEvent.VariableChangeKind changeKind = resolveVariableChangeKind(hasValue, existedBefore, previousValue, existsAfter, currentValue);

        if (!created && !deleted && changeKind == WiredEvent.VariableChangeKind.NONE) {
            return;
        }

        WiredManager.triggerFurniVariableChanged(this.room, furniId, definitionItemId, created, deleted, changeKind);
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

    private void upsertPersistentAssignment(int furniId, int definitionItemId, VariableAssignment assignment) {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement("INSERT INTO room_furni_wired_variables (room_id, furni_id, variable_item_id, value, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE value = VALUES(value), updated_at = VALUES(updated_at)")) {
            statement.setInt(1, this.room.getId());
            statement.setInt(2, furniId);
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
            LOGGER.error("Failed to store permanent wired furni variable for room {}, furni {}, item {}", this.room.getId(), furniId, definitionItemId, e);
        }
    }

    private void deletePersistentAssignment(int furniId, int definitionItemId) {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM room_furni_wired_variables WHERE room_id = ? AND furni_id = ? AND variable_item_id = ?")) {
            statement.setInt(1, this.room.getId());
            statement.setInt(2, furniId);
            statement.setInt(3, definitionItemId);
            statement.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("Failed to delete permanent wired furni variable for room {}, furni {}, item {}", this.room.getId(), furniId, definitionItemId, e);
        }
    }

    private void deletePersistentAssignmentsForDefinition(int definitionItemId) {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM room_furni_wired_variables WHERE room_id = ? AND variable_item_id = ?")) {
            statement.setInt(1, this.room.getId());
            statement.setInt(2, definitionItemId);
            statement.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("Failed to delete permanent wired furni variables for room {} and item {}", this.room.getId(), definitionItemId, e);
        }
    }

    private void deletePersistentAssignmentsForFurni(int furniId) {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM room_furni_wired_variables WHERE room_id = ? AND furni_id = ?")) {
            statement.setInt(1, this.room.getId());
            statement.setInt(2, furniId);
            statement.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("Failed to delete permanent wired furni variables for room {} and furni {}", this.room.getId(), furniId, e);
        }
    }

    public static class Snapshot {
        private final int roomId;
        private final List<DefinitionEntry> definitions;
        private final List<FurniAssignmentsEntry> furnis;

        public Snapshot(int roomId, List<DefinitionEntry> definitions, List<FurniAssignmentsEntry> furnis) {
            this.roomId = roomId;
            this.definitions = definitions;
            this.furnis = furnis;
        }

        public int getRoomId() {
            return this.roomId;
        }

        public List<DefinitionEntry> getDefinitions() {
            return this.definitions;
        }

        public List<FurniAssignmentsEntry> getFurnis() {
            return this.furnis;
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

    public static class FurniAssignmentsEntry {
        private final int furniId;
        private final List<AssignmentEntry> assignments;

        public FurniAssignmentsEntry(int furniId, List<AssignmentEntry> assignments) {
            this.furniId = furniId;
            this.assignments = assignments;
        }

        public int getFurniId() {
            return this.furniId;
        }

        public List<AssignmentEntry> getAssignments() {
            return this.assignments;
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

        public boolean hasValue() {
            return this.value != null;
        }

        public Integer getValue() {
            return this.value;
        }

        public int getCreatedAt() {
            return this.createdAt;
        }

        public int getUpdatedAt() {
            return this.updatedAt;
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
            return this.value;
        }

        public void setValue(Integer value, int updatedAt) {
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

    private static int normalizeTimestamp(int value, int fallback) {
        if (value > 0) return value;
        if (fallback > 0) return fallback;
        return Emulator.getIntUnixTimestamp();
    }
}
