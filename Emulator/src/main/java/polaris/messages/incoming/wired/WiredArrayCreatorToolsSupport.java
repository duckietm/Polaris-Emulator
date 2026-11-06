package polaris.messages.incoming.wired;

import polaris.hotel.items.interactions.wired.extra.WiredExtraVariableReference;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.users.User;
import polaris.hotel.users.UserItem;
import polaris.hotel.wired.arrays.WiredArrayDefinitionSupport;
import polaris.hotel.wired.arrays.WiredArrayVariableDefinition;
import polaris.hotel.wired.arrays.WiredArrayVariableType;

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
                User user = room.getUserByRoomUnitId(requestedOwnerId);
                if (user == null) user = room.getUser(requestedOwnerId);
                if (user == null || user.getUserInfo() == null) yield null;
                RoomUnit unit = user.getRoomUnit();
                yield new Resolved(
                        definition, "user", requestedOwnerId, user.getUserInfo().getId(), unit, null);
            }
            case FURNI -> {
                UserItem item = room.getUserItem(requestedOwnerId);
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
            UserItem item) {
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
