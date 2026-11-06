package polaris.messages.incoming.wired;

import polaris.hotel.items.interactions.wired.InteractionWiredLink;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.User;
import polaris.hotel.wired.link.WiredLinkService;
import polaris.messages.outgoing.wired.WiredLinkOperationResultComposer;

/**
 * WIRED Link save selection (AIR 385): {@code int furniId, String networkId, bool receiveLocal}. Connects the
 * Link to one of the user's networks ("0" for none) and sets whether it receives its own room's messages.
 */
public class WiredLinkSaveSelectionEvent extends WiredLinkMessageHandler {
    @Override
    protected int operation() {
        return WiredLinkService.OP_SAVE;
    }

    @Override
    protected void handleLink(Room room, User user, InteractionWiredLink link) {
        String networkId = this.packet.readString();
        boolean receiveLocal = this.packet.readBoolean();
        this.client.sendResponse(new WiredLinkOperationResultComposer(WiredLinkService.get()
                .save(user.getUserInfo().getId(), room.getId(), link.getId(), networkId, receiveLocal)));
    }
}
