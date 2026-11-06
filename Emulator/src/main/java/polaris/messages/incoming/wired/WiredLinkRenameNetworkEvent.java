package polaris.messages.incoming.wired;

import polaris.hotel.items.interactions.wired.InteractionWiredLink;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.User;
import polaris.hotel.wired.link.WiredLinkService;
import polaris.messages.outgoing.wired.WiredLinkOperationResultComposer;

/** WIRED Link rename network (AIR 2794): {@code int furniId, String networkId, String name}. */
public class WiredLinkRenameNetworkEvent extends WiredLinkMessageHandler {
    @Override
    protected int operation() {
        return WiredLinkService.OP_RENAME;
    }

    @Override
    protected void handleLink(Room room, User user, InteractionWiredLink link) {
        String networkId = this.packet.readString();
        String name = this.packet.readString();
        this.client.sendResponse(new WiredLinkOperationResultComposer(WiredLinkService.get()
                .rename(user.getUserInfo().getId(), room.getId(), link.getId(), networkId, name)));
    }
}
