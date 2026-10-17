package com.eu.habbo.habbohotel.wired.core;

import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraVariableTextConnector;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.wired.arrays.WiredArrayVariableDefinition;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    /**
     * The value-to-text table the definition's text connectors give it, in connector execution order
     * with the first connector naming a value winning, exactly as {@link #toText} resolves it. Empty
     * when the definition has no connector, so callers can tell "connected" from "named nothing".
     */
    public static Map<Integer, String> mappings(Room room, int definitionItemId) {
        List<WiredExtraVariableTextConnector> connectors = getConnectors(room, definitionItemId, 0);
        if (connectors.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<Integer, String> merged = new LinkedHashMap<>();

        for (WiredExtraVariableTextConnector connector : connectors) {
            for (Map.Entry<Integer, String> mapping : connector.getMappings().entrySet()) {
                if (mapping.getKey() == null || mapping.getValue() == null) {
                    continue;
                }

                merged.putIfAbsent(mapping.getKey(), mapping.getValue());
            }
        }

        return Collections.unmodifiableMap(merged);
    }

    public static String toText(Room room, int definitionItemId, Integer value) {
        if (value == null) {
            return "";
        }

        List<WiredExtraVariableTextConnector> connectors = getConnectors(room, definitionItemId, 0);
        for (WiredExtraVariableTextConnector connector : connectors) {
            String resolved = connector.resolveText(value);
            if (!resolved.equals(String.valueOf(value))) return resolved;
        }

        return fallback(connectors, String.valueOf(value));
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
        List<WiredExtraVariableTextConnector> connectors = getConnectors(room, definitionItemId, fieldId);
        for (WiredExtraVariableTextConnector connector : connectors) {
            String resolved = connector.resolveText(value.longValue());
            if (!resolved.equals(String.valueOf(value))) return resolved;
        }
        return fallback(connectors, String.valueOf(value));
    }

    /**
     * The text a value no connector names reads as: the first connector's "_=" text, or null when none
     * sets one (the official {@code WiredVariable.textConnectorFallback}).
     */
    public static String fallbackText(Room room, int definitionItemId) {
        for (WiredExtraVariableTextConnector connector : getConnectors(room, definitionItemId, 0)) {
            String fallback = connector.getFallbackText();
            if (fallback != null) return fallback;
        }
        return null;
    }

    /** The first connector's "_=" text (official), else the number itself. */
    private static String fallback(List<WiredExtraVariableTextConnector> connectors, String number) {
        for (WiredExtraVariableTextConnector connector : connectors) {
            String fallback = connector.getFallbackText();
            if (fallback != null) return fallback;
        }
        return number;
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
