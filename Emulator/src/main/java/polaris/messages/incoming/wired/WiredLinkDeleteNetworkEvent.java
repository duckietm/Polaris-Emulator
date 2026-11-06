package polaris.messages.incoming.wired;

import polaris.hotel.items.interactions.wired.InteractionWiredLink;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.User;
import polaris.hotel.wired.link.WiredLinkService;
import polaris.messages.outgoing.wired.WiredLinkNetworkInvalidatedComposer;
import polaris.messages.outgoing.wired.WiredLinkOperationResultComposer;

/**
 * WIRED Link delete network (AIR 2731): {@code int furniId, String networkId}. Its Links in every room are
 * disconnected. A Link window the user has open on another Link is told with the invalidated push (999).
 */
public class WiredLinkDeleteNetworkEvent extends WiredLinkMessageHandler {
    @Override
    protected int operation() {
        return WiredLinkService.OP_DELETE;
    }

    @Override
    protected void handleLink(Room room, User user, InteractionWiredLink link) {
        String networkId = this.packet.readString();
        int ownerId = user.getUserInfo().getId();
        WiredLinkService service = WiredLinkService.get();
        WiredLinkService.Result result = service.delete(ownerId, room.getId(), link.getId(), networkId);
        this.client.sendResponse(new WiredLinkOperationResultComposer(result));
        WiredLinkService.OpenTarget open = service.openTarget(ownerId);
        if (result.succeeded() && open != null && open.furniId() != link.getId()) {
            this.client.sendResponse(
                    new WiredLinkNetworkInvalidatedComposer(open.roomId(), open.furniId(), result.removedNetworkId()));
        }
    }
}
