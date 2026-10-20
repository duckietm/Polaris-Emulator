package com.eu.habbo.habbohotel.rooms;

import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredContextVariableSupport;
import com.eu.habbo.habbohotel.wired.core.WiredPointerVariableSupport;
import com.eu.habbo.habbohotel.wired.core.WiredVariableLevelSystemSupport;
import com.eu.habbo.habbohotel.wired.core.WiredVariableTextConnectorSupport;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Flattens the room's wired variable definitions and their holders into the shape the official
 * AIR 13 wired menu expects: one string-keyed variable per definition, a hash per variable so the
 * client can cache them, and paged holder lists for the variable overview.
 *
 * <p>Kept outside {@code habbohotel.wired} on purpose: it is a protocol projection of the existing
 * room variable managers, not part of the wired plugin surface.
 */
public final class RoomWiredVariableCatalog {

    /** Official variable targets ({@code WiredVariable.variableTarget}): furni 0, user 1, global -10, context -20. */
    public static final int TARGET_FURNI = 0;

    public static final int TARGET_USER = 1;

    public static final int TARGET_GLOBAL = -10;

    public static final int TARGET_CONTEXT = -20;

    /** Our name for the global target. */
    public static final int TARGET_ROOM = TARGET_GLOBAL;

    /**
     * Official variable types ({@code WiredVariable.variableType}): made with a variable box, internal ({@code @}),
     * a sub-variable of a box (level-up, quest and Time Utilities parts) and smart ({@code ~}).
     */
    public static final int TYPE_CREATED = 0;

    public static final int TYPE_INTERNAL = 1;

    public static final int TYPE_SUB = 2;

    public static final int TYPE_SMART = 3;

    /** Holder kinds of a variable page element ({@code entityType}); unchanged on the wire. */
    public static final int HOLDER_ROOM = 0;

    public static final int HOLDER_USER = 1;

    public static final int HOLDER_FURNI = 2;

    /** Official {@code userTypeFilter} values of the variable overview. */
    public static final int USER_FILTER_ALL = 0;

    public static final int USER_FILTER_IN_ROOM = 1;

    /** Official {@code sortTypFilter} values; -1 keeps the natural (holder id) order. */
    public static final int SORT_NONE = -1;

    public static final int SORT_VALUE_ASCENDING = 0;

    public static final int SORT_VALUE_DESCENDING = 1;

    public static final int SORT_NAME = 2;

    private RoomWiredVariableCatalog() {}

    /** One wired variable definition as the client caches it. */
    public static final class Variable {
        private final String variableId;
        private final int variableType;
        private final String variableName;
        private final int availabilityType;
        private final int variableTarget;
        private final boolean alwaysAvailable;
        private final boolean canCreateAndDelete;
        private final boolean hasValue;
        private final boolean canWriteValue;
        private final boolean canInterceptChanges;
        private final boolean invisible;
        private final boolean requiresExecutionContext;
        private final boolean canReadCreationTime;
        private final boolean canReadLastUpdateTime;
        private final boolean textConnected;
        private final boolean readOnly;
        private final Map<Integer, String> textConnector;
        private final String textConnectorFallback;

        public Variable(
                String variableId,
                int variableType,
                String variableName,
                int availabilityType,
                int variableTarget,
                boolean hasValue,
                boolean textConnected,
                boolean readOnly) {
            this(
                    variableId,
                    variableType,
                    variableName,
                    availabilityType,
                    variableTarget,
                    hasValue,
                    textConnected,
                    readOnly,
                    Collections.emptyMap());
        }

        public Variable(
                String variableId,
                int variableType,
                String variableName,
                int availabilityType,
                int variableTarget,
                boolean hasValue,
                boolean textConnected,
                boolean readOnly,
                Map<Integer, String> textConnector) {
            this(
                    variableId,
                    variableType,
                    variableName,
                    availabilityType,
                    variableTarget,
                    hasValue,
                    textConnected,
                    readOnly,
                    textConnector,
                    null,
                    false);
        }

