package polaris.messages.outgoing.wired;

import polaris.hotel.items.interactions.wired.contract.WiredNativeContract;
import polaris.hotel.rooms.BuildersClubItemIds;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

/**
 * What the official contract editor shows (AIR {@code WiredContractController.onContractContents}); the layout is
 * {@link WiredNativeContract#write}.
 */
public class WiredContractContentsComposer extends MessageComposer {
    private final WiredNativeContract contract;

    /** @param contract the contract under its database id */
    public WiredContractContentsComposer(WiredNativeContract contract) {
        this.contract = contract;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.WiredContractContentsComposer);
        this.contract
                .withContractId(BuildersClubItemIds.toWire(this.contract.contractId()))
                .write(this.response);
        return this.response;
    }
}
