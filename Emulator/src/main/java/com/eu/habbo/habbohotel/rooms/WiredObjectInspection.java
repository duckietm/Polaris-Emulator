package com.eu.habbo.habbohotel.rooms;

import com.eu.habbo.habbohotel.items.interactions.InteractionWired;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One object's wired variable values for the inspection tab, as the official {@code WiredObjectInspectionData}:
 * a furni (by item id), a user (by room index) or the room, its variable values keyed by the same string ids as
 * {@link RoomWiredVariableCatalog}, and for a furni the wired boxes that pick it.
 */
public final class WiredObjectInspection {
    public static final int TYPE_FURNI = 0;
    public static final int TYPE_USER = 1;
    public static final int TYPE_ROOM = -10;

    private final int type;
    private final int objectId;
    private final Map<String, Integer> values;
    private final List<Integer> configuredInWireds;

    WiredObjectInspection(int type, int objectId, Map<String, Integer> values, List<Integer> configuredInWireds) {
        this.type = type;
        this.objectId = objectId;
        this.values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
        this.configuredInWireds = List.copyOf(configuredInWireds);
    }

    public int getType() {
        return this.type;
    }

    /** The furni's item id or the user's room index; 0 for the room. */
    public int getObjectId() {
        return this.objectId;
    }

    public Map<String, Integer> getValues() {
        return this.values;
    }

    /** Ids of the wired boxes that pick the furni; empty for a user or the room. */
    public List<Integer> getConfiguredInWireds() {
        return this.configuredInWireds;
    }

    /** The values of the object, or null when the room holds no such object. */
    public static WiredObjectInspection inspect(Room room, int type, int objectId) {
        if (room == null) {
            return null;
        }
        if (type == TYPE_FURNI) {
            if (room.getHabboItem(objectId) == null) {
                return null;
            }
            return new WiredObjectInspection(
                    type, objectId, furniValues(room, objectId), configuredInWireds(room, objectId));
        }
        if (type == TYPE_USER) {
            RoomUnit unit = unitByRoomIndex(room, objectId);
            int key = UserVariableHolders.of(room, unit);
            if (key == 0) {
                return null;
            }
            return new WiredObjectInspection(type, objectId, userValues(room, key), List.of());
        }
        if (type == TYPE_ROOM) {
            return new WiredObjectInspection(type, 0, roomValues(room), List.of());
        }
        return null;
    }

    private static Map<String, Integer> furniValues(Room room, int furniId) {
        Map<String, Integer> values = new LinkedHashMap<>();
        for (RoomFurniVariableManager.FurniAssignmentsEntry furni :
                room.getFurniVariableManager().createSnapshot().getFurnis()) {
            if (furni.getFurniId() != furniId) {
                continue;
            }
            for (RoomFurniVariableManager.AssignmentEntry assignment : furni.getAssignments()) {
                values.put(
                        RoomWiredVariableCatalog.variableId(
                                RoomWiredVariableCatalog.TARGET_FURNI, assignment.getVariableItemId()),
                        assignment.hasValue() ? assignment.getValue() : 0);
            }
        }
        return values;
    }

    private static Map<String, Integer> userValues(Room room, int holderKey) {
        Map<String, Integer> values = new LinkedHashMap<>();
        for (RoomUserVariableManager.UserAssignmentsEntry user :
                room.getUserVariableManager().createSnapshot().getUsers()) {
            if (user.getUserId() != holderKey) {
                continue;
            }
            for (RoomUserVariableManager.AssignmentEntry assignment : user.getAssignments()) {
                values.put(
                        RoomWiredVariableCatalog.variableId(
                                RoomWiredVariableCatalog.TARGET_USER, assignment.getVariableItemId()),
                        assignment.hasValue() ? assignment.getValue() : 0);
            }
        }
        return values;
    }

    private static Map<String, Integer> roomValues(Room room) {
        Map<String, Integer> values = new LinkedHashMap<>();
        for (RoomVariableManager.AssignmentEntry assignment :
                room.getRoomVariableManager().createSnapshot().getAssignments()) {
            if (!assignment.hasValue()) {
                continue;
            }
            values.put(
                    RoomWiredVariableCatalog.variableId(
                            RoomWiredVariableCatalog.TARGET_ROOM, assignment.getVariableItemId()),
                    assignment.getValue());
        }
        return values;
    }

    private static RoomUnit unitByRoomIndex(Room room, int roomIndex) {
        for (RoomUnit unit : room.getRoomUnits()) {
            if (unit != null && unit.getId() == roomIndex) {
                return unit;
            }
        }
        return null;
    }

    /** The wired boxes whose stored picks ({@code itemIds} in their saved data) include the furni. */
    static List<Integer> configuredInWireds(Room room, int furniId) {
        List<Integer> boxes = new ArrayList<>();
        RoomSpecialTypes types = room.getRoomSpecialTypes();
        if (types == null) {
            return boxes;
        }
        List<Collection<? extends InteractionWired>> groups =
                List.of(types.getTriggers(), types.getConditions(), types.getEffects(), types.getExtras());
        for (Collection<? extends InteractionWired> group : groups) {
            for (InteractionWired box : group) {
                if (box != null && picks(box.getWiredData(), furniId)) {
                    boxes.add(box.getId());
                }
            }
        }
        Collections.sort(boxes);
        return boxes;
    }

    /** Whether saved wired data (JSON) lists the furni among its {@code itemIds}. */
    static boolean picks(String wiredData, int furniId) {
        if (wiredData == null || !wiredData.startsWith("{")) {
            return false;
        }
        try {
            JsonElement parsed = JsonParser.parseString(wiredData);
            if (!parsed.isJsonObject()) {
                return false;
            }
            JsonObject data = parsed.getAsJsonObject();
            if (!data.has("itemIds") || !data.get("itemIds").isJsonArray()) {
                return false;
            }
            JsonArray ids = data.getAsJsonArray("itemIds");
            for (JsonElement id : ids) {
                if (id.isJsonPrimitive() && id.getAsJsonPrimitive().isNumber() && id.getAsInt() == furniId) {
                    return true;
                }
            }
        } catch (RuntimeException ignored) {
            return false;
        }
        return false;
    }
}
