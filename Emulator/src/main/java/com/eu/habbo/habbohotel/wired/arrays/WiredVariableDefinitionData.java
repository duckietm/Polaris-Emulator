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
}
