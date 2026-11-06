package polaris.messages.incoming.crafting;

import java.util.HashMap;
import java.util.Map;
import polaris.Emulator;
import polaris.hotel.achievements.AchievementManager;
import polaris.hotel.crafting.CraftingAltar;
import polaris.hotel.crafting.CraftingRecipe;
import polaris.hotel.items.Item;
import polaris.hotel.rooms.BuildersClubItemIds;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.UserItem;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.catalog.AlertLimitedSoldOutComposer;
import polaris.messages.outgoing.crafting.CraftingResultComposer;
import polaris.messages.outgoing.inventory.AddUserItemComposer;
import polaris.messages.outgoing.inventory.InventoryRefreshComposer;
import polaris.messages.outgoing.inventory.RemoveUserItemComposer;

public class CraftingCraftItemEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int craftingTable = BuildersClubItemIds.fromWire(this.currentRoom(), this.packet.readInt());
        CraftingAltar altar = this.altarAt(craftingTable);
        if (altar == null) {
            this.client.sendResponse(new CraftingResultComposer(null));
            return;
        }

        CraftingRecipe recipe = altar.getRecipe(this.packet.readString());

        // A secret recipe is listed, and so crafted by name, only once it was discovered on the altar.
        if (recipe != null
                && recipe.isSecret()
                && !this.client.getUser().getUserStats().hasRecipe(recipe.getId())) {
            recipe = null;
        }

        if (recipe != null) {
            if (!recipe.canBeCrafted()) {
                this.client.sendResponse(new AlertLimitedSoldOutComposer());
                return;
            }

            Map<Integer, UserItem> toRemove = this.takeIngredients(recipe);
            if (toRemove == null) {
                // One ingredient short: the crafting window waits for a result either way.
                this.client.sendResponse(new CraftingResultComposer(null));
                return;
            }

            // The unit is taken before the reward exists, so two crafters cannot both pass for the last one.
            if (recipe.isLimited() && !recipe.decrease()) {
                this.restoreItems(toRemove);
                this.client.sendResponse(new AlertLimitedSoldOutComposer());
                return;
            }

            // The ingredients are deleted and the reward created in one transaction: before, the reward
            // was saved first and the ingredients deleted later in the background, so a failed delete kept both.
            UserItem rewardItem = Emulator.getGameEnvironment()
                    .getItemManager()
                    .craftInTransaction(
                            this.client.getUser().getUserInfo().getId(), recipe.getReward(), toRemove.values());

            if (rewardItem != null) {
                this.complete(recipe, rewardItem, toRemove);
                return;
            }

            // Reward creation failed after we already pulled the ingredients —
            // restore them so the craft isn't a silent item sink.
            if (recipe.isLimited()) recipe.increase();
            this.restoreItems(toRemove);
        }

        this.client.sendResponse(new CraftingResultComposer(null));
    }

    /** The altar the crafting table in the buyer's room is, or null. */
    private CraftingAltar altarAt(int craftingTable) {
        Room room = this.client.getUser().getUserInfo().getCurrentRoom();
        if (room == null) return null;

        UserItem item = room.getUserItem(craftingTable);
        if (item == null) return null;

        return Emulator.getGameEnvironment().getCraftingManager().getAltar(item.getBaseItem());
    }

    /** Pulls every ingredient out of the inventory, or none: null when one is missing. */
    private Map<Integer, UserItem> takeIngredients(CraftingRecipe recipe) {
        Map<Integer, UserItem> toRemove = new HashMap<>();
        for (Map.Entry<Item, Integer> set : recipe.getIngredients().entrySet()) {
            for (int i = 0; i < set.getValue(); i++) {
                UserItem userItem = this.client
                        .getUser()
                        .getInventory()
                        .getItemsComponent()
                        .getAndRemoveUnrentedUserItem(set.getKey());

                if (userItem == null) {
                    // Not enough ingredients — give back whatever we already pulled.
                    this.restoreItems(toRemove);
                    return null;
                }

                toRemove.put(userItem.getId(), userItem);
            }
        }
        return toRemove;
    }

    private void complete(CraftingRecipe recipe, UserItem rewardItem, Map<Integer, UserItem> toRemove) {
        if (!recipe.getAchievement().isEmpty()) {
            AchievementManager.progressAchievement(
                    this.client.getUser(),
                    Emulator.getGameEnvironment().getAchievementManager().getAchievement(recipe.getAchievement()));
        }

        this.client.sendResponse(new CraftingResultComposer(recipe));
        this.client.getUser().getInventory().getItemsComponent().addItem(rewardItem);
        this.client.sendResponse(new AddUserItemComposer(rewardItem));
        AchievementManager.progressAchievement(
                this.client.getUser(),
                Emulator.getGameEnvironment().getAchievementManager().getAchievement("Atcg"));
        toRemove.values().forEach(object -> {
            CraftingCraftItemEvent.this.client.sendResponse(new RemoveUserItemComposer(object.getGiftAdjustedId()));
        });
        this.client.sendResponse(new InventoryRefreshComposer());
    }

    private void restoreItems(Map<Integer, UserItem> items) {
        if (items.isEmpty()) {
            return;
        }
        items.values().forEach(item -> {
            this.client.getUser().getInventory().getItemsComponent().addItem(item);
            this.client.sendResponse(new AddUserItemComposer(item));
        });
        this.client.sendResponse(new InventoryRefreshComposer());
    }
}
