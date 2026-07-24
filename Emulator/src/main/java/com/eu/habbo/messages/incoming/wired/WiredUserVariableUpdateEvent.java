package com.eu.habbo.messages.incoming.wired;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectGiveVariable;
import com.eu.habbo.messages.incoming.MessageHandler;

public class WiredUserVariableUpdateEvent extends MessageHandler {
    private static final int TARGET_ROOM = 3;

    @Override
    public void handle() throws Exception {
        Room room = currentRoom();

        if (room == null) {
            return;
        }

        if (!room.canModifyWired(this.client.getHabbo())) {
            room.getUserVariableManager().sendSnapshot(this.client.getHabbo());
            return;
        }

        if (this.packet.bytesAvailable() < 16) {
            room.getUserVariableManager().sendSnapshot(this.client.getHabbo());
            return;
        }

        int targetType = this.packet.readInt();
        int targetId = this.packet.readInt();
        int definitionItemId = this.packet.readInt();
        int value = this.packet.readInt();

        if (targetType == WiredEffectGiveVariable.TARGET_FURNI) {
            room.getFurniVariableManager().updateVariableValue(targetId, definitionItemId, value);
            room.getFurniVariableManager().sendSnapshot(this.client.getHabbo());
            return;
        }

        if (targetType == TARGET_ROOM) {
            room.getRoomVariableManager().updateVariableValue(definitionItemId, value);
            room.getRoomVariableManager().sendSnapshot(this.client.getHabbo());
            return;
        }

        room.getUserVariableManager().updateVariableValue(targetId, definitionItemId, value);
        room.getUserVariableManager().sendSnapshot(this.client.getHabbo());
    }

    @Override
    public int getRatelimit() {
        return 150;
    }
}
