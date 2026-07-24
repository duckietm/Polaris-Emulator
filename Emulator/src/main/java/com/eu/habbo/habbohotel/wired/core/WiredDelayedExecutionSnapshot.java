package com.eu.habbo.habbohotel.wired.core;

import com.eu.habbo.WiredCompatibilityDiagnostics;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomTileState;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.api.IWiredCondition;
import com.eu.habbo.habbohotel.wired.api.IWiredEffect;
import com.eu.habbo.habbohotel.wired.api.IWiredTrigger;
import com.eu.habbo.habbohotel.wired.api.WiredDelayedSnapshotProvider;
import com.eu.habbo.habbohotel.wired.api.WiredStack;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Immutable delayed-work description that resolves live room entities only when it runs. */
final class WiredDelayedExecutionSnapshot {

    record Resolved(WiredContext context, List<IWiredEffect> effects) {}

    private final int roomId;
    private final long roomGeneration;
    private final long triggerTime;
    private final EventSnapshot event;
    private final ItemReference triggerItem;
    private final StackSnapshot stack;
    private final List<ComponentReference<IWiredEffect>> effects;
    private final TargetSnapshot targets;
    private final WiredServices services;
    private final StateSnapshot state;
    private final List<Object> legacySettings;
    private final WiredContextVariableScope contextVariables;
    private final boolean includeWiredSelectorItems;

    private WiredDelayedExecutionSnapshot(
            int roomId,
            long roomGeneration,
            long triggerTime,
            EventSnapshot event,
            ItemReference triggerItem,
            StackSnapshot stack,
            List<ComponentReference<IWiredEffect>> effects,
            TargetSnapshot targets,
            WiredServices services,
            StateSnapshot state,
            List<Object> legacySettings,
            WiredContextVariableScope contextVariables,
            boolean includeWiredSelectorItems) {
        this.roomId = roomId;
        this.roomGeneration = roomGeneration;
        this.triggerTime = triggerTime;
        this.event = event;
        this.triggerItem = triggerItem;
        this.stack = stack;
        this.effects = immutableList(effects);
        this.targets = targets;
        this.services = services;
        this.state = state;
        this.legacySettings = immutableList(legacySettings);
        this.contextVariables = contextVariables.copy();
        this.includeWiredSelectorItems = includeWiredSelectorItems;
    }

    static WiredDelayedExecutionSnapshot capture(List<IWiredEffect> effects, WiredContext context, long triggerTime) {
        Room room = context.room();
        long roomGeneration = room.getLifecycleGeneration();
        List<ComponentReference<IWiredEffect>> effectReferences = new ArrayList<>();
        for (IWiredEffect effect : new ArrayList<>(effects)) {
            effectReferences.add(ComponentReference.capture(effect, IWiredEffect.class, room));
        }

        List<Object> legacySettings = new ArrayList<>();
        for (Object value : context.legacySettings().clone()) {
            legacySettings.add(snapshotLegacyValue(value, room));
        }

        return new WiredDelayedExecutionSnapshot(
                room.getId(),
                roomGeneration,
                triggerTime,
                EventSnapshot.capture(context.event(), room),
                ItemReference.capture(context.triggerItem(), room),
                StackSnapshot.capture(context.stack(), room),
                effectReferences,
                TargetSnapshot.capture(context.targets(), room),
                context.services(),
                StateSnapshot.capture(context.state()),
                legacySettings,
                context.contextVariables(),
                context.includeWiredSelectorItems());
    }

    int roomId() {
        return this.roomId;
    }

    long triggerTime() {
        return this.triggerTime;
    }

    Optional<Resolved> resolve(Room room) {
        if (room == null
                || room.getId() != this.roomId
                || !room.isLoaded()
                || this.roomGeneration <= 0L
                || room.getLifecycleGeneration() != this.roomGeneration) {
            return Optional.empty();
        }

        HabboItem resolvedTrigger = resolveRequiredItem(this.triggerItem, room);
        if (this.triggerItem != null && resolvedTrigger == null) {
            return Optional.empty();
        }

        List<IWiredEffect> resolvedEffects = resolveComponents(this.effects, room);
        if (resolvedEffects == null) {
            return Optional.empty();
        }

        WiredStack resolvedStack = null;
        if (this.stack != null) {
            Optional<WiredStack> candidate = this.stack.resolve(room);
            if (candidate.isEmpty()) {
                return Optional.empty();
            }
            resolvedStack = candidate.get();
        }

        WiredEvent resolvedEvent = this.event.resolve(room);
        Object[] resolvedLegacySettings = new Object[this.legacySettings.size()];
        for (int index = 0; index < this.legacySettings.size(); index++) {
            resolvedLegacySettings[index] = resolveLegacyValue(this.legacySettings.get(index), room);
        }

        WiredContext context = new WiredContext(
                resolvedEvent,
                resolvedTrigger,
                resolvedStack,
                this.services,
                this.state.restore(),
                resolvedLegacySettings);
        this.targets.restore(context, room, this.contextVariables, this.includeWiredSelectorItems);
        return Optional.of(new Resolved(context, List.copyOf(resolvedEffects)));
    }

