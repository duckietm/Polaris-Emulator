package com.eu.habbo.messages.incoming.catalog.specialitems;

import com.eu.habbo.habbohotel.catalog.specialitems.SpecialItemClaimService;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.catalog.specialitems.HasClaimedProductComposer;

/**
 * AIR 15 has-claimed request (395, {@code claimId}), sent by {@code SpecialItemsController.initialize}
 * when a showcase with a free claim opens. Answered with {@link HasClaimedProductComposer}; a claim id
 * the hotel does not offer answers as claimed, so the window never enables a claim that would fail.
 */
public class RequestHasClaimedProductEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 250;
    }

    @Override
    public void handle() throws Exception {
        Habbo habbo = this.client.getHabbo();
        if (habbo == null) return;

        String claimId = this.packet.readString();
        if (!SpecialItemClaimService.isValidClaimId(claimId)) return;

        SpecialItemClaimService service = SpecialItemClaimService.installed();
        boolean claimed = service == null
                || service.claimedOrUnavailable(habbo.getHabboInfo().getId(), claimId);

        this.client.sendResponse(new HasClaimedProductComposer(claimId, claimed));
    }
}