        /**
         * A variable of a box, with the text connector's fallback text (null for none) and {@code
         * requiresExecutionContext}: the variable only exists while a stack runs (a pointer), as the official {@code
         * WiredVariable}. The other flags follow from what such a variable can do: a global one always exists and is
         * never given or removed, a read-only one (a reference, an echo) cannot be written, given or removed, a
         * sub-variable is computed from its box, and only stored values carry their times.
         */
        public Variable(
                String variableId,
                int variableType,
                String variableName,
                int availabilityType,
                int variableTarget,
                boolean hasValue,
                boolean textConnected,
                boolean readOnly,
                Map<Integer, String> textConnector,
                String textConnectorFallback,
                boolean requiresExecutionContext) {
            this(
                    variableId,
                    variableType,
                    variableName,
                    availabilityType,
                    variableTarget,
                    variableTarget == TARGET_GLOBAL,
                    !readOnly && variableTarget != TARGET_GLOBAL && variableType != TYPE_SUB,
                    hasValue,
                    !readOnly && hasValue,
                    !requiresExecutionContext,
                    false,
                    requiresExecutionContext,
                    timestamped(variableType, variableTarget, requiresExecutionContext),
                    timestamped(variableType, variableTarget, requiresExecutionContext),
                    textConnected,
                    readOnly,
                    textConnector,
                    textConnectorFallback);
        }

        /** Every flag of the official {@code WiredVariable} given explicitly. */
        public Variable(
                String variableId,
                int variableType,
                String variableName,
                int availabilityType,
                int variableTarget,
                boolean alwaysAvailable,
                boolean canCreateAndDelete,
                boolean hasValue,
                boolean canWriteValue,
                boolean canInterceptChanges,
                boolean invisible,
                boolean requiresExecutionContext,
                boolean canReadCreationTime,
                boolean canReadLastUpdateTime,
                boolean textConnected,
                boolean readOnly,
                Map<Integer, String> textConnector,
                String textConnectorFallback) {
            this.variableId = variableId;
            this.variableType = variableType;
            this.variableName = variableName;
            this.availabilityType = availabilityType;
            this.variableTarget = variableTarget;
            this.alwaysAvailable = alwaysAvailable;
            this.canCreateAndDelete = canCreateAndDelete;
            this.hasValue = hasValue;
            this.canWriteValue = canWriteValue;
            this.canInterceptChanges = canInterceptChanges;
            this.invisible = invisible;
            this.requiresExecutionContext = requiresExecutionContext;
            this.canReadCreationTime = canReadCreationTime;
            this.canReadLastUpdateTime = canReadLastUpdateTime;
            this.textConnected = textConnected;
            this.readOnly = readOnly;
            this.textConnector = (textConnector != null)
                    ? Collections.unmodifiableMap(new LinkedHashMap<>(textConnector))
                    : Collections.emptyMap();
            this.textConnectorFallback = textConnectorFallback;
        }

        /**
         * Stored values carry when they were created and last changed: the user, furni and global variables of a box.
         * A context variable lives for one stack, a pointer only while a stack runs, and a sub-variable is computed.
         */
        private static boolean timestamped(int variableType, int variableTarget, boolean requiresExecutionContext) {
            return variableType == TYPE_CREATED && variableTarget != TARGET_CONTEXT && !requiresExecutionContext;
        }

        /** The text a value the connector does not name reads as, or null. */
        public String getTextConnectorFallback() {
            return this.textConnectorFallback;
        }

        /** True for a pointer: it only exists while a stack runs, so the menu cannot list holders of it. */
        public boolean requiresExecutionContext() {
            return this.requiresExecutionContext;
        }

        public String getVariableId() {
            return this.variableId;
        }

        /** The official variable type: {@link #TYPE_CREATED}, {@link #TYPE_INTERNAL}, {@link #TYPE_SUB} or smart. */
        public int getVariableType() {
            return this.variableType;
        }

