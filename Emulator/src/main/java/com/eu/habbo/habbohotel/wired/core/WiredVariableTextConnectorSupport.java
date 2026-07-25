package com.eu.habbo.habbohotel.wired.core;

import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraVariableTextConnector;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.wired.arrays.WiredArrayVariableDefinition;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public final class WiredVariableTextConnectorSupport {
    private static final String PRESERVED_SPACE = "\u00A0";

    private WiredVariableTextConnectorSupport() {}

    public static boolean isTextConnected(Room room, InteractionWiredExtra definition) {
        return getConnector(room, definition, 0) != null;
    }

    public static boolean isTextConnected(Room room, int definitionItemId) {
        return getConnector(room, definitionItemId, 0) != null;
    }

    public static WiredExtraVariableTextConnector getConnector(Room room, int definitionItemId) {
        List<WiredExtraVariableTextConnector> connectors = getConnectors(room, definitionItemId, 0);
        return connectors.isEmpty() ? null : connectors.get(0);
    }

    public static List<WiredExtraVariableTextConnector> getConnectors(Room room, int definitionItemId) {
        if (room == null || room.getRoomSpecialTypes() == null || definitionItemId <= 0) {
            return Collections.emptyList();
        }

        InteractionWiredExtra extra = room.getRoomSpecialTypes().getExtra(definitionItemId);
        return getConnectors(room, extra);
    }

    public static WiredExtraVariableTextConnector getConnector(Room room, InteractionWiredExtra definition) {
        List<WiredExtraVariableTextConnector> connectors = getConnectors(room, definition, 0);
        return connectors.isEmpty() ? null : connectors.get(0);
    }

    public static List<WiredExtraVariableTextConnector> getConnectors(Room room, InteractionWiredExtra definition) {
        if (room == null || definition == null || room.getRoomSpecialTypes() == null) {
            return Collections.emptyList();
        }

        Collection<InteractionWiredExtra> extras =
                room.getRoomSpecialTypes().getExtras(definition.getX(), definition.getY());
        if (extras == null || extras.isEmpty()) {
            return Collections.emptyList();
        }

        List<WiredExtraVariableTextConnector> connectors = new ArrayList<>();

        for (InteractionWiredExtra extra : WiredExecutionOrderUtil.sort(extras)) {
            if (extra instanceof WiredExtraVariableTextConnector) {
                connectors.add((WiredExtraVariableTextConnector) extra);
            }
        }

        return connectors;
    }

    public static String toText(Room room, int definitionItemId, Integer value) {
        if (value == null) {
            return "";
        }

        for (WiredExtraVariableTextConnector connector : getConnectors(room, definitionItemId, 0)) {
            String resolved = connector.resolveText(value);
            if (!resolved.equals(String.valueOf(value))) return resolved;
        }

        return String.valueOf(value);
    }

    public static Integer toValue(Room room, int definitionItemId, String text) {
        if (text == null) {
            return null;
        }

        String normalizedText = normalizePreservedSpaces(text);

        for (WiredExtraVariableTextConnector connector : getConnectors(room, definitionItemId, 0)) {
            Integer mappedValue = connector.resolveValue(normalizedText);
            if (mappedValue != null) {
                return mappedValue;
            }
        }

        return null;
    }

    public static String toArrayText(Room room, int definitionItemId, int fieldId, Long value) {
        if (value == null) return "";
        for (WiredExtraVariableTextConnector connector : getConnectors(room, definitionItemId, fieldId)) {
            String resolved = connector.resolveText(value.longValue());
            if (!resolved.equals(String.valueOf(value))) return resolved;
        }
        return String.valueOf(value);
    }

    public static Long toArrayValue(Room room, int definitionItemId, int fieldId, String text) {
        if (text == null) return null;
        String normalizedText = normalizePreservedSpaces(text);
        for (WiredExtraVariableTextConnector connector : getConnectors(room, definitionItemId, fieldId)) {
            Long value = connector.resolveLongValue(normalizedText);
            if (value != null) return value;
        }
        return null;
    }

    public static WiredExtraVariableTextConnector getConnector(
            Room room, InteractionWiredExtra definition, int fieldId) {
        List<WiredExtraVariableTextConnector> connectors = getConnectors(room, definition, fieldId);
        return connectors.isEmpty() ? null : connectors.get(0);
    }

    public static WiredExtraVariableTextConnector getConnector(Room room, int definitionItemId, int fieldId) {
        List<WiredExtraVariableTextConnector> connectors = getConnectors(room, definitionItemId, fieldId);
        return connectors.isEmpty() ? null : connectors.get(0);
    }

    public static List<WiredExtraVariableTextConnector> getConnectors(Room room, int definitionItemId, int fieldId) {
        if (room == null || room.getRoomSpecialTypes() == null || definitionItemId <= 0) {
            return Collections.emptyList();
        }
        InteractionWiredExtra extra = room.getRoomSpecialTypes().getExtra(definitionItemId);
        return getConnectors(room, extra, fieldId);
    }

    public static List<WiredExtraVariableTextConnector> getConnectors(
            Room room, InteractionWiredExtra definition, int fieldId) {
        List<WiredExtraVariableTextConnector> connectors = getConnectors(room, definition);
        if (connectors.isEmpty()) return connectors;
        List<WiredExtraVariableTextConnector> matching = new ArrayList<>();
        for (WiredExtraVariableTextConnector connector : connectors) {
            if (definition instanceof WiredArrayVariableDefinition arrayDefinition
                    ? connector.appliesToField(arrayDefinition, fieldId)
                    : connector.appliesToField(fieldId)) matching.add(connector);
        }
        return matching;
    }

    private static String normalizePreservedSpaces(String value) {
        return value.replace(PRESERVED_SPACE, " ");
    }
}
