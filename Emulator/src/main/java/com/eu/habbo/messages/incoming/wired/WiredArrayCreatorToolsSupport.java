package com.eu.habbo.messages.incoming.wired;

import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraVariableReference;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.arrays.WiredArrayDefinitionSupport;
import com.eu.habbo.habbohotel.wired.arrays.WiredArrayVariableDefinition;
import com.eu.habbo.habbohotel.wired.arrays.WiredArrayVariableType;

final class WiredArrayCreatorToolsSupport {
    private WiredArrayCreatorToolsSupport() {}

    static Resolved resolve(Room room, int variableTypeCode, int requestedOwnerId, int definitionItemId) {
        if (room == null || definitionItemId <= 0 || !isSupportedType(variableTypeCode)) return null;
        WiredArrayVariableType type = WiredArrayVariableType.fromCode(variableTypeCode);
        WiredArrayVariableDefinition definition =
                WiredArrayDefinitionSupport.resolve(room, variableTypeCode, definitionItemId);
        if (definition == null || !definition.isArray()) return null;

        return switch (type) {
            case ROOM -> {
                if (requestedOwnerId != 0 && requestedOwnerId != room.getId()) yield null;
                yield new Resolved(
                        definition,
                        "room",
                        requestedOwnerId,
                        definition.getArrayStorageRoomId(room.getId()),
                        null,
                        null);
            }
            case USER -> {
                Habbo habbo = room.getHabboByRoomUnitId(requestedOwnerId);
                if (habbo == null) habbo = room.getHabbo(requestedOwnerId);
                if (habbo == null || habbo.getHabboInfo() == null) yield null;
                RoomUnit unit = habbo.getRoomUnit();
                yield new Resolved(
                        definition,
                        "user",
                        requestedOwnerId,
                        habbo.getHabboInfo().getId(),
                        unit,
                        null);
            }
            case FURNI -> {
                HabboItem item = room.getHabboItem(requestedOwnerId);
                if (item == null) yield null;
                yield new Resolved(definition, "furni", requestedOwnerId, item.getId(), null, item);
            }
            case CONTEXT -> null;
        };
    }

    private static boolean isSupportedType(int variableTypeCode) {
        return variableTypeCode == WiredArrayVariableType.ROOM.code()
                || variableTypeCode == WiredArrayVariableType.USER.code()
                || variableTypeCode == WiredArrayVariableType.FURNI.code();
    }

    record Resolved(
            WiredArrayVariableDefinition definition,
            String requestedOwnerType,
            int requestedOwnerId,
            int ownerId,
            RoomUnit unit,
            HabboItem item) {
        int legacyTargetType() {
            return switch (this.definition.getArrayVariableType()) {
                case USER -> 0;
                case FURNI -> 1;
                case CONTEXT -> 2;
                case ROOM -> 3;
            };
        }

        boolean referenced() {
            return this.definition instanceof WiredExtraVariableReference;
        }
    }
}
