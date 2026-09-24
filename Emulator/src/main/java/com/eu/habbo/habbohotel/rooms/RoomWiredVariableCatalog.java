package com.eu.habbo.habbohotel.rooms;

import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredContextVariableSupport;
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

    /** Holder kinds, mirroring the official {@code entityType} of a variable page element. */
    public static final int TARGET_ROOM = 0;

    public static final int TARGET_USER = 1;

    public static final int TARGET_FURNI = 2;

    public static final int TARGET_CONTEXT = 3;

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
        private final boolean hasValue;
        private final boolean textConnected;
        private final boolean readOnly;
        private final Map<Integer, String> textConnector;

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
            this.variableId = variableId;
            this.variableType = variableType;
            this.variableName = variableName;
            this.availabilityType = availabilityType;
            this.variableTarget = variableTarget;
            this.hasValue = hasValue;
            this.textConnected = textConnected;
            this.readOnly = readOnly;
            this.textConnector = (textConnector != null)
                    ? Collections.unmodifiableMap(new LinkedHashMap<>(textConnector))
                    : Collections.emptyMap();
        }

        public String getVariableId() {
            return this.variableId;
        }

        public int getVariableType() {
            return this.variableType;
        }

        public String getVariableName() {
            return this.variableName;
        }

        public int getAvailabilityType() {
            return this.availabilityType;
        }

        public int getVariableTarget() {
            return this.variableTarget;
        }

        public boolean hasValue() {
            return this.hasValue;
        }

        public boolean isTextConnected() {
            return this.textConnected;
        }

        public boolean isReadOnly() {
            return this.readOnly;
        }

        /**
         * The value-to-text table a text connector addon gives this variable; the official client
         * shows the text beside the number. Empty when the variable is not text connected.
         */
        public Map<Integer, String> getTextConnector() {
            return this.textConnector;
        }

        /** Stable per-variable hash; the client only compares it, it never interprets it. */
        public int hash() {
            int result = this.variableId.hashCode();
            result = (31 * result) + this.variableName.hashCode();
            result = (31 * result) + this.variableType;
            result = (31 * result) + this.availabilityType;
            result = (31 * result) + this.variableTarget;
            result = (31 * result) + (this.hasValue ? 1 : 0);
            result = (31 * result) + (this.textConnected ? 2 : 0);
            result = (31 * result) + (this.readOnly ? 4 : 0);
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

    /** Every wired variable definition of the room, ordered by target and then by name. */
    public static List<Variable> variables(Room room) {
        List<Variable> variables = new ArrayList<>();

        if (room == null) {
            return variables;
        }

        RoomUserVariableManager.Snapshot userSnapshot =
                room.getUserVariableManager().createSnapshot();
        for (RoomUserVariableManager.DefinitionEntry definition : userSnapshot.getDefinitions()) {
            variables.add(new Variable(
                    variableId(TARGET_USER, definition.getItemId()),
                    TARGET_USER,
                    definition.getName(),
                    definition.getAvailability(),
                    TARGET_USER,
                    definition.hasValue(),
                    definition.isTextConnected(),
                    definition.isReadOnly(),
                    textConnector(room, definition.getItemId())));
        }

        RoomFurniVariableManager.Snapshot furniSnapshot =
                room.getFurniVariableManager().createSnapshot();
        for (RoomFurniVariableManager.DefinitionEntry definition : furniSnapshot.getDefinitions()) {
            variables.add(new Variable(
                    variableId(TARGET_FURNI, definition.getItemId()),
                    TARGET_FURNI,
                    definition.getName(),
                    definition.getAvailability(),
                    TARGET_FURNI,
                    definition.hasValue(),
                    definition.isTextConnected(),
                    definition.isReadOnly(),
                    textConnector(room, definition.getItemId())));
        }

        RoomVariableManager.Snapshot roomSnapshot =
                room.getRoomVariableManager().createSnapshot();
        for (RoomVariableManager.DefinitionEntry definition : roomSnapshot.getDefinitions()) {
            variables.add(new Variable(
                    variableId(TARGET_ROOM, definition.getItemId()),
                    TARGET_ROOM,
                    definition.getName(),
                    definition.getAvailability(),
                    TARGET_ROOM,
                    definition.hasValue(),
                    definition.isTextConnected(),
                    definition.isReadOnly(),
                    textConnector(room, definition.getItemId())));
        }

        for (WiredVariableDefinitionInfo definition : WiredContextVariableSupport.createDefinitionInfos(room)) {
            if (definition == null) {
                continue;
            }

            variables.add(new Variable(
                    variableId(TARGET_CONTEXT, definition.getItemId()),
                    TARGET_CONTEXT,
                    definition.getName(),
                    definition.getAvailability(),
                    TARGET_CONTEXT,
                    definition.hasValue(),
                    definition.isTextConnected(),
                    true,
                    textConnector(room, definition.getItemId())));
        }

        variables.sort(Comparator.comparingInt(Variable::getVariableTarget)
                .thenComparing(Variable::getVariableName, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(Variable::getVariableId));
        return variables;
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
                            TARGET_USER,
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
                            TARGET_FURNI,
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
                        TARGET_ROOM,
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
                    && holder.getEntityType() == TARGET_USER
                    && (room == null || room.getHabbo(holder.getEntityId()) == null)) {
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
        Habbo habbo = (room != null) ? room.getHabbo(userId) : null;
        return (habbo != null && habbo.getHabboInfo() != null)
                ? habbo.getHabboInfo().getUsername()
                : String.valueOf(userId);
    }

    private static String furniName(Room room, int furniId) {
        HabboItem item = (room != null) ? room.getHabboItem(furniId) : null;
        return (item != null && item.getBaseItem() != null) ? item.getBaseItem().getName() : String.valueOf(furniId);
    }
}
