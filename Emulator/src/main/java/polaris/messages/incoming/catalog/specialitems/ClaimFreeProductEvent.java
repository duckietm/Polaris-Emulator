package polaris.messages.incoming.catalog.specialitems;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.WiredPlatform;
import polaris.hotel.catalog.specialitems.SpecialItemClaimService;
import polaris.hotel.pets.Pet;
import polaris.hotel.users.User;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.catalog.PetBoughtNotificationComposer;
import polaris.messages.outgoing.catalog.specialitems.ClaimProductResultComposer;
import polaris.messages.outgoing.catalog.specialitems.HasClaimedProductComposer;
import polaris.messages.outgoing.inventory.AddPetComposer;
import polaris.messages.outgoing.inventory.AddUserItemComposer;
import polaris.messages.outgoing.inventory.InventoryRefreshComposer;

/**
 * The official free claim (3935): {@code claimId}, then the choice when the window offers one (the cursed dragon
 * adoption sends 0 for blue, 1 for purple; the special items display sends none). Gives the furni the hotel
 * configured for the claim id and choice, once per user, and answers with {@link ClaimProductResultComposer} and, as
 * before, {@link HasClaimedProductComposer}.
 */
public class ClaimFreeProductEvent extends MessageHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClaimFreeProductEvent.class);

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

        int choice = this.packet.bytesAvailable() >= 4 ? this.packet.readInt() : SpecialItemClaimService.NO_CHOICE;

        SpecialItemClaimService service = SpecialItemClaimService.installed();
        if (service == null) {
            this.client.sendResponse(
                    new ClaimProductResultComposer(claimId, SpecialItemClaimService.Outcome.UNAVAILABLE.code()));
            this.client.sendResponse(new HasClaimedProductComposer(claimId, true));
            return;
        }

        SpecialItemClaimService.ChoiceResult result =
                service.claim(user.getUserInfo().getId(), claimId, choice, (int) (System.currentTimeMillis() / 1000L));

        if (result.item() != null) {
            user.getInventory().getItemsComponent().addItem(result.item());
            this.client.sendResponse(new AddUserItemComposer(result.item()));
            this.client.sendResponse(new InventoryRefreshComposer());
        }

        // The pet of the choice (the adopted dragon) goes to the pets inventory, announced like a bought pet.
        if (result.pet() != null) {
            Pet pet = WiredPlatform.gameEnvironment()
                    .getPetManager()
                    .createPet(
                            result.pet().type(),
                            result.pet().breed(),
                            result.pet().name(),
                            this.client);

            if (pet == null) {
                LOGGER.warn(
                        "Free claim {} names pet type {}, which this hotel does not have (pet_actions); no pet given",
                        claimId,
                        result.pet().type());
            } else {
                user.getInventory().getPetsComponent().addPet(pet);
                this.client.sendResponse(new AddPetComposer(pet));
                this.client.sendResponse(new PetBoughtNotificationComposer(pet, false));
            }
        }

        // The badge of the choice ("I adopted a Blue Cursed Dragon!"); a user who already has it keeps theirs.
        if (!result.badge().isEmpty()) {
            user.addBadge(result.badge());
        }

        this.client.sendResponse(
                new ClaimProductResultComposer(claimId, result.outcome().code()));
        this.client.sendResponse(
                new HasClaimedProductComposer(claimId, result.outcome().countsAsClaimed()));
    }
}
