package com.eu.habbo.messages.incoming.collectibles;

import com.eu.habbo.habbohotel.collectibles.CollectibleData.Owned;
import com.eu.habbo.habbohotel.collectibles.CollectiblesService;
import com.eu.habbo.habbohotel.collectibles.CollectiblesSettings;
import com.eu.habbo.habbohotel.rooms.BuildersClubItemIds;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.collectibles.CollectibleMintResultComposer;
import com.eu.habbo.messages.outgoing.collectibles.CollectibleMintTokensComposer;
import com.eu.habbo.messages.outgoing.inventory.InventoryRefreshComposer;
import com.eu.habbo.messages.outgoing.inventory.RemoveHabboItemComposer;
import java.util.List;
import java.util.Optional;

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
        Habbo habbo = this.client.getHabbo();
        if (habbo == null) return;

        int itemId = BuildersClubItemIds.fromWire(this.currentRoom(), this.packet.readInt());
        String wallet = this.packet.readString();

        CollectiblesSettings settings = CollectiblesSupport.settings();
        CollectiblesService service = CollectiblesSupport.service(settings);
        if (service == null) return;

        int userId = habbo.getHabboInfo().getId();
        HabboItem item = habbo.getInventory().getItemsComponent().getHabboItem(itemId);
        if (!settings.mintingEnabled()
                || item == null
                || item.getRoomId() != 0
                || item.getUserId() != userId
                || !CollectiblesSupport.ownsWallet(habbo, wallet)
                || !habbo.getInventory().getItemsComponent().takeHabboItemsAtomically(List.of(item))) {
            this.client.sendResponse(new CollectibleMintResultComposer(FAILED));
            return;
        }

        Optional<Owned> owned;
        try {
            owned = service.mintRelic(userId, item.getBaseItem().getId(), CollectiblesSupport.now());
        } catch (Exception exception) {
            habbo.getInventory().getItemsComponent().restoreHabboItemsAfterFailedTake(List.of(item));
            this.client.sendResponse(new CollectibleMintResultComposer(FAILED));
            throw exception;
        }
        if (owned.isEmpty()) {
            habbo.getInventory().getItemsComponent().restoreHabboItemsAfterFailedTake(List.of(item));
            this.client.sendResponse(new CollectibleMintResultComposer(FAILED));
            return;
        }

        CollectiblesSupport.deleteItem(item);
        this.client.sendResponse(new RemoveHabboItemComposer(item.getGiftAdjustedId()));
        this.client.sendResponse(new InventoryRefreshComposer());
        CollectiblesSupport.publishAcquired(habbo, List.of(owned.get()), service, settings);
        this.client.sendResponse(new CollectibleMintResultComposer(MINTED));
        this.client.sendResponse(new CollectibleMintTokensComposer(service.mintTokens(userId)));
    }
}
