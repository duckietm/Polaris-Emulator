package polaris.messages.incoming.catalog.specialitems;

import polaris.hotel.catalog.specialitems.SpecialItemClaimService;
import polaris.hotel.users.User;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.catalog.specialitems.HasClaimedProductComposer;
import polaris.messages.outgoing.inventory.AddUserItemComposer;
import polaris.messages.outgoing.inventory.InventoryRefreshComposer;

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
        User user = this.client.getUser();
        if (user == null) return;

        String claimId = this.packet.readString();
        if (!SpecialItemClaimService.isValidClaimId(claimId)) return;

        SpecialItemClaimService service = SpecialItemClaimService.installed();
        if (service == null) {
            this.client.sendResponse(new HasClaimedProductComposer(claimId, true));
            return;
        }

        SpecialItemClaimService.ClaimResult result =
                service.claim(user.getUserInfo().getId(), claimId, (int) (System.currentTimeMillis() / 1000L));

        if (result.item() != null) {
            user.getInventory().getItemsComponent().addItem(result.item());
            this.client.sendResponse(new AddUserItemComposer(result.item()));
            this.client.sendResponse(new InventoryRefreshComposer());
        }

        this.client.sendResponse(new HasClaimedProductComposer(claimId, result.claimed()));
    }
}