    private static HabboItem resolveRequiredItem(ItemReference reference, Room room) {
        return reference != null ? reference.resolve(room) : null;
    }

    private static <T> List<T> resolveComponents(List<ComponentReference<T>> references, Room room) {
        List<T> resolved = new ArrayList<>(references.size());
        for (ComponentReference<T> reference : references) {
            T component = reference != null ? reference.resolve(room) : null;
            if (component == null) {
                return null;
            }
            resolved.add(component);
        }
        return resolved;
    }

    private static Object snapshotLegacyValue(Object value, Room room) {
        if (value == null
                || value instanceof String
                || value instanceof Number
                || value instanceof Boolean
                || value instanceof Character
                || value instanceof Enum<?>) {
            return value;
        }
        if (value instanceof HabboItem item) {
            return new LegacyItem(ItemReference.capture(item, room));
        }
        if (value instanceof RoomUnit unit) {
            return new LegacyUnit(UnitReference.capture(unit));
        }
        if (value instanceof RoomTile tile) {
            return new LegacyTile(TileSnapshot.capture(tile));
        }
        if (value instanceof WiredContextVariableScope scope) {
            return new LegacyVariableScope(scope.copy());
        }
        Object detachedPluginSnapshot = captureDetachedPluginSnapshot(value, value.getClass(), room);
        if (detachedPluginSnapshot != null) {
            return new LegacyPluginSnapshot(detachedPluginSnapshot);
        }
        if (value.getClass().isArray()) {
            int length = Array.getLength(value);
            List<Object> elements = new ArrayList<>(length);
            for (int index = 0; index < length; index++) {
                elements.add(snapshotLegacyValue(Array.get(value, index), room));
            }
            return new LegacyArray(value.getClass().getComponentType(), immutableList(elements));
        }
        if (value instanceof List<?> values) {
            return new LegacyList(values.stream()
                    .map(element -> snapshotLegacyValue(element, room))
                    .toList());
        }
        if (value instanceof Set<?> values) {
            List<Object> elements = new ArrayList<>();
            for (Object element : values) {
                elements.add(snapshotLegacyValue(element, room));
            }
            return new LegacySet(immutableList(elements));
        }
        if (value instanceof Map<?, ?> values) {
            List<LegacyMapEntry> entries = new ArrayList<>();
            for (Map.Entry<?, ?> entry : values.entrySet()) {
                entries.add(new LegacyMapEntry(
                        snapshotLegacyValue(entry.getKey(), room), snapshotLegacyValue(entry.getValue(), room)));
            }
            return new LegacyMap(immutableList(entries));
        }

        // Compatibility fallback for opaque plugin payloads that are not room entities.
        return new LegacyOpaque(value);
    }

    private static Object resolveLegacyValue(Object value, Room room) {
        if (value instanceof LegacyItem legacy) {
            return legacy.reference() != null ? legacy.reference().resolve(room) : null;
        }
        if (value instanceof LegacyUnit legacy) {
            return legacy.reference() != null ? legacy.reference().resolve(room) : null;
        }
        if (value instanceof LegacyTile legacy) {
            return legacy.snapshot() != null ? legacy.snapshot().resolve(room) : null;
        }
        if (value instanceof LegacyVariableScope legacy) {
            return legacy.scope().copy();
        }
        if (value instanceof LegacyPluginSnapshot legacy) {
            return legacy.value();
        }
        if (value instanceof LegacyArray legacy) {
            Object array =
                    Array.newInstance(legacy.componentType(), legacy.values().size());
            for (int index = 0; index < legacy.values().size(); index++) {
                Array.set(array, index, resolveLegacyValue(legacy.values().get(index), room));
            }
            return array;
        }
        if (value instanceof LegacyList legacy) {
            List<Object> values = new ArrayList<>(legacy.values().size());
            for (Object element : legacy.values()) {
                values.add(resolveLegacyValue(element, room));
            }
            return values;
        }
        if (value instanceof LegacySet legacy) {
            Set<Object> values = new LinkedHashSet<>();
            for (Object element : legacy.values()) {
                values.add(resolveLegacyValue(element, room));
            }
            return values;
        }
        if (value instanceof LegacyMap legacy) {
            Map<Object, Object> values = new LinkedHashMap<>();
            for (LegacyMapEntry entry : legacy.entries()) {
                values.put(resolveLegacyValue(entry.key(), room), resolveLegacyValue(entry.value(), room));
            }
            return values;
        }
        if (value instanceof LegacyOpaque legacy) {
            return legacy.value();
        }
        return value;
    }

