package polaris.messages.incoming.inventory;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;
import polaris.hotel.habbicons.HabbiconService;
import polaris.hotel.users.inventory.UnseenItemsComponent;
import polaris.messages.incoming.MessageHandler;

/**
 * Official {@code UnseenResetItemsMessageComposer} (2343): the listed items of one category have
 * been looked at ({@code UnseenItemTracker.resetItems}). Payload is
 * {@code (category, count, itemId...)}. Category {@link HabbiconService#UNSEEN_CATEGORY} belongs to the
 * habbicon collection and is cleared through {@link HabbiconService#clearUnseen} instead.
 */
public class UnseenResetItemsEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int category = this.packet.readInt();
        int count = this.packet.readInt();

        if (category <= 0 || count <= 0 || count > UnseenItemsComponent.MAX_RESET_ITEMS) {
            return;
        }

        Set<Integer> itemIds = new LinkedHashSet<>();

        for (int i = 0; i < count; i++) {
            if (this.packet.bytesAvailable() < 4) {
                return;
            }

            itemIds.add(this.packet.readInt());
        }

        if (category == HabbiconService.UNSEEN_CATEGORY) {
            HabbiconService habbicons = this.client.getUser().getHabbiconService();

            if (habbicons != null) {
                habbicons.clearUnseen(this.client.getUser().getUserInfo().getId(), new ArrayList<>(itemIds));
            }

            return;
        }

        this.client.getUser().getInventory().getUnseenItemsComponent().resetItems(category, itemIds);
    }
}
