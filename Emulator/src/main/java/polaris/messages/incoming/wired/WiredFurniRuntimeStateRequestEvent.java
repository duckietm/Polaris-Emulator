package polaris.messages.incoming.wired;

import polaris.hotel.rooms.BuildersClubItemIds;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.UserItem;
import polaris.hotel.wired.WiredVariableChangeOrigin;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.wired.WiredFurniRuntimeStateComposer;

public class WiredFurniRuntimeStateRequestEvent extends MessageHandler {
    private static final int MINIMUM_PACKET_BYTES = 14;

    @Override
    public void handle() throws Exception {
        Room room = currentRoom();
        if (room == null || !room.canInspectWired(this.client.getUser())) {
            return;
        }
        if (this.packet.bytesAvailable() < MINIMUM_PACKET_BYTES) {
            return;
        }

        int itemId = BuildersClubItemIds.fromWire(this.currentRoom(), this.packet.readInt());
        int action = this.packet.readInt();
        String key = this.packet.readString();
        if (key.length() > WiredFurniRuntimeStatePolicy.MAX_KEY_LENGTH || this.packet.bytesAvailable() < 4) {
            return;
        }
        int requestedValue = this.packet.readInt();

        UserItem item = itemId > 0 ? room.getUserItem(itemId) : null;
        WiredFurniRuntimeStatePolicy.Result result;
        if (action == WiredFurniRuntimeStatePolicy.ACTION_READ) {
            result = WiredFurniRuntimeStatePolicy.read(room, item, key);
        } else if (action == WiredFurniRuntimeStatePolicy.ACTION_WRITE && room.canModifyWired(this.client.getUser())) {
            int previousOrigin = WiredVariableChangeOrigin.enter(WiredVariableChangeOrigin.CREATOR_TOOLS);
            try {
                result = WiredFurniRuntimeStatePolicy.write(room, item, key, requestedValue);
            } finally {
                WiredVariableChangeOrigin.exit(previousOrigin);
            }
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