        public String getVariableName() {
            return this.variableName;
        }

        public int getAvailabilityType() {
            return this.availabilityType;
        }

        /** The official target: {@link #TARGET_FURNI}, {@link #TARGET_USER}, {@link #TARGET_GLOBAL} or context. */
        public int getVariableTarget() {
            return this.variableTarget;
        }

        /** Every holder has the variable without a box giving it ({@code @id}, a global variable). */
        public boolean isAlwaysAvailable() {
            return this.alwaysAvailable;
        }

        /** Give Variable and Remove Variable can hand the variable out and take it away. */
        public boolean canCreateAndDelete() {
            return this.canCreateAndDelete;
        }

        public boolean hasValue() {
            return this.hasValue;
        }

        public boolean canWriteValue() {
            return this.canWriteValue;
        }

        /** Variable Changed can watch the variable. */
        public boolean canInterceptChanges() {
            return this.canInterceptChanges;
        }

        /** Hidden from the pickers: a smart variable while nothing in the room is of its kind. */
        public boolean isInvisible() {
            return this.invisible;
        }

        public boolean canReadCreationTime() {
            return this.canReadCreationTime;
        }

        public boolean canReadLastUpdateTime() {
            return this.canReadLastUpdateTime;
        }

        public boolean isTextConnected() {
            return this.textConnected;
        }

        public boolean isReadOnly() {
            return this.readOnly;
        }

        /**
         * The value-to-text table a text connector addon gives this variable; the official client
         * shows the text beside the number. Empty when the variable is not text connected, and empty
         * for a packed built-in value ({@code @position}), which the client unpacks itself.
         */
        public Map<Integer, String> getTextConnector() {
            return this.textConnector;
        }

        /**
         * Whether the block carries a text connector: a box's variable when a connector addon names its values, and a
         * packed built-in ({@code @position}) with an empty table, which the client unpacks as "x: 14, y: 22".
         */
        public boolean hasTextConnector() {
            return !this.textConnector.isEmpty()
                    || (this.textConnected && (this.variableType == TYPE_INTERNAL || this.variableType == TYPE_SMART));
        }

        /** Stable per-variable hash; the client only compares it, it never interprets it. */
        public int hash() {
            int result = this.variableId.hashCode();
            result = (31 * result) + this.variableName.hashCode();
            result = (31 * result) + this.variableType;
            result = (31 * result) + this.availabilityType;
            result = (31 * result) + this.variableTarget;
            int flags = (this.hasValue ? 1 : 0)
                    | (this.textConnected ? 2 : 0)
                    | (this.readOnly ? 4 : 0)
                    | (this.requiresExecutionContext ? 8 : 0)
                    | (this.alwaysAvailable ? 16 : 0)
                    | (this.canCreateAndDelete ? 32 : 0)
                    | (this.canWriteValue ? 64 : 0)
                    | (this.canInterceptChanges ? 128 : 0)
                    | (this.invisible ? 256 : 0)
                    | (this.canReadCreationTime ? 512 : 0)
                    | (this.canReadLastUpdateTime ? 1024 : 0);
            result = (31 * result) + flags;
            result = (31 * result) + (this.textConnectorFallback != null ? this.textConnectorFallback.hashCode() : 0);
            // The table is part of what the client caches, so a renamed value has to re-sync too.
            for (Map.Entry<Integer, String> mapping : this.textConnector.entrySet()) {
                result = (31 * result) + mapping.getKey();
                result = (31 * result) + mapping.getValue().hashCode();
            }
            return result;
        }
    }

    /** One entity that currently holds a value for a variable. */
    public static final class Holder {
        private final int entityType;
        private final int entityId;
        private final String entityName;
        private final int value;
        private final long createdAt;
        private final long updatedAt;
        private final boolean hasValue;

        Holder(int entityType, int entityId, String entityName, int value, long createdAt, long updatedAt) {
            this(entityType, entityId, entityName, value, createdAt, updatedAt, true);
        }

