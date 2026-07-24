package com.eu.habbo.messages.incoming.wired;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.wired.WiredFurniRuntimeStateComposer;

public class WiredFurniRuntimeStateRequestEvent extends MessageHandler {
    private static final int MINIMUM_PACKET_BYTES = 14;

    @Override
    public void handle() throws Exception {
        Room room = currentRoom();
        if (room == null || !room.canInspectWired(this.client.getHabbo())) {
            return;
        }
        if (this.packet.bytesAvailable() < MINIMUM_PACKET_BYTES) {
            return;
        }

        int itemId = this.packet.readInt();
        int action = this.packet.readInt();
        String key = this.packet.readString();
        if (key.length() > WiredFurniRuntimeStatePolicy.MAX_KEY_LENGTH || this.packet.bytesAvailable() < 4) {
            return;
        }
        int requestedValue = this.packet.readInt();

        HabboItem item = itemId > 0 ? room.getHabboItem(itemId) : null;
        WiredFurniRuntimeStatePolicy.Result result;
        if (action == WiredFurniRuntimeStatePolicy.ACTION_READ) {
            result = WiredFurniRuntimeStatePolicy.read(room, item, key);
        } else if (action == WiredFurniRuntimeStatePolicy.ACTION_WRITE && room.canModifyWired(this.client.getHabbo())) {
            result = WiredFurniRuntimeStatePolicy.write(room, item, key, requestedValue);
        } else {
            result = WiredFurniRuntimeStatePolicy.read(room, item, key);
            result = new WiredFurniRuntimeStatePolicy.Result(result.value(), result.supported(), false);
        }

        this.client.sendResponse(new WiredFurniRuntimeStateComposer(
                itemId,
                WiredFurniRuntimeStatePolicy.normalizeAllowedKey(key),
                result.value(),
                result.supported(),
                result.success()));
    }

    @Override
    public int getRatelimit() {
        return 100;
    }
}
