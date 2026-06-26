package com.eu.habbo.messages.incoming.wired;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
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

        switch (targetType) {
            case com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectGiveVariable.TARGET_FURNI -> {
                if (action == ACTION_REMOVE) {
                    room.getFurniVariableManager().removeVariable(targetId, definitionItemId);
                } else {
                    HabboItem furni = room.getHabboItem(targetId);
                    if (furni != null) {
                        room.getFurniVariableManager().assignVariable(furni, definitionItemId, value, true);
                    }
                }
            }
            case TARGET_ROOM -> {
                if (action == ACTION_REMOVE) {
                    room.getRoomVariableManager().removeVariable(definitionItemId);
                } else {
                    room.getRoomVariableManager().updateVariableValue(definitionItemId, value);
                }
            }
            default -> {
                if (action == ACTION_REMOVE) {
                    room.getUserVariableManager().removeVariable(targetId, definitionItemId);
                } else {
                    Habbo habbo = room.getHabbo(targetId);
                    if (habbo != null) {
                        room.getUserVariableManager().assignVariable(habbo, definitionItemId, value, true);
                    }
                }
            }
        }

        room.getRoomVariableManager().sendSnapshot(this.client.getHabbo());
    }

    @Override
    public int getRatelimit() {
        return 150;
    }
}
