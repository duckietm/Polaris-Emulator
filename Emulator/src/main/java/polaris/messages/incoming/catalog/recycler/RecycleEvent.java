package polaris.messages.incoming.catalog.recycler;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import polaris.Emulator;
import polaris.hotel.achievements.AchievementManager;
import polaris.hotel.catalog.RecyclerRules;
import polaris.hotel.items.Item;
import polaris.hotel.items.ItemManager;
import polaris.hotel.rooms.BuildersClubItemIds;
import polaris.hotel.users.UserItem;
import polaris.hotel.users.inventory.ItemsComponent;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.catalog.AlertPurchaseFailedComposer;
import polaris.messages.outgoing.catalog.CatalogRuntimeConfigurationComposer;
import polaris.messages.outgoing.catalog.RecyclerCompleteComposer;
import polaris.messages.outgoing.generic.alerts.HotelWillCloseInMinutesComposer;
import polaris.messages.outgoing.inventory.AddUserItemComposer;
import polaris.messages.outgoing.inventory.InventoryRefreshComposer;
import polaris.messages.outgoing.inventory.RemoveUserItemsComposer;
import polaris.threading.runnables.ShutdownEmulator;

public class RecycleEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        if (ShutdownEmulator.timestamp > 0) {
            this.client.sendResponse(new HotelWillCloseInMinutesComposer(
                    (ShutdownEmulator.timestamp - Emulator.getIntUnixTimestamp()) / 60));
            return;
        }

        if (Emulator.getGameEnvironment().getCatalogManager().ecotronItem != null && ItemManager.RECYCLER_ENABLED) {
            // The official client waits between two exchanges and may ask Duckets for one; both are checked before
            // anything is taken.
            int userId = this.client.getUser().getUserInfo().getId();
            int cost = RecyclerRules.ducketCost();
            if (RecyclerRules.secondsToWait(userId) > 0
                    || (cost > 0
                            && this.client.getUser().getUserInfo().getCurrencyAmount(RecyclerRules.DUCKETS) < cost)) {
                this.fail();
                return;
            }

            // In the order the client sent them: the furni leave its inventory in that order.
            Set<UserItem> items = new LinkedHashSet<>();

            int count = this.packet.readInt();
            if (count != CatalogRuntimeConfigurationComposer.getRecyclerSlotCount()) {
                this.fail();
                return;
            }

            ItemsComponent itemsComponent = this.client.getUser().getInventory().getItemsComponent();

            for (int i = 0; i < count; i++) {
                UserItem item = itemsComponent.getUserItem(
                        BuildersClubItemIds.fromWire(this.currentRoom(), this.packet.readInt()));

                if (item == null) {
                    this.fail();
                    return;
                }

                // A rented item is on loan: recycling it turns a few credits of rent
                // into a real prize. The count check below then fails the request.
                if (item.getBaseItem().allowRecyle() && !item.hasRentPeriod()) {
                    items.add(item);
                }
            }

            if (items.size() != count) {
                this.fail();
                return;
            }

            if (!itemsComponent.takeUserItemsAtomically(items)) {
                this.fail();
                return;
            }

            UserItem reward;
            boolean consumed = false;

            try {
                Item prize = Emulator.getGameEnvironment().getCatalogManager().getRandomRecyclerPrize();

                if (prize == null) {
                    this.fail();
                    return;
                }

                // The furni are deleted and the box inserted in one transaction: either both happened
                // or neither did, so the taken furni go back to the inventory only on a null.
                reward = Emulator.getGameEnvironment()
                        .getItemManager()
                        .handleRecycle(this.client.getUser(), prize.getId() + "", items);
                if (reward == null) {
                    this.fail();
                    return;
                }
                consumed = true;

                if (cost > 0) this.client.getUser().givePoints(RecyclerRules.DUCKETS, -cost, "recycler.exchange");
                RecyclerRules.exchanged(userId);

                itemsComponent.addItem(reward);

                List<Integer> removedItemIds = new ArrayList<>();
                for (UserItem item : items) {
                    removedItemIds.add(item.getGiftAdjustedId());
                }
                this.client.sendResponse(new RemoveUserItemsComposer(removedItemIds));
                this.client.sendResponse(new AddUserItemComposer(reward));
                this.client.sendResponse(
                        new RecyclerCompleteComposer(RecyclerCompleteComposer.RECYCLING_COMPLETE, reward.getId()));
                this.client.sendResponse(new InventoryRefreshComposer());

                AchievementManager.progressAchievement(
                        this.client.getUser(),
                        Emulator.getGameEnvironment().getAchievementManager().getAchievement("FurnimaticQuest"));
            } finally {
                if (!consumed) {
                    itemsComponent.restoreUserItemsAfterFailedTake(items);
                }
            }
        } else {
            this.client.sendResponse(new RecyclerCompleteComposer(RecyclerCompleteComposer.RECYCLING_CLOSED));
        }
    }

    /** The purchase alert, and AIR's recycler finished FAILED so the client stops waiting and keeps its slots. */
    private void fail() {
        this.client.sendResponse(new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR));
        this.client.sendResponse(new RecyclerCompleteComposer(RecyclerCompleteComposer.RECYCLING_FAILED));
    }
}