    private static <T> List<T> immutableList(List<T> values) {
        return Collections.unmodifiableList(new ArrayList<>(values));
    }

    private record StateSnapshot(
            UUID runId, int maxSteps, int steps, long startTimeMs, boolean aborted, String abortReason) {

        static StateSnapshot capture(WiredState state) {
            return new StateSnapshot(
                    state.runId(),
                    state.maxSteps(),
                    state.steps(),
                    state.startTimeMs(),
                    state.isAborted(),
                    state.abortReason());
        }

        WiredState restore() {
            return WiredState.restoreDelayedSnapshot(
                    this.runId, this.maxSteps, this.steps, this.startTimeMs, this.aborted, this.abortReason);
        }
    }

    private record TargetSnapshot(
            List<UnitReference> users,
            List<ItemReference> items,
            boolean usersModifiedBySelector,
            boolean itemsModifiedBySelector) {

        static TargetSnapshot capture(WiredTargets targets, Room room) {
            List<UnitReference> users =
                    targets.users().stream().map(UnitReference::capture).toList();
            List<ItemReference> items = targets.items().stream()
                    .map(item -> ItemReference.capture(item, room))
                    .toList();
            return new TargetSnapshot(
                    users, items, targets.isUsersModifiedBySelector(), targets.isItemsModifiedBySelector());
        }

        void restore(
                WiredContext context,
                Room room,
                WiredContextVariableScope variables,
                boolean includeWiredSelectorItems) {
            List<RoomUnit> resolvedUsers = new ArrayList<>();
            for (UnitReference user : this.users) {
                RoomUnit resolved = user.resolve(room);
                if (resolved != null) {
                    resolvedUsers.add(resolved);
                }
            }
            List<HabboItem> resolvedItems = new ArrayList<>();
            for (ItemReference item : this.items) {
                HabboItem resolved = item.resolve(room);
                if (resolved != null) {
                    resolvedItems.add(resolved);
                }
            }
            context.restoreDelayedSnapshot(
                    variables,
                    resolvedUsers,
                    resolvedItems,
                    this.usersModifiedBySelector,
                    this.itemsModifiedBySelector,
                    includeWiredSelectorItems);
        }
    }

    private record StackSnapshot(
            ItemReference triggerItem,
            ComponentReference<IWiredTrigger> trigger,
            List<ComponentReference<IWiredCondition>> conditions,
            List<ComponentReference<IWiredEffect>> effects,
            int conditionEvaluationMode,
            int conditionEvaluationValue,
            boolean useRandom,
            boolean useUnseen,
            boolean executeInOrder) {

        static StackSnapshot capture(WiredStack stack, Room room) {
            if (stack == null) {
                return null;
            }
            List<ComponentReference<IWiredCondition>> conditions = stack.conditions().stream()
                    .map(condition -> ComponentReference.capture(condition, IWiredCondition.class, room))
                    .toList();
            List<ComponentReference<IWiredEffect>> effects = stack.effects().stream()
                    .map(effect -> ComponentReference.capture(effect, IWiredEffect.class, room))
                    .toList();
            return new StackSnapshot(
                    ItemReference.capture(stack.triggerItem(), room),
                    ComponentReference.capture(stack.trigger(), IWiredTrigger.class, room),
                    conditions,
                    effects,
                    stack.conditionEvaluationMode(),
                    stack.conditionEvaluationValue(),
                    stack.useRandom(),
                    stack.useUnseen(),
                    stack.executeInOrder());
        }

        Optional<WiredStack> resolve(Room room) {
            HabboItem triggerItem = resolveRequiredItem(this.triggerItem, room);
            if (this.triggerItem != null && triggerItem == null) {
                return Optional.empty();
            }
            IWiredTrigger trigger = this.trigger != null ? this.trigger.resolve(room) : null;
            if (this.trigger != null && trigger == null) {
                return Optional.empty();
            }
            List<IWiredCondition> conditions = resolveComponents(this.conditions, room);
            List<IWiredEffect> effects = resolveComponents(this.effects, room);
            if (conditions == null || effects == null) {
                return Optional.empty();
            }
            return Optional.of(new WiredStack(
                    triggerItem,
                    trigger,
                    conditions,
                    effects,
                    this.conditionEvaluationMode,
                    this.conditionEvaluationValue,
                    this.useRandom,
                    this.useUnseen,
                    this.executeInOrder));
        }
    }

