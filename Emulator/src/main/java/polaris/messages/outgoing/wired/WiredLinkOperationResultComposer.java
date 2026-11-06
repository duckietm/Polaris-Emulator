package polaris.messages.outgoing.wired;

import polaris.hotel.rooms.BuildersClubItemIds;
import polaris.hotel.wired.link.WiredLinkService;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

/**
 * WIRED Link operation result (AIR 3961): {@code int roomId, int furniId, int operation, int resultCode,
 * bool hasConfiguration, [String selectedNetworkId, bool receiveLocal], String removedNetworkId, int
 * count, count x network}. Operations 1 save, 2 create, 3 rename, 4 delete, 5 connections; result 0 is
 * success, 1..8 the official {@code wiredlink.error.*}.
 */
public class WiredLinkOperationResultComposer extends MessageComposer {
    private final WiredLinkService.Result result;

    public WiredLinkOperationResultComposer(WiredLinkService.Result result) {
        this.result = result;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.WiredLinkOperationResultComposer);
        this.response.appendInt(this.result.roomId());
        this.response.appendInt(BuildersClubItemIds.toWire(this.result.furniId()));
        this.response.appendInt(this.result.operation());
        this.response.appendInt(this.result.resultCode());
        this.response.appendBoolean(this.result.hasConfiguration());
        if (this.result.hasConfiguration()) {
            this.response.appendString(this.result.selectedNetworkId());
            this.response.appendBoolean(this.result.receiveLocal());
        }
        this.response.appendString(this.result.removedNetworkId());
        this.response.appendInt(this.result.networks().size());
        for (WiredLinkService.NetworkView network : this.result.networks()) {
            appendNetwork(this.response, network);
        }
        return this.response;
    }

    static void appendNetwork(ServerMessage message, WiredLinkService.NetworkView network) {
        message.appendString(network.id());
        message.appendString(network.name());
        message.appendInt(network.connectionCount());
        message.appendBoolean(network.linkedInCurrentRoom());
    }
}
