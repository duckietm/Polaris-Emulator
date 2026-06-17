package com.eu.habbo.messages.incoming.crafting;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.achievements.AchievementManager;
import com.eu.habbo.habbohotel.crafting.CraftingAltar;
import com.eu.habbo.habbohotel.crafting.CraftingRecipe;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.catalog.AlertLimitedSoldOutComposer;
import com.eu.habbo.messages.outgoing.crafting.CraftingResultComposer;
import com.eu.habbo.messages.outgoing.inventory.AddHabboItemComposer;
import com.eu.habbo.messages.outgoing.inventory.InventoryRefreshComposer;
import com.eu.habbo.messages.outgoing.inventory.RemoveHabboItemComposer;
import com.eu.habbo.threading.runnables.QueryDeleteHabboItems;
import gnu.trove.map.hash.TIntObjectHashMap;

import java.util.Map;

public class CraftingCraftItemEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int craftingTable = this.packet.readInt();
        HabboItem item = this.client.getHabbo().getHabboInfo().getCurrentRoom().getHabboItem(craftingTable);
        CraftingAltar altar = Emulator.getGameEnvironment().getCraftingManager().getAltar(item.getBaseItem());
        CraftingRecipe recipe = altar.getRecipe(this.packet.readString());

        if (recipe != null) {
            if (!recipe.canBeCrafted()) {
                this.client.sendResponse(new AlertLimitedSoldOutComposer());
                return;
            }

            TIntObjectHashMap<HabboItem> toRemove = new TIntObjectHashMap<>();
            for (Map.Entry<Item, Integer> set : recipe.getIngredients().entrySet()) {
                for (int i = 0; i < set.getValue(); i++) {
                    HabboItem habboItem = this.client.getHabbo().getInventory().getItemsComponent().getAndRemoveHabboItem(set.getKey());

                    if (habboItem == null) {
                        // Not enough ingredients — give back whatever we already pulled.
                        this.restoreItems(toRemove);
                        return;
                    }

                    toRemove.put(habboItem.getId(), habboItem);
                }
            }

            HabboItem rewardItem = Emulator.getGameEnvironment().getItemManager().createItem(this.client.getHabbo().getHabboInfo().getId(), recipe.getReward(), 0, 0, "");

            if (rewardItem != null) {
                if (recipe.isLimited()) {
                    recipe.decrease();
                }

                if (!recipe.getAchievement().isEmpty()) {
                    AchievementManager.progressAchievement(this.client.getHabbo(), Emulator.getGameEnvironment().getAchievementManager().getAchievement(recipe.getAchievement()));

                }

                this.client.sendResponse(new CraftingResultComposer(recipe));
                this.client.getHabbo().getInventory().getItemsComponent().addItem(rewardItem);
                this.client.sendResponse(new AddHabboItemComposer(rewardItem));
                AchievementManager.progressAchievement(this.client.getHabbo(), Emulator.getGameEnvironment().getAchievementManager().getAchievement("Atcg"));
                toRemove.forEachValue(object -> {
                    CraftingCraftItemEvent.this.client.sendResponse(new RemoveHabboItemComposer(object.getGiftAdjustedId()));
                    return true;
                });
                this.client.sendResponse(new InventoryRefreshComposer());

                Emulator.getThreading().run(new QueryDeleteHabboItems(toRemove));
                return;
            }

            // Reward creation failed after we already pulled the ingredients —
            // restore them so the craft isn't a silent item sink.
            this.restoreItems(toRemove);
        }

        this.client.sendResponse(new CraftingResultComposer(null));
    }

    private void restoreItems(TIntObjectHashMap<HabboItem> items) {
        if (items.isEmpty()) {
            return;
        }
        items.forEachValue(item -> {
            this.client.getHabbo().getInventory().getItemsComponent().addItem(item);
            this.client.sendResponse(new AddHabboItemComposer(item));
            return true;
        });
        this.client.sendResponse(new InventoryRefreshComposer());
    }
}
