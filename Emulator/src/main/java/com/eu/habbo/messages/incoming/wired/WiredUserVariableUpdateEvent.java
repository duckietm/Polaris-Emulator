package com.eu.habbo.messages.incoming.wired;

import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectGiveVariable;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.wired.WiredVariableChangeOrigin;
import com.eu.habbo.habbohotel.wired.core.WiredInternalVariableSupport;
import com.eu.habbo.messages.incoming.MessageHandler;

public class WiredUserVariableUpdateEvent extends MessageHandler {
    private static final int TARGET_ROOM = 3;

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
            String variableToken = "";

            if (this.packet.bytesAvailable() > 0) {
                if (this.packet.bytesAvailable() < 2
                        || this.packet.bytesAvailable() > 66
                        || this.packet
                                        .getBuffer()
                                        .getUnsignedShort(
                                                this.packet.getBuffer().readerIndex())
                                != this.packet.bytesAvailable() - 2) {
                    return;
                }

                variableToken = this.packet.readString();
            }

            if (!variableToken.isEmpty()) {
                if (definitionItemId == 0) {
                    updateInternalVariable(room, targetType, targetId, variableToken, value);
                }

                if (targetType == TARGET_ROOM) {
                    room.getRoomVariableManager().sendSnapshot(this.client.getHabbo());
                } else {
                    room.getUserVariableManager().sendSnapshot(this.client.getHabbo());
                }

                return;
            }

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
        } finally {
            WiredVariableChangeOrigin.exit(previousOrigin);
        }
    }

    private static void updateInternalVariable(Room room, int targetType, int targetId, String token, int value) {
        if (!token.startsWith("internal:")) {
            return;
        }

        String key = WiredInternalVariableSupport.normalizeKey(token.substring("internal:".length()));

        if (targetType == TARGET_ROOM) {
            if ((targetId == 0 || targetId == room.getId())
                    && WiredInternalVariableSupport.canUseRoomDestination(key)) {
                WiredInternalVariableSupport.writeRoomValue(room, key, value);
            }

            return;
        }

        if (targetType == WiredEffectGiveVariable.TARGET_USER && "@altitude".equals(key)) {
            Habbo target = room.getHabbo(targetId);

            if (target != null && target.getRoomUnit() != null) {
                WiredInternalVariableSupport.writeUserValue(room, target.getRoomUnit(), key, value);
            }
        }
    }

    @Override
    public int getRatelimit() {
        return 150;
    }
}