    private record ComponentReference<T>(
            Class<T> contract, ItemReference item, T detachedSnapshot, T compatibilityFallback) {

        static <T> ComponentReference<T> capture(T component, Class<T> contract, Room room) {
            if (component == null) {
                return null;
            }
            if (component instanceof HabboItem item) {
                return new ComponentReference<>(contract, ItemReference.capture(item, room), null, null);
            }
            Object detachedSnapshot = captureDetachedPluginSnapshot(component, component.getClass(), room);
            if (contract.isInstance(detachedSnapshot)) {
                return new ComponentReference<>(contract, null, contract.cast(detachedSnapshot), null);
            }
            // Programmatic/plugin components that are not room furniture have no stable room ID.
            return new ComponentReference<>(contract, null, null, component);
        }

        T resolve(Room room) {
            Object resolved = this.item != null
                    ? this.item.resolve(room)
                    : this.detachedSnapshot != null ? this.detachedSnapshot : this.compatibilityFallback;
            return this.contract.isInstance(resolved) ? this.contract.cast(resolved) : null;
        }
    }

    private static Object captureDetachedPluginSnapshot(Object value, Class<?> requiredType, Room room) {
        if (!(value instanceof WiredDelayedSnapshotProvider<?> provider)) {
            return null;
        }

        try {
            Object snapshot = provider.snapshotForDelayedExecution();
            if (snapshot == null || snapshot == value || !requiredType.isInstance(snapshot)) {
                WiredCompatibilityDiagnostics.record(
                        WiredCompatibilityDiagnostics.FailurePoint.DELAYED_PLUGIN_SNAPSHOT,
                        room != null ? room.getId() : 0,
                        value instanceof HabboItem item ? item.getId() : 0,
                        new IllegalStateException("Invalid detached WIRED plugin snapshot"));
                return null;
            }
            return snapshot;
        } catch (RuntimeException failure) {
            WiredCompatibilityDiagnostics.record(
                    WiredCompatibilityDiagnostics.FailurePoint.DELAYED_PLUGIN_SNAPSHOT,
                    room != null ? room.getId() : 0,
                    value instanceof HabboItem item ? item.getId() : 0,
                    failure);
            return null;
        }
    }

    private record ItemReference(int id, long incarnation, String runtimeClassName, HabboItem compatibilityFallback) {

        static ItemReference capture(HabboItem item, Room room) {
            if (item == null) {
                return null;
            }
            long incarnation = item.getId() > 0 ? room.getItemIncarnation(item.getId()) : 0L;
            return new ItemReference(
                    item.getId(), incarnation, item.getClass().getName(), incarnation > 0L ? null : item);
        }

        HabboItem resolve(Room room) {
            if (this.incarnation <= 0L) {
                return this.compatibilityFallback;
            }
            if (room.getItemIncarnation(this.id) != this.incarnation) {
                return null;
            }
            HabboItem item = room.getHabboItem(this.id);
            return item != null && item.getClass().getName().equals(this.runtimeClassName) ? item : null;
        }
    }

    private record UnitReference(int id, String runtimeClassName, RoomUnit compatibilityFallback) {

        static UnitReference capture(RoomUnit unit) {
            if (unit == null) {
                return null;
            }
            return new UnitReference(unit.getId(), unit.getClass().getName(), unit.getId() > 0 ? null : unit);
        }

        RoomUnit resolve(Room room) {
            Set<RoomUnit> units = room.getRoomUnits();
            if (units == null) {
                return null;
            }
            if (this.compatibilityFallback != null) {
                return units.contains(this.compatibilityFallback) ? this.compatibilityFallback : null;
            }
            for (RoomUnit unit : units) {
                if (unit != null
                        && unit.getId() == this.id
                        && unit.getClass().getName().equals(this.runtimeClassName)) {
                    return unit;
                }
            }
            return null;
        }
    }

