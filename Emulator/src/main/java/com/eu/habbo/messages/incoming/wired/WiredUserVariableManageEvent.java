package com.eu.habbo.messages.incoming.wired;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.arrays.WiredArrayChange;
import com.eu.habbo.habbohotel.wired.arrays.WiredArrayDefinitionSupport;
import com.eu.habbo.habbohotel.wired.arrays.WiredArrayVariableDefinition;
import com.eu.habbo.habbohotel.wired.arrays.WiredArrayVariableType;
import com.eu.habbo.habbohotel.wired.core.WiredEvent;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.incoming.MessageHandler;

public class WiredUserVariableManageEvent extends MessageHandler {
    private static final int ACTION_ASSIGN = 0;
    private static final int ACTION_REMOVE = 1;
    private static final int TARGET_ROOM = 3;

    @Override
    public void handle() throws Exception {
        Room room = currentRoom();

        if (room == null) {
            return;
        }

        if (!room.canModifyWired(this.client.getHabbo())) {
            room.getRoomVariableManager().sendSnapshot(this.client.getHabbo());
            return;
        }

        if (this.packet.bytesAvailable() < 20) {
            room.getRoomVariableManager().sendSnapshot(this.client.getHabbo());
            return;
        }

        int action = this.packet.readInt();
        int targetType = this.packet.readInt();
        int targetId = this.packet.readInt();
        int definitionItemId = this.packet.readInt();
        int value = this.packet.readInt();

        if (handleArray(room, action, targetType, targetId, definitionItemId)) {
            room.getRoomVariableManager().sendSnapshot(this.client.getHabbo());
            return;
        }

        switch (targetType) {
            case com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectGiveVariable.TARGET_FURNI:
                if (action == ACTION_REMOVE) {
                    room.getFurniVariableManager().removeVariable(targetId, definitionItemId);
                } else {
                    HabboItem furni = room.getHabboItem(targetId);
                    if (furni != null) {
                        room.getFurniVariableManager().assignVariable(furni, definitionItemId, value, true);
                    }
                }
                break;
            case TARGET_ROOM:
                if (action == ACTION_REMOVE) {
                    room.getRoomVariableManager().removeVariable(definitionItemId);
                } else {
                    room.getRoomVariableManager().updateVariableValue(definitionItemId, value);
                }
                break;
            default:
                if (action == ACTION_REMOVE) {
                    room.getUserVariableManager().removeVariable(targetId, definitionItemId);
                } else {
                    Habbo habbo = room.getHabbo(targetId);
                    if (habbo != null) {
                        room.getUserVariableManager().assignVariable(habbo, definitionItemId, value, true);
                    }
                }
                break;
        }

        room.getRoomVariableManager().sendSnapshot(this.client.getHabbo());
    }

    private boolean handleArray(Room room, int action, int targetType, int targetId, int definitionItemId) {
        int arrayType = WiredArrayVariableType.USER.code();
        if (targetType
                == com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectGiveVariable.TARGET_FURNI) {
            arrayType = WiredArrayVariableType.FURNI.code();
        } else if (targetType == TARGET_ROOM) {
            arrayType = WiredArrayVariableType.ROOM.code();
        }
        WiredArrayVariableDefinition definition =
                WiredArrayDefinitionSupport.resolve(room, arrayType, definitionItemId);
        if (definition == null || !definition.isArray()) return false;

        int ownerId;
        Habbo habbo = null;
        HabboItem item = null;
        if (definition.getArrayVariableType() == WiredArrayVariableType.ROOM) {
            ownerId = definition.getArrayStorageRoomId(room.getId());
        } else if (definition.getArrayVariableType() == WiredArrayVariableType.FURNI) {
            item = room.getHabboItem(targetId);
            if (item == null) return true;
            ownerId = item.getId();
        } else {
            habbo = room.getHabbo(targetId);
            if (habbo == null || habbo.getHabboInfo() == null) return true;
            ownerId = habbo.getHabboInfo().getId();
        }

        boolean changed = action == ACTION_REMOVE
                ? room.getArrayVariableManager().remove(definition, ownerId)
                : room.getArrayVariableManager().give(definition, ownerId, true).changed();
        if (!changed) return true;

        WiredEvent.Builder event = WiredEvent.builder(WiredEvent.Type.VARIABLE_CHANGED, room)
                .actor(habbo == null ? null : habbo.getRoomUnit())
                .sourceItem(item)
                .variableTargetType(targetType)
                .variableDefinitionItemId(definition.getId());
        if (action == ACTION_REMOVE) event.variableDeleted(true);
        else event.arrayChange(WiredArrayChange.created()).variableCreated(true);
        WiredManager.handleEvent(event.build());
        return true;
    }

    @Override
    public int getRatelimit() {
        return 150;
    }
}
