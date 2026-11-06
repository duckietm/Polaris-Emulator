package polaris.messages.incoming.wired;

import polaris.hotel.rooms.BuildersClubItemIds;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.variables.RoomWiredVariableWrites;
import polaris.hotel.users.User;
import polaris.hotel.users.UserItem;
import polaris.hotel.wired.WiredVariableChangeOrigin;
import polaris.hotel.wired.WiredVariableClearPolicy;
import polaris.hotel.wired.arrays.WiredArrayChange;
import polaris.hotel.wired.arrays.WiredArrayDefinitionSupport;
import polaris.hotel.wired.arrays.WiredArrayVariableDefinition;
import polaris.hotel.wired.arrays.WiredArrayVariableType;
import polaris.hotel.wired.core.WiredEvent;
import polaris.hotel.wired.core.WiredManager;
import polaris.messages.incoming.MessageHandler;

public class WiredUserVariableManageEvent extends MessageHandler {
    private static final int ACTION_ASSIGN = 0;
    private static final int ACTION_REMOVE = 1;
    private static final int ACTION_CLEAR_ALL = 2;
    private static final int TARGET_ROOM = RoomWiredVariableWrites.TARGET_ROOM;

    @Override
    public void handle() throws Exception {
        // Every write below is a creator-tool write, whatever the manager it lands in. The packet
        // is read here, in handle(), where the packet-contract catalogue looks for it.
        int previousOrigin = WiredVariableChangeOrigin.enter(WiredVariableChangeOrigin.CREATOR_TOOLS);
        try {
            Room room = currentRoom();

            if (room == null) {
                return;
            }

            if (!room.canModifyWired(this.client.getUser())) {
                room.getRoomVariableManager().sendSnapshot(this.client.getUser());
                return;
            }

            if (this.packet.bytesAvailable() < 20) {
                room.getRoomVariableManager().sendSnapshot(this.client.getUser());
                return;
            }

            int action = this.packet.readInt();
            int targetType = this.packet.readInt();
            int targetId = BuildersClubItemIds.fromWire(
                    room, this.packet.readInt(), targetType == RoomWiredVariableWrites.TARGET_FURNI);
            int definitionItemId = this.packet.readInt();
            int value = this.packet.readInt();

            if (action == ACTION_CLEAR_ALL) {
                // Reaches holders who are not in the room, so the policy is stricter than wired rights.
                if (WiredVariableClearPolicy.canClear(room, this.client.getUser(), definitionItemId)) {
                    RoomWiredVariableWrites.clearAll(room, targetType, definitionItemId);
                }
                room.getRoomVariableManager().sendSnapshot(this.client.getUser());
                return;
            }

            if (handleArray(room, action, targetType, targetId, definitionItemId)) {
                room.getRoomVariableManager().sendSnapshot(this.client.getUser());
                return;
            }

            if (action == ACTION_REMOVE) {
                if (targetType == RoomWiredVariableWrites.TARGET_FURNI
                        || targetType == RoomWiredVariableWrites.TARGET_ROOM
                        || RoomWiredVariableWrites.mayManageUser(
                                room, this.client.getUser(), targetId, definitionItemId)) {
                    RoomWiredVariableWrites.remove(room, targetType, targetId, definitionItemId);
                }
            } else {
                RoomWiredVariableWrites.assign(room, targetType, targetId, definitionItemId, value);
            }

            room.getRoomVariableManager().sendSnapshot(this.client.getUser());
        } finally {
            WiredVariableChangeOrigin.exit(previousOrigin);
        }
    }

    private boolean handleArray(Room room, int action, int targetType, int targetId, int definitionItemId) {
        int arrayType = WiredArrayVariableType.USER.code();
        if (targetType == polaris.hotel.items.interactions.wired.effects.WiredEffectGiveVariable.TARGET_FURNI) {
            arrayType = WiredArrayVariableType.FURNI.code();
        } else if (targetType == TARGET_ROOM) {
            arrayType = WiredArrayVariableType.ROOM.code();
        }
        WiredArrayVariableDefinition definition =
                WiredArrayDefinitionSupport.resolve(room, arrayType, definitionItemId);
        if (definition == null || !definition.isArray()) return false;

        int ownerId;
        User user = null;
        UserItem item = null;
        if (definition.getArrayVariableType() == WiredArrayVariableType.ROOM) {
            ownerId = definition.getArrayStorageRoomId(room.getId());
        } else if (definition.getArrayVariableType() == WiredArrayVariableType.FURNI) {
            item = room.getUserItem(targetId);
            if (item == null) return true;
            ownerId = item.getId();
        } else {
            user = room.getUser(targetId);
            if (user == null || user.getUserInfo() == null) return true;
            ownerId = user.getUserInfo().getId();
        }

        var outcome =
                action == ACTION_REMOVE ? null : room.getArrayVariableManager().give(definition, ownerId, true);
        boolean changed = action == ACTION_REMOVE
                ? room.getArrayVariableManager().remove(definition, ownerId)
                : outcome.changed();
        if (!changed) return true;

        WiredEvent.Builder event = WiredEvent.builder(WiredEvent.Type.VARIABLE_CHANGED, room)
                .actor(user == null ? null : user.getRoomUnit())
                .sourceItem(item)
                .variableTargetType(targetType)
                .variableDefinitionItemId(definition.getId());
        if (action == ACTION_REMOVE) event.variableDeleted(true);
        else if (outcome.previousValue() == null)
            event.arrayChange(WiredArrayChange.created()).variableCreated(true);
        else
            event.arrayChange(WiredArrayChange.structural(
                    polaris.hotel.wired.arrays.WiredArrayStructuralOperation.CLEAR,
                    0,
                    0,
                    outcome.previousValue().getLengthForCondition(),
                    0));
        WiredManager.handleEvent(event.build());
        return true;
    }

    @Override
    public int getRatelimit() {
        return 150;
    }
}
