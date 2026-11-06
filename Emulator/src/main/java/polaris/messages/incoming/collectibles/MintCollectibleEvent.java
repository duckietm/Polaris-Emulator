package polaris.messages.incoming.collectibles;

import java.util.List;
import java.util.Optional;
import polaris.hotel.collectibles.CollectibleData.Owned;
import polaris.hotel.collectibles.CollectiblesService;
import polaris.hotel.collectibles.CollectiblesSettings;
import polaris.hotel.rooms.BuildersClubItemIds;
import polaris.hotel.users.User;
import polaris.hotel.users.UserItem;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.collectibles.CollectibleMintResultComposer;
import polaris.messages.outgoing.collectibles.CollectibleMintTokensComposer;
import polaris.messages.outgoing.inventory.InventoryRefreshComposer;
import polaris.messages.outgoing.inventory.RemoveUserItemComposer;

/**
 * AIR 15 mint (3779, {@code itemId, wallet}): turns a furni from the user's inventory into its relic
 * ({@code collectible_mintables}) for the relic's price in stamps. The furni goes, the relic is minted.
 * Answers the mint result ({@code 9421}, 1 ok, 0 failed) and the stamp balance.
 */
public class MintCollectibleEvent extends MessageHandler {
    public static final int FAILED = 0;
    public static final int MINTED = 1;

    @Override
    public int getRatelimit() {
        return 1000;
    }

    @Override
    public void handle() throws Exception {
        User user = this.client.getUser();
        if (user == null) return;

        int itemId = BuildersClubItemIds.fromWire(this.currentRoom(), this.packet.readInt());
        String wallet = this.packet.readString();

        CollectiblesSettings settings = CollectiblesSupport.settings();
        CollectiblesService service = CollectiblesSupport.service(settings);
        if (service == null) return;

        int userId = user.getUserInfo().getId();
        UserItem item = user.getInventory().getItemsComponent().getUserItem(itemId);
        if (!settings.mintingEnabled()
                || item == null
                || item.getRoomId() != 0
                || item.getUserId() != userId
                || !CollectiblesSupport.ownsWallet(user, wallet)
                || !user.getInventory().getItemsComponent().takeUserItemsAtomically(List.of(item))) {
            this.client.sendResponse(new CollectibleMintResultComposer(FAILED));
            return;
        }

        Optional<Owned> owned;
        try {
            owned = service.mintRelic(userId, item.getBaseItem().getId(), CollectiblesSupport.now());
        } catch (Exception exception) {
            user.getInventory().getItemsComponent().restoreUserItemsAfterFailedTake(List.of(item));
            this.client.sendResponse(new CollectibleMintResultComposer(FAILED));
            throw exception;
        }
        if (owned.isEmpty()) {
            user.getInventory().getItemsComponent().restoreUserItemsAfterFailedTake(List.of(item));
            this.client.sendResponse(new CollectibleMintResultComposer(FAILED));
            return;
        }

        CollectiblesSupport.deleteItem(item);
        this.client.sendResponse(new RemoveUserItemComposer(item.getGiftAdjustedId()));
        this.client.sendResponse(new InventoryRefreshComposer());
        CollectiblesSupport.publishAcquired(user, List.of(owned.get()), service, settings);
        this.client.sendResponse(new CollectibleMintResultComposer(MINTED));
        this.client.sendResponse(new CollectibleMintTokensComposer(service.mintTokens(userId)));
    }
}
