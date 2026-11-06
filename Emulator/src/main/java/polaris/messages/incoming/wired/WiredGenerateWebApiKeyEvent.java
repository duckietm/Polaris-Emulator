package polaris.messages.incoming.wired;

import polaris.hotel.items.interactions.InteractionWiredExtra;
import polaris.hotel.items.interactions.wired.extra.WiredExtraVariableWebApi;
import polaris.hotel.rooms.BuildersClubItemIds;
import polaris.hotel.rooms.Room;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.generic.alerts.UpdateFailedComposer;
import polaris.messages.outgoing.wired.WiredWebApiKeyResultComposer;

/**
 * The owner of a Variables Web API box asking for a new read or write key (2819). The server mints
 * it, stores it on the box and answers with {@link WiredWebApiKeyResultComposer} to that owner only.
 */
public class WiredGenerateWebApiKeyEvent extends MessageHandler {
    static final String INVALID_KEYS = "wiredfurni.error.invalid_api_keys";

    @Override
    public void handle() throws Exception {
        int itemId = BuildersClubItemIds.fromWire(this.currentRoom(), this.packet.readInt());
        boolean isReadKey = this.packet.readBoolean();
        Room room = currentRoom();

        if (room == null || room.getRoomSpecialTypes() == null || !room.canInspectWired(this.client.getUser())) {
            return;
        }

        InteractionWiredExtra extra = room.getRoomSpecialTypes().getExtra(itemId);
        if (!(extra instanceof WiredExtraVariableWebApi box)) {
            return;
        }

        if (!box.isUsableIn(room)
                || box.getUserId() != this.client.getUser().getUserInfo().getId()) {
            this.client.sendResponse(new UpdateFailedComposer(INVALID_KEYS));
            return;
        }

        String key = box.generateKey(this.client.getUser(), room, isReadKey, System.currentTimeMillis());
        if (key != null) {
            this.client.sendResponse(new WiredWebApiKeyResultComposer(itemId, isReadKey, key));
        }
    }

    @Override
    public int getRatelimit() {
        return 500;
    }
}
