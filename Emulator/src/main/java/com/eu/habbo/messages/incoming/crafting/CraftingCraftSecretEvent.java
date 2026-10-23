package com.eu.habbo.messages.incoming.crafting;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.achievements.AchievementManager;
import com.eu.habbo.habbohotel.crafting.CraftingAltar;
import com.eu.habbo.habbohotel.crafting.CraftingRecipe;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.BuildersClubItemIds;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.catalog.AlertLimitedSoldOutComposer;
import com.eu.habbo.messages.outgoing.crafting.CraftingResultComposer;
import com.eu.habbo.messages.outgoing.inventory.AddHabboItemComposer;
import com.eu.habbo.messages.outgoing.inventory.InventoryRefreshComposer;
import com.eu.habbo.messages.outgoing.inventory.RemoveHabboItemComposer;
import com.eu.habbo.threading.runnables.QueryDeleteHabboItem;
import com.eu.habbo.util.PacketGuard;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public class CraftingCraftSecretEvent extends MessageHandler {
    static final int MAX_SECRET_CRAFT_INGREDIENTS = 50;

    @Override
    public void handle() throws Exception {
        int altarId = BuildersClubItemIds.fromWire(this.currentRoom(), this.packet.readInt());
        int count = this.packet.readInt();

        if (count <= 0
                || !PacketGuard.isValidIntList(count, this.packet.bytesAvailable(), MAX_SECRET_CRAFT_INGREDIENTS)) {
            this.client.sendResponse(new CraftingResultComposer(null));
            return;
        }

        CraftingAltar altar = this.altarAt(altarId);

        if (altar != null) {
            // HabboItem has no equals: the set tracks each inventory item once, in the order the packet lists them.
            Set<HabboItem> habboItems = new LinkedHashSet<>();
            Map<Item, Integer> items = new HashMap<>();

            for (int i = 0; i < count; i++) {
                int itemId = BuildersClubItemIds.fromWire(this.currentRoom(), this.packet.readInt());
                HabboItem habboItem = this.client
                        .getHabbo()
                        .getInventory()
                        .getItemsComponent()
                        .getHabboItem(itemId);

                if (habboItem == null || !habboItems.add(habboItem)) {
                    this.client.sendResponse(new CraftingResultComposer(null));
                    return;
                }

                items.merge(habboItem.getBaseItem(), 1, Integer::sum);
            }

            CraftingRecipe recipe = altar.getRecipe(items);

            if (recipe != null) {
                // The unit is taken before the reward exists, so two crafters cannot both pass for the last one.
                if (!recipe.canBeCrafted() || (recipe.isLimited() && !recipe.decrease())) {
                    this.client.sendResponse(new AlertLimitedSoldOutComposer());
                    return;
                }

                HabboItem rewardItem = Emulator.getGameEnvironment()
                        .getItemManager()
                        .createItem(this.client.getHabbo().getHabboInfo().getId(), recipe.getReward(), 0, 0, "");

                if (rewardItem != null) {
                    this.complete(recipe, rewardItem, habboItems);
                    return;
                }

                if (recipe.isLimited()) recipe.increase();
            }
        }

        this.client.sendResponse(new CraftingResultComposer(null));
    }

    /** The altar the furni in the crafter's room is, or null. */
    private CraftingAltar altarAt(int altarId) {
        Room room = this.client.getHabbo().getHabboInfo().getCurrentRoom();
        if (room == null) return null;

        HabboItem craftingAltar = room.getHabboItem(altarId);
        if (craftingAltar == null) return null;

        return Emulator.getGameEnvironment().getCraftingManager().getAltar(craftingAltar.getBaseItem());
    }

    /** Hands out the reward, records a first discovery and consumes the furni laid on the altar. */
    private void complete(CraftingRecipe recipe, HabboItem rewardItem, Set<HabboItem> habboItems) {
        if (!recipe.getAchievement().isEmpty()) {
            AchievementManager.progressAchievement(
                    this.client.getHabbo(),
                    Emulator.getGameEnvironment().getAchievementManager().getAchievement(recipe.getAchievement()));
        }

        this.client.sendResponse(new CraftingResultComposer(recipe));
        if (!this.client.getHabbo().getHabboStats().hasRecipe(recipe.getId())) {
            this.client.getHabbo().getHabboStats().addRecipe(recipe.getId());
            AchievementManager.progressAchievement(
                    this.client.getHabbo(),
                    Emulator.getGameEnvironment().getAchievementManager().getAchievement("AtcgSecret"));
        }
        this.client.getHabbo().getInventory().getItemsComponent().addItem(rewardItem);
        this.client.sendResponse(new AddHabboItemComposer(rewardItem));
        for (HabboItem item : habboItems) {
            this.client.getHabbo().getInventory().getItemsComponent().removeHabboItem(item);
            this.client.sendResponse(new RemoveHabboItemComposer(item.getGiftAdjustedId()));
            Emulator.getThreading().runPersistence(new QueryDeleteHabboItem(item.getId()));
        }
        this.client.sendResponse(new InventoryRefreshComposer());
    }
}
