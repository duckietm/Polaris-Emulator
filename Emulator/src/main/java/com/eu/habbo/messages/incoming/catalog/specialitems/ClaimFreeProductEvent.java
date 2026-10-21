package com.eu.habbo.messages.incoming.catalog.specialitems;

import com.eu.habbo.habbohotel.catalog.specialitems.SpecialItemClaimService;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.catalog.specialitems.HasClaimedProductComposer;
import com.eu.habbo.messages.outgoing.inventory.AddHabboItemComposer;
import com.eu.habbo.messages.outgoing.inventory.InventoryRefreshComposer;

/**
 * AIR 15 claim (3935, {@code claimId}), sent by the special items display once the user has looked at
 * every item and pressed claim. Gives the furni the hotel configured for the claim id, once per user,
 * and confirms with {@link HasClaimedProductComposer} {@code (claimId, true)}.
 */
public class ClaimFreeProductEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 1000;
    }

    @Override
    public void handle() throws Exception {
        Habbo habbo = this.client.getHabbo();
        if (habbo == null) return;

        String claimId = this.packet.readString();
        if (!SpecialItemClaimService.isValidClaimId(claimId)) return;

        SpecialItemClaimService service = SpecialItemClaimService.installed();
        if (service == null) {
            this.client.sendResponse(new HasClaimedProductComposer(claimId, true));
            return;
        }

        SpecialItemClaimService.ClaimResult result =
                service.claim(habbo.getHabboInfo().getId(), claimId, (int) (System.currentTimeMillis() / 1000L));

        if (result.item() != null) {
            habbo.getInventory().getItemsComponent().addItem(result.item());
            this.client.sendResponse(new AddHabboItemComposer(result.item()));
            this.client.sendResponse(new InventoryRefreshComposer());
        }

        this.client.sendResponse(new HasClaimedProductComposer(claimId, result.claimed()));
    }
}
