package com.eu.habbo.habbohotel.wired.arrays;

import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class WiredArrayDefinitionSupport {
    private WiredArrayDefinitionSupport() {}

    public static WiredVariableDefinitionData readEditorData(String rawValue) {
        if (rawValue == null || !rawValue.trim().startsWith("{")) {
            return WiredVariableDefinitionData.scalar(rawValue);
        }
        try {
            WiredVariableDefinitionData data =
                    WiredManager.getGson().fromJson(rawValue, WiredVariableDefinitionData.class);
            if (data == null) throw new IllegalArgumentException("Invalid variable definition metadata.");
            if (data.fields == null) data.fields = new ArrayList<>();
            return data;
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Invalid variable definition metadata.", exception);
        }
    }

    public static WiredArrayDefinition parseArrayDefinition(WiredVariableDefinitionData data) {
        return WiredArrayDefinition.fromData(data, WiredArraySettings.maxEntries());
    }

    public static String editorString(String name, WiredArrayDefinition definition) {
        if (definition == null) return name == null ? "" : name;
        WiredVariableDefinitionData data = WiredVariableDefinitionData.array(name, definition);
        data.serverMaxEntries = WiredArraySettings.maxEntries();
        data.serverMaxPopulatedCells = WiredArraySettings.maxPopulatedCellsPerOwner();
        return WiredManager.getGson().toJson(data);
    }

    public static WiredArrayVariableDefinition resolve(Room room, int variableType, int definitionItemId) {
        if (room == null || room.getRoomSpecialTypes() == null || definitionItemId <= 0) return null;
        InteractionWiredExtra extra = room.getRoomSpecialTypes().getExtra(definitionItemId);
        if (!(extra instanceof WiredArrayVariableDefinition definition)) return null;
        return definition.getArrayVariableType() == WiredArrayVariableType.fromCode(variableType) ? definition : null;
    }

    public static List<EditorDefinition> collect(Room room) {
        List<EditorDefinition> result = new ArrayList<>();
        if (room == null || room.getRoomSpecialTypes() == null) return result;
        for (InteractionWiredExtra extra : room.getRoomSpecialTypes().getExtras()) {
            if (!(extra instanceof WiredArrayVariableDefinition definition)) continue;
            if (definition.getVariableName() == null
                    || definition.getVariableName().isBlank()) continue;
            result.add(EditorDefinition.from(definition));
        }
        result.sort(Comparator.comparingInt(EditorDefinition::variableType)
                .thenComparing(EditorDefinition::name, String.CASE_INSENSITIVE_ORDER)
                .thenComparingInt(EditorDefinition::itemId));
        return result;
    }

    public record EditorDefinition(
            int itemId,
            String name,
            int variableType,
            String valueShape,
            String arrayFormat,
            String arrayMode,
            int maxEntries,
            List<WiredArrayFieldDefinition> fields,
            boolean permanent,
            boolean hasValue) {
        static EditorDefinition from(WiredArrayVariableDefinition definition) {
            WiredArrayDefinition array = definition.getArrayDefinition();
            return new EditorDefinition(
                    definition.getId(),
                    definition.getVariableName(),
                    definition.getArrayVariableType().code(),
                    array == null ? "single" : "array",
                    array == null
                            ? WiredArrayFormat.SIMPLE.wireName()
                            : array.getFormat().wireName(),
                    array == null
                            ? WiredArrayMode.LIST.wireName()
                            : array.getMode().wireName(),
                    array == null ? 0 : array.getMaxEntries(),
                    array == null ? List.of() : array.getFields(),
                    definition.isArrayPermanent(),
                    definition.hasValue());
        }
    }
}
