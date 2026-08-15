package com.eu.habbo.messages.incoming.catalog.recycler;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.achievements.AchievementManager;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.ItemManager;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.users.inventory.ItemsComponent;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.catalog.AlertPurchaseFailedComposer;
import com.eu.habbo.messages.outgoing.catalog.CatalogRuntimeConfigurationComposer;
import com.eu.habbo.messages.outgoing.catalog.RecyclerCompleteComposer;
import com.eu.habbo.messages.outgoing.generic.alerts.HotelWillCloseInMinutesComposer;
import com.eu.habbo.messages.outgoing.inventory.AddHabboItemComposer;
import com.eu.habbo.messages.outgoing.inventory.InventoryRefreshComposer;
import com.eu.habbo.messages.outgoing.inventory.RemoveHabboItemComposer;
import com.eu.habbo.threading.runnables.QueryDeleteHabboItem;
import com.eu.habbo.threading.runnables.ShutdownEmulator;
import java.util.HashSet;
import java.util.Set;

public class RecycleEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        if (ShutdownEmulator.timestamp > 0) {
            this.client.sendResponse(new HotelWillCloseInMinutesComposer(
                    (ShutdownEmulator.timestamp - Emulator.getIntUnixTimestamp()) / 60));
            return;
        }

        if (Emulator.getGameEnvironment().getCatalogManager().ecotronItem != null && ItemManager.RECYCLER_ENABLED) {
            Set<HabboItem> items = new HashSet<>();

            int count = this.packet.readInt();
            if (count != CatalogRuntimeConfigurationComposer.getRecyclerSlotCount()) return;

            ItemsComponent itemsComponent =
                    this.client.getHabbo().getInventory().getItemsComponent();

            for (int i = 0; i < count; i++) {
                HabboItem item = itemsComponent.getHabboItem(this.packet.readInt());

                if (item == null) return;

                if (item.getBaseItem().allowRecyle()) {
                    items.add(item);
                }
            }

            if (items.size() != count) {
                this.client.sendResponse(new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR));
                return;
            }

            if (!itemsComponent.takeHabboItemsAtomically(items)) {
                this.client.sendResponse(new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR));
                return;
            }

            HabboItem reward = null;
            boolean consumed = false;

            try {
                Item prize = Emulator.getGameEnvironment().getCatalogManager().getRandomRecyclerPrize();

                if (prize == null) {
                    this.client.sendResponse(new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR));
                    return;
                }

                reward = Emulator.getGameEnvironment()
                        .getItemManager()
                        .handleRecycle(this.client.getHabbo(), prize.getId() + "");
                if (reward == null) {
                    this.client.sendResponse(new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR));
                    return;
                }

                itemsComponent.addItem(reward);
                if (itemsComponent.getHabboItem(reward.getId()) != reward) {
                    this.client.sendResponse(new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR));
                    return;
                }

                for (HabboItem item : items) {
                    Emulator.getThreading().runPersistence(new QueryDeleteHabboItem(item.getId()));
                }
                consumed = true;

                for (HabboItem item : items) {
                    this.client.sendResponse(new RemoveHabboItemComposer(item.getGiftAdjustedId()));
                }
                this.client.sendResponse(new AddHabboItemComposer(reward));
                this.client.sendResponse(new RecyclerCompleteComposer(RecyclerCompleteComposer.RECYCLING_COMPLETE));
                this.client.sendResponse(new InventoryRefreshComposer());

                AchievementManager.progressAchievement(
                        this.client.getHabbo(),
                        Emulator.getGameEnvironment().getAchievementManager().getAchievement("FurnimaticQuest"));
            } finally {
                if (!consumed) {
                    itemsComponent.restoreHabboItemsAfterFailedTake(items);

                    if (reward != null) {
                        itemsComponent.removeHabboItem(reward);
                        Emulator.getThreading().runPersistence(new QueryDeleteHabboItem(reward.getId()));
                    }
                }
            }
        } else {
            this.client.sendResponse(new RecyclerCompleteComposer(RecyclerCompleteComposer.RECYCLING_CLOSED));
        }
    }
}
