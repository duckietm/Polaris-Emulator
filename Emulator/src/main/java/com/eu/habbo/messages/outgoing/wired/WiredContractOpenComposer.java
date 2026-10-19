package com.eu.habbo.messages.outgoing.wired;

import com.eu.habbo.habbohotel.rooms.BuildersClubItemIds;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

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
