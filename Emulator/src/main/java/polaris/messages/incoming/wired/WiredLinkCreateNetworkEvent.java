package polaris.messages.incoming.wired;

import polaris.hotel.items.interactions.wired.InteractionWiredLink;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.User;
import polaris.hotel.wired.link.WiredLinkService;
import polaris.messages.outgoing.wired.WiredLinkOperationResultComposer;

/** WIRED Link create network (AIR 2192): {@code int furniId, String name}. */
public class WiredLinkCreateNetworkEvent extends WiredLinkMessageHandler {
    @Override
    protected int operation() {
        return WiredLinkService.OP_CREATE;
    }

    @Override
    protected void handleLink(Room room, User user, InteractionWiredLink link) {
        String name = this.packet.readString();
        this.client.sendResponse(new WiredLinkOperationResultComposer(
                WiredLinkService.get().create(user.getUserInfo().getId(), room.getId(), link.getId(), name)));
    }
}
