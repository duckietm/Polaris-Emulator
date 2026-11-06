package polaris.messages.outgoing.wired;

import polaris.hotel.rooms.BuildersClubItemIds;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

/**
 * WIRED Link network invalidated (AIR 999): {@code int roomId, int furniId, String deletedNetworkId}. Tells
 * an open Link window that one of its networks was deleted elsewhere.
 */
public class WiredLinkNetworkInvalidatedComposer extends MessageComposer {
    private final int roomId;
    private final int furniId;
    private final String networkId;

    public WiredLinkNetworkInvalidatedComposer(int roomId, int furniId, String networkId) {
        this.roomId = roomId;
        this.furniId = furniId;
        this.networkId = networkId;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.WiredLinkNetworkInvalidatedComposer);
        this.response.appendInt(this.roomId);
        this.response.appendInt(BuildersClubItemIds.toWire(this.furniId));
        this.response.appendString(this.networkId);
        return this.response;
    }
}
