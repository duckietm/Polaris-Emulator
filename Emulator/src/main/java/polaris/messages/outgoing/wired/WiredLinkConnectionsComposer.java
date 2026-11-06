package polaris.messages.outgoing.wired;

import polaris.hotel.rooms.BuildersClubItemIds;
import polaris.hotel.wired.link.WiredLinkService;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

/**
 * WIRED Link connections (AIR 3962): {@code int roomId, int furniId, String networkId, int count, count
 * x (int roomId, String roomName, int furniId, bool buildersClub, bool receiveLocal)}.
 */
public class WiredLinkConnectionsComposer extends MessageComposer {
    private final WiredLinkService.Connections connections;

    public WiredLinkConnectionsComposer(WiredLinkService.Connections connections) {
        this.connections = connections;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.WiredLinkConnectionsComposer);
        this.response.appendInt(this.connections.roomId());
        this.response.appendInt(BuildersClubItemIds.toWire(this.connections.furniId()));
        this.response.appendString(this.connections.networkId());
        this.response.appendInt(this.connections.connections().size());
        for (WiredLinkService.Connection connection : this.connections.connections()) {
            this.response.appendInt(connection.roomId());
            this.response.appendString(connection.roomName());
            this.response.appendInt(BuildersClubItemIds.toWire(connection.furniId()));
            this.response.appendBoolean(connection.buildersClub());
            this.response.appendBoolean(connection.receiveLocal());
        }
        return this.response;
    }
}
