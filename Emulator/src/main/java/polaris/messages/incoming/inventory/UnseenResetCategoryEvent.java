package polaris.messages.incoming.inventory;

import java.util.List;
import polaris.hotel.habbicons.HabbiconService;
import polaris.messages.incoming.MessageHandler;

/**
 * Official {@code UnseenResetCategoryMessageComposer} (3493): the user opened an inventory tab,
 * so everything the tracker still counted as new in that category has now been seen
 * ({@code FurniModel/BadgesModel/BotsModel/PetsModel.resetUnseenItems}). Category
 * {@link HabbiconService#UNSEEN_CATEGORY} belongs to the habbicon collection and is cleared through
 * {@link HabbiconService#clearUnseen} instead.
 */
public class UnseenResetCategoryEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int category = this.packet.readInt();

        if (category <= 0) {
            return;
        }

        if (category == HabbiconService.UNSEEN_CATEGORY) {
            HabbiconService habbicons = this.client.getUser().getHabbiconService();

            if (habbicons != null) {
                habbicons.clearUnseen(this.client.getUser().getUserInfo().getId(), List.of());
            }

            return;
        }

        this.client.getUser().getInventory().getUnseenItemsComponent().resetCategory(category);
    }
}