    private record TileSnapshot(
            short x, short y, short z, RoomTileState state, boolean allowStack, double stackHeight) {

        static TileSnapshot capture(RoomTile tile) {
            if (tile == null) {
                return null;
            }
            return new TileSnapshot(tile.x, tile.y, tile.z, tile.state, tile.getAllowStack(), tile.getStackHeight());
        }

        RoomTile resolve(Room room) {
            if (room.getLayout() != null) {
                RoomTile live = room.getLayout().getTile(this.x, this.y);
                if (live != null) {
                    return live;
                }
            }
            RoomTile copy = new RoomTile(this.x, this.y, this.z, this.state, this.allowStack);
            copy.setStackHeight(this.stackHeight);
            return copy;
        }
    }

    private record EventSnapshot(
            WiredEvent.Type type,
            UnitReference actor,
            UnitReference originActor,
            ItemReference sourceItem,
            TileSnapshot tile,
            String text,
            UnitReference targetUnit,
            int score,
            int scoreAdded,
            boolean triggeredByEffect,
            int callStackDepth,
            int signalChannel,
            int actionId,
            int actionParameter,
            int chatType,
            int chatStyle,
            int signalUserCount,
            int signalFurniCount,
            int variableTargetType,
            int variableDefinitionItemId,
            boolean variableCreated,
            boolean variableDeleted,
            WiredEvent.VariableChangeKind variableChangeKind,
            WiredContextVariableScope contextVariableScope,
            long createdAtMs) {

        static EventSnapshot capture(WiredEvent event, Room room) {
            return new EventSnapshot(
                    event.getType(),
                    event.getActor().map(UnitReference::capture).orElse(null),
                    event.getOriginActor().map(UnitReference::capture).orElse(null),
                    event.getSourceItem()
                            .map(item -> ItemReference.capture(item, room))
                            .orElse(null),
                    event.getTile().map(TileSnapshot::capture).orElse(null),
                    event.getText().orElse(null),
                    event.getTargetUnit().map(UnitReference::capture).orElse(null),
                    event.getScore(),
                    event.getScoreAdded(),
                    event.isTriggeredByEffect(),
                    event.getCallStackDepth(),
                    event.getSignalChannel(),
                    event.getActionId(),
                    event.getActionParameter(),
                    event.getChatType(),
                    event.getChatStyle(),
                    event.getSignalUserCount(),
                    event.getSignalFurniCount(),
                    event.getVariableTargetType(),
                    event.getVariableDefinitionItemId(),
                    event.isVariableCreated(),
                    event.isVariableDeleted(),
                    event.getVariableChangeKind(),
                    event.getContextVariableScope() != null
                            ? event.getContextVariableScope().copy()
                            : null,
                    event.getCreatedAtMs());
        }

        WiredEvent resolve(Room room) {
            WiredEvent.Builder builder = WiredEvent.builder(this.type, room)
                    .actor(this.actor != null ? this.actor.resolve(room) : null)
                    .originActor(this.originActor != null ? this.originActor.resolve(room) : null)
                    .sourceItem(this.sourceItem != null ? this.sourceItem.resolve(room) : null)
                    .tile(this.tile != null ? this.tile.resolve(room) : null)
                    .text(this.text)
                    .targetUnit(this.targetUnit != null ? this.targetUnit.resolve(room) : null)
                    .score(this.score)
                    .scoreAdded(this.scoreAdded)
                    .triggeredByEffect(this.triggeredByEffect)
                    .callStackDepth(this.callStackDepth)
                    .signalChannel(this.signalChannel)
                    .actionId(this.actionId)
                    .actionParameter(this.actionParameter)
                    .chatType(this.chatType)
                    .chatStyle(this.chatStyle)
                    .signalUserCount(this.signalUserCount)
                    .signalFurniCount(this.signalFurniCount)
                    .variableTargetType(this.variableTargetType)
                    .variableDefinitionItemId(this.variableDefinitionItemId)
                    .variableCreated(this.variableCreated)
                    .variableDeleted(this.variableDeleted)
                    .variableChangeKind(this.variableChangeKind)
                    .createdAtMs(this.createdAtMs);
            if (this.contextVariableScope != null) {
                builder.contextVariableScope(this.contextVariableScope.copy());
            }
            return builder.build();
        }
    }

    private record LegacyItem(ItemReference reference) {}

    private record LegacyUnit(UnitReference reference) {}

    private record LegacyTile(TileSnapshot snapshot) {}

    private record LegacyVariableScope(WiredContextVariableScope scope) {}

    private record LegacyPluginSnapshot(Object value) {}

    private record LegacyArray(Class<?> componentType, List<Object> values) {}

    private record LegacyList(List<Object> values) {}

    private record LegacySet(List<Object> values) {}

    private record LegacyMapEntry(Object key, Object value) {}

    private record LegacyMap(List<LegacyMapEntry> entries) {}

    private record LegacyOpaque(Object value) {}
}
