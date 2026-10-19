package com.eu.habbo.messages.outgoing.wired;

import com.eu.habbo.habbohotel.rooms.BuildersClubItemIds;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

/**
 * The answer to a contract editor save (AIR {@code WiredContractController.onContractUpdateResult}): the contract
 * furni, whether it saved, and a fail code the client shows as {@code wiredcontracts.error.<code>}.
 */
public class WiredContractUpdateResultComposer extends MessageComposer {
    /** The only fail code the official texts carry. */
    public static final String INVALID_RULES = "invalid_rules";

    private final int furniId;
    private final boolean success;

    public WiredContractUpdateResultComposer(int furniId, boolean success) {
        this.furniId = furniId;
        this.success = success;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.WiredContractUpdateResultComposer);
        this.response.appendInt(BuildersClubItemIds.toWire(this.furniId));
        this.response.appendBoolean(this.success);
        this.response.appendString(this.success ? "" : INVALID_RULES);
        return this.response;
    }
}
