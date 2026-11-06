package polaris.messages.incoming.catalog.specialitems;

import polaris.hotel.catalog.specialitems.SpecialItemClaimService;
import polaris.hotel.users.User;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.catalog.specialitems.HasClaimedProductComposer;

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
        User user = this.client.getUser();
        if (user == null) return;

        String claimId = this.packet.readString();
        if (!SpecialItemClaimService.isValidClaimId(claimId)) return;

        SpecialItemClaimService service = SpecialItemClaimService.installed();
        boolean claimed = service == null
                || service.claimedOrUnavailable(user.getUserInfo().getId(), claimId);

        this.client.sendResponse(new HasClaimedProductComposer(claimId, claimed));
    }
}
