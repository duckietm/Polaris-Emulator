package com.eu.habbo.habbohotel.wired.arrays;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.wired.core.WiredVariableTextConnectorSupport;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** One explicit, bounded Creator Tools owner-array inspection response. */
public final class WiredCreatorToolsArrayInspection {
    public static final int PROTOCOL_VERSION = 1;
    public static final int DEFAULT_PAGE_SIZE = 25;
    public static final int MAX_PAGE_SIZE = 50;

    public int protocolVersion = PROTOCOL_VERSION;
    public String requestedOwnerType = "";
    public int requestedOwnerId;
    public int ownerId;
    public boolean hasArray;
    public Definition definition;
    public int logicalLength;
    public int occupiedCount;
    public int page;
    public int pageSize;
    public int pageCount;
    public int startIndex;
    public int endIndex;
    public int totalIndexes;
    public List<Entry> entries = new ArrayList<>();

    private WiredCreatorToolsArrayInspection() {}

    public static WiredCreatorToolsArrayInspection create(
            Room room,
            String requestedOwnerType,
            int requestedOwnerId,
            WiredArrayVariableDefinition variable,
            int ownerId,
            int requestedPage,
            int requestedPageSize) {
        WiredCreatorToolsArrayInspection result = new WiredCreatorToolsArrayInspection();
        result.requestedOwnerType = requestedOwnerType == null ? "" : requestedOwnerType;
        result.requestedOwnerId = requestedOwnerId;
        result.ownerId = ownerId;
        if (room == null || variable == null || !variable.isArray() || ownerId <= 0) return result;

        result.definition = Definition.from(room, variable);
        WiredArrayValue value = room.getArrayVariableManager().getValue(variable, ownerId);
        result.hasArray = value != null;
        result.logicalLength = value == null ? 0 : value.getLogicalLength();
        result.occupiedCount = value == null ? 0 : value.getOccupiedCount();

        int normalizedPageSize =
                requestedPageSize <= 0 ? DEFAULT_PAGE_SIZE : Math.min(MAX_PAGE_SIZE, requestedPageSize);
        result.totalIndexes = value == null
                ? 0
                : variable.getArrayDefinition().getMode() == WiredArrayMode.LIST
                        ? value.getLogicalLength()
                        : variable.getArrayDefinition().getMaxEntries();
        result.pageCount =
                Math.max(1, (int) (((long) result.totalIndexes + normalizedPageSize - 1L) / normalizedPageSize));
        result.page = Math.max(0, Math.min(requestedPage, result.pageCount - 1));
        result.pageSize = normalizedPageSize;
        result.startIndex = (int) Math.min(Integer.MAX_VALUE, (long) result.page * normalizedPageSize);
        result.endIndex = Math.min(result.totalIndexes, result.startIndex + normalizedPageSize);

        if (value == null) return result;
        for (int index = result.startIndex; index < result.endIndex; index++) {
            WiredArrayEntry arrayEntry = value.getEntry(index);
            Entry entry = new Entry(index, arrayEntry != null);
            if (arrayEntry != null) {
                for (WiredArrayFieldDefinition field :
                        variable.getArrayDefinition().getFields()) {
                    Long fieldValue = arrayEntry.getValue(field.getId());
                    if (fieldValue == null) continue;
                    String key = Integer.toString(field.getId());
                    entry.values.put(key, Long.toString(fieldValue));
                    if (WiredVariableTextConnectorSupport.getConnector(room, variable.getId(), field.getId()) != null) {
                        entry.connectedText.put(
                                key,
                                WiredVariableTextConnectorSupport.toArrayText(
                                        room, variable.getId(), field.getId(), fieldValue));
                    }
                }
            }
            result.entries.add(entry);
        }
        return result;
    }

    public static final class Definition {
        public int itemId;
        public int variableType;
        public String name = "";
        public String valueShape = "array";
        public String arrayFormat = "simple";
        public String arrayMode = "list";
        public int maxEntries;
        public int schemaVersion = WiredArrayDefinition.SCHEMA_VERSION;
        public boolean inspectable;
        public boolean referenced;
        public boolean writable;
        public List<Field> fields = new ArrayList<>();

        private static Definition from(Room room, WiredArrayVariableDefinition variable) {
            Definition result = new Definition();
            WiredArrayDefinition array = variable.getArrayDefinition();
            result.itemId = variable.getId();
            result.variableType = variable.getArrayVariableType().code();
            result.name = variable.getVariableName();
            result.arrayFormat = array.getFormat().wireName();
            result.arrayMode = array.getMode().wireName();
            result.maxEntries = array.getMaxEntries();
            result.inspectable = variable.getArrayVariableType() != WiredArrayVariableType.CONTEXT;
            result.referenced = variable.getArrayStorageDefinitionItemId() != variable.getId()
                    || variable.getArrayStorageRoomId(room.getId()) != room.getId();
            result.writable = variable.isArrayWritable();
            for (WiredArrayFieldDefinition field : array.getFields()) {
                result.fields.add(new Field(
                        field.getId(),
                        field.getName(),
                        field.getOrder(),
                        WiredVariableTextConnectorSupport.getConnector(room, variable.getId(), field.getId()) != null));
            }
            return result;
        }
    }

    public static final class Field {
        public int id;
        public String name = "";
        public int order;
        public boolean textConnected;

        private Field(int id, String name, int order, boolean textConnected) {
            this.id = id;
            this.name = name;
            this.order = order;
            this.textConnected = textConnected;
        }
    }

    public static final class Entry {
        public int index;
        public boolean occupied;
        public Map<String, String> values = new LinkedHashMap<>();
        public Map<String, String> connectedText = new LinkedHashMap<>();

        private Entry(int index, boolean occupied) {
            this.index = index;
            this.occupied = occupied;
        }
    }
}
