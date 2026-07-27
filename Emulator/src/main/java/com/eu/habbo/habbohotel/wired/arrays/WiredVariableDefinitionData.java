package com.eu.habbo.habbohotel.wired.arrays;

import java.util.ArrayList;
import java.util.List;

/** Versioned editor and persistence payload for scalar and array variable definitions. */
public final class WiredVariableDefinitionData {
    public String name = "";
    public String valueShape = "single";
    public String arrayFormat = WiredArrayFormat.SIMPLE.wireName();
    public String arrayMode = WiredArrayMode.LIST.wireName();
    public int maxEntries = WiredArrayDefinition.DEFAULT_MAX_ENTRIES;
    public int nextFieldId = 2;
    public List<WiredArrayFieldDefinition> fields = new ArrayList<>();
    public int schemaVersion;
    public int serverMaxEntries;
    public int serverMaxPopulatedCells;

    public boolean isArray() {
        return "array".equalsIgnoreCase(this.valueShape);
    }

    public static WiredVariableDefinitionData scalar(String name) {
        WiredVariableDefinitionData data = new WiredVariableDefinitionData();
        data.name = name == null ? "" : name;
        return data;
    }

    public static WiredVariableDefinitionData array(String name, WiredArrayDefinition definition) {
        WiredVariableDefinitionData data = scalar(name);
        data.valueShape = "array";
        data.arrayFormat = definition.getFormat().wireName();
        data.arrayMode = definition.getMode().wireName();
        data.maxEntries = definition.getMaxEntries();
        data.nextFieldId = definition.getNextFieldId();
        data.fields = new ArrayList<>(definition.getFields());
        data.schemaVersion = WiredArrayDefinition.SCHEMA_VERSION;
        return data;
    }

    public static WiredVariableDefinitionData copyOf(WiredVariableDefinitionData source) {
        if (source == null) return null;
        WiredVariableDefinitionData copy = new WiredVariableDefinitionData();
        copy.name = source.name;
        copy.valueShape = source.valueShape;
        copy.arrayFormat = source.arrayFormat;
        copy.arrayMode = source.arrayMode;
        copy.maxEntries = source.maxEntries;
        copy.nextFieldId = source.nextFieldId;
        copy.fields = source.fields == null ? new ArrayList<>() : new ArrayList<>(source.fields);
        copy.schemaVersion = source.schemaVersion;
        copy.serverMaxEntries = source.serverMaxEntries;
        copy.serverMaxPopulatedCells = source.serverMaxPopulatedCells;
        return copy;
    }
}
