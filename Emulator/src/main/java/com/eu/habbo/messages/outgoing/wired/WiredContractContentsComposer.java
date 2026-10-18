package com.eu.habbo.messages.outgoing.wired;

import com.eu.habbo.habbohotel.items.interactions.wired.contract.WiredNativeContract;
import com.eu.habbo.habbohotel.rooms.BuildersClubItemIds;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

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
