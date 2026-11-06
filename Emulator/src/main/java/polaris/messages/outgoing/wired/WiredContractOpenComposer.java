package polaris.messages.outgoing.wired;

import polaris.hotel.rooms.BuildersClubItemIds;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

/**
 * Opens the official contract editor (AIR {@code WiredContractController.onOpenContract}): the contract furni. The
 * client answers with a contents request for it.
 */
public class WiredContractOpenComposer extends MessageComposer {
    private final int furniId;

    public WiredContractOpenComposer(int furniId) {
        this.furniId = furniId;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.WiredContractOpenComposer);
        this.response.appendInt(BuildersClubItemIds.toWire(this.furniId));
        return this.response;
    }
}