        Holder(
                int entityType,
                int entityId,
                String entityName,
                int value,
                long createdAt,
                long updatedAt,
                boolean hasValue) {
            this.entityType = entityType;
            this.entityId = entityId;
            this.entityName = entityName;
            this.value = value;
            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
            this.hasValue = hasValue;
        }

        /** False for a holder of a variable without a value; {@link #getValue()} is then 0. */
        public boolean hasValue() {
            return this.hasValue;
        }

        public int getEntityType() {
            return this.entityType;
        }

        public int getEntityId() {
            return this.entityId;
        }

        public String getEntityName() {
            return this.entityName;
        }

        public int getValue() {
            return this.value;
        }

        public long getCreatedAt() {
            return this.createdAt;
        }

        public long getUpdatedAt() {
            return this.updatedAt;
        }
    }

    /**
     * Every wired variable of the room: the variables of its boxes, ordered by target and then by name, followed by
     * the built-in ones under Habbo's fixed ids ({@link RoomWiredInternalVariables}).
     */
    public static List<Variable> variables(Room room) {
        List<Variable> variables = new ArrayList<>();

        if (room == null) {
            return variables;
        }

        RoomUserVariableManager.Snapshot userSnapshot =
                room.getUserVariableManager().createSnapshot();
        for (RoomUserVariableManager.DefinitionEntry definition : userSnapshot.getDefinitions()) {
            variables.add(boxVariable(
                    room,
                    TARGET_USER,
                    definition.getItemId(),
                    definition.getName(),
                    definition.getAvailability(),
                    definition.hasValue(),
                    definition.isTextConnected(),
                    definition.isReadOnly()));
        }

        RoomFurniVariableManager.Snapshot furniSnapshot =
                room.getFurniVariableManager().createSnapshot();
        for (RoomFurniVariableManager.DefinitionEntry definition : furniSnapshot.getDefinitions()) {
            variables.add(boxVariable(
                    room,
                    TARGET_FURNI,
                    definition.getItemId(),
                    definition.getName(),
                    definition.getAvailability(),
                    definition.hasValue(),
                    definition.isTextConnected(),
                    definition.isReadOnly()));
        }

        RoomVariableManager.Snapshot roomSnapshot =
                room.getRoomVariableManager().createSnapshot();
        for (RoomVariableManager.DefinitionEntry definition : roomSnapshot.getDefinitions()) {
            variables.add(boxVariable(
                    room,
                    TARGET_GLOBAL,
                    definition.getItemId(),
                    definition.getName(),
                    definition.getAvailability(),
                    definition.hasValue(),
                    definition.isTextConnected(),
                    definition.isReadOnly()));
        }

        for (WiredVariableDefinitionInfo definition : WiredContextVariableSupport.createDefinitionInfos(room)) {
            if (definition == null) {
                continue;
            }

            variables.add(boxVariable(
                    room,
                    TARGET_CONTEXT,
                    definition.getItemId(),
                    definition.getName(),
                    definition.getAvailability(),
                    definition.hasValue(),
                    definition.isTextConnected(),
                    definition.isReadOnly()));
        }

        variables.sort(Comparator.comparingInt(Variable::getVariableTarget)
                .thenComparing(Variable::getVariableName, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(Variable::getVariableId));
        variables.addAll(RoomWiredInternalVariables.catalogEntries(room));
        return variables;
    }

    /**
     * The catalog entry of a box's variable: a level-up, quest or Time Utilities part is a sub-variable (type 2), and
     * a pointer requires an execution context.
     */
    private static Variable boxVariable(
            Room room,
            int target,
            int definitionItemId,
            String name,
            int availability,
            boolean hasValue,
            boolean textConnected,
            boolean readOnly) {
        return new Variable(
                variableId(target, definitionItemId),
                isSubVariable(room, target, definitionItemId) ? TYPE_SUB : TYPE_CREATED,
                name,
                availability,
                target,
                hasValue,
                textConnected,
                readOnly,
                textConnector(room, definitionItemId),
                WiredVariableTextConnectorSupport.fallbackText(room, definitionItemId),
                WiredPointerVariableSupport.isPointer(room, definitionItemId));
    }

    private static boolean isSubVariable(Room room, int target, int definitionItemId) {
        int levelTarget =
                switch (target) {
                    case TARGET_USER -> WiredVariableLevelSystemSupport.TARGET_USER;
                    case TARGET_FURNI -> WiredVariableLevelSystemSupport.TARGET_FURNI;
                    case TARGET_GLOBAL -> WiredVariableLevelSystemSupport.TARGET_ROOM;
                    default -> -1;
                };
        return levelTarget >= 0
                && WiredVariableLevelSystemSupport.resolveDerivedDefinition(room, levelTarget, definitionItemId)
                        != null;
    }

    /** The text-connector table of a definition, empty unless a connector addon names its values. */
    private static Map<Integer, String> textConnector(Room room, int definitionItemId) {
        return WiredVariableTextConnectorSupport.mappings(room, definitionItemId);
    }

    /** Hash of the whole set; the client asks for it before requesting a diff. */
    public static int allVariablesHash(List<Variable> variables) {
        int result = variables.size();

        for (Variable variable : variables) {
            result = (31 * result) + variable.hash();
        }

        return result;
    }

    /** Indexes the variables by their string id, keeping the sorted order. */
    public static Map<String, Variable> byId(List<Variable> variables) {
        Map<String, Variable> result = new LinkedHashMap<>();

        for (Variable variable : variables) {
            result.put(variable.getVariableId(), variable);
        }

        return result;
    }

    /** Every entity that currently holds a value for the given variable. */
    public static List<Holder> holders(Room room, String variableId) {
        return holders(room, variableId, false);
    }

    /**
     * Every entity that currently holds the given variable; with {@code includeValueless} also the
     * holders of a variable that has no value.
     */
    public static List<Holder> holders(Room room, String variableId, boolean includeValueless) {
        List<Holder> holders = new ArrayList<>();

        if (room == null || variableId == null) {
            return holders;
        }

        int target = targetOf(variableId);
        int definitionItemId = definitionIdOf(variableId);

        if (definitionItemId <= 0) {
            return holders;
        }

        if (target == TARGET_USER) {
            RoomUserVariableManager.Snapshot snapshot =
                    room.getUserVariableManager().createSnapshot();
            for (RoomUserVariableManager.UserAssignmentsEntry user : snapshot.getUsers()) {
                for (RoomUserVariableManager.AssignmentEntry assignment : user.getAssignments()) {
                    if (assignment.getVariableItemId() != definitionItemId
                            || (!assignment.hasValue() && !includeValueless)) {
                        continue;
                    }

                    holders.add(new Holder(
                            HOLDER_USER,
                            user.getUserId(),
                            userName(room, user.getUserId()),
                            assignment.hasValue() ? assignment.getValue() : 0,
                            toMillis(assignment.getCreatedAt()),
                            toMillis(assignment.getUpdatedAt()),
                            assignment.hasValue()));
                }
            }
        } else if (target == TARGET_FURNI) {
            RoomFurniVariableManager.Snapshot snapshot =
                    room.getFurniVariableManager().createSnapshot();
            for (RoomFurniVariableManager.FurniAssignmentsEntry furni : snapshot.getFurnis()) {
                for (RoomFurniVariableManager.AssignmentEntry assignment : furni.getAssignments()) {
                    if (assignment.getVariableItemId() != definitionItemId
                            || (!assignment.hasValue() && !includeValueless)) {
                        continue;
                    }

                    holders.add(new Holder(
                            HOLDER_FURNI,
                            furni.getFurniId(),
                            furniName(room, furni.getFurniId()),
                            assignment.hasValue() ? assignment.getValue() : 0,
                            toMillis(assignment.getCreatedAt()),
                            toMillis(assignment.getUpdatedAt()),
                            assignment.hasValue()));
                }
            }
        } else if (target == TARGET_ROOM) {
            RoomVariableManager.Snapshot snapshot =
                    room.getRoomVariableManager().createSnapshot();
            for (RoomVariableManager.AssignmentEntry assignment : snapshot.getAssignments()) {
                if (assignment.getVariableItemId() != definitionItemId || !assignment.hasValue()) {
                    continue;
                }

                holders.add(new Holder(
                        HOLDER_ROOM,
                        room.getId(),
                        room.getName(),
                        assignment.getValue(),
                        toMillis(assignment.getCreatedAt()),
                        toMillis(assignment.getUpdatedAt())));
            }
        }

        return holders;
    }

    /** Applies the official user-type filter and sort order of the variable overview. */
    public static List<Holder> filterAndSort(Room room, List<Holder> holders, int userTypeFilter, int sortTypeFilter) {
        List<Holder> result = new ArrayList<>();

        for (Holder holder : holders) {
            if (userTypeFilter == USER_FILTER_IN_ROOM
                    && holder.getEntityType() == HOLDER_USER
                    && !UserVariableHolders.isInRoom(room, holder.getEntityId())) {
                continue;
            }

            result.add(holder);
        }

        if (sortTypeFilter == SORT_VALUE_ASCENDING) {
            result.sort(Comparator.comparingInt(Holder::getValue).thenComparingInt(Holder::getEntityId));
        } else if (sortTypeFilter == SORT_VALUE_DESCENDING) {
            result.sort(Comparator.comparingInt(Holder::getValue).reversed().thenComparingInt(Holder::getEntityId));
        } else if (sortTypeFilter == SORT_NAME) {
            result.sort(Comparator.comparing(Holder::getEntityName, String.CASE_INSENSITIVE_ORDER)
                    .thenComparingInt(Holder::getEntityId));
        }

        return result;
    }

    /** The string id the client uses for a definition of the given target. */
    public static String variableId(int target, int definitionItemId) {
        return prefix(target) + definitionItemId;
    }

    /** The target encoded in a variable id, or -1 when the id is not one of ours. */
    public static int targetOf(String variableId) {
        if (variableId == null) {
            return -1;
        }
        if (variableId.startsWith("user:")) {
            return TARGET_USER;
        }
        if (variableId.startsWith("furni:")) {
            return TARGET_FURNI;
        }
        if (variableId.startsWith("room:")) {
            return TARGET_ROOM;
        }
        if (variableId.startsWith("ctx:")) {
            return TARGET_CONTEXT;
        }
        return -1;
    }

    /** The definition item id encoded in a variable id, or 0 when the id is not one of ours. */
    public static int definitionIdOf(String variableId) {
        if (variableId == null) {
            return 0;
        }

        int separator = variableId.indexOf(':');

        if (separator < 0 || separator + 1 >= variableId.length()) {
            return 0;
        }

        try {
            return Integer.parseInt(variableId.substring(separator + 1));
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    /** The variable managers keep unix seconds; the official page reads epoch milliseconds. */
    private static long toMillis(int unixSeconds) {
        return (unixSeconds > 0) ? unixSeconds * 1000L : 0L;
    }

    private static String prefix(int target) {
        return switch (target) {
            case TARGET_USER -> "user:";
            case TARGET_FURNI -> "furni:";
            case TARGET_CONTEXT -> "ctx:";
            default -> "room:";
        };
    }

    private static String userName(Room room, int userId) {
        String name = UserVariableHolders.nameOf(room, userId);
        return !name.isEmpty() ? name : String.valueOf(UserVariableHolders.idOf(userId));
    }

    private static String furniName(Room room, int furniId) {
        HabboItem item = (room != null) ? room.getHabboItem(furniId) : null;
        return (item != null && item.getBaseItem() != null) ? item.getBaseItem().getName() : String.valueOf(furniId);
    }
}
