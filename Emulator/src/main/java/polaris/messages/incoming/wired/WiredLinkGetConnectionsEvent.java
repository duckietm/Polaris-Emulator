package polaris.messages.incoming.wired;

import polaris.hotel.items.interactions.wired.InteractionWiredLink;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.User;
import polaris.hotel.wired.link.WiredLinkService;
import polaris.messages.outgoing.wired.WiredLinkConnectionsComposer;
import polaris.messages.outgoing.wired.WiredLinkOperationResultComposer;

/**
 * WIRED Link connections request (AIR 2300, ours {@code Incoming.WiredLinkGetConnectionsEvent} since 2300 is
 * taken): {@code int furniId, String networkId}; answered with the connections (3962), or a failed result for
 * operation 5.
 */
public class WiredLinkGetConnectionsEvent extends WiredLinkMessageHandler {
    @Override
    protected int operation() {
        return WiredLinkService.OP_CONNECTIONS;
    }

    @Override
    protected void handleLink(Room room, User user, InteractionWiredLink link) {
        String networkId = this.packet.readString();
        Object answer =
                WiredLinkService.get().connections(user.getUserInfo().getId(), room.getId(), link.getId(), networkId);
        if (answer instanceof WiredLinkService.Connections connections) {
            this.client.sendResponse(new WiredLinkConnectionsComposer(connections));
        } else if (answer instanceof WiredLinkService.Result result) {
            this.client.sendResponse(new WiredLinkOperationResultComposer(result));
        }
    }
}
