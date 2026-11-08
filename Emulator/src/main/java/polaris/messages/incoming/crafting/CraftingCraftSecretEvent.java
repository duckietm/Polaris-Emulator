package polaris.messages.incoming.crafting;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
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
import polaris.util.PacketGuard;

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
            // UserItem has no equals: the set tracks each inventory item once, in the order the packet lists them.
            Set<UserItem> userItems = new LinkedHashSet<>();
            Map<Item, Integer> items = new HashMap<>();

            for (int i = 0; i < count; i++) {
                int itemId = BuildersClubItemIds.fromWire(this.currentRoom(), this.packet.readInt());
                UserItem userItem =
                        this.client.getUser().getInventory().getItemsComponent().getUserItem(itemId);

                // A rented furni is no ingredient: trades, the marketplace and the recycler refuse it too.
                if (userItem == null || !userItems.add(userItem) || userItem.hasRentPeriod()) {
                    this.client.sendResponse(new CraftingResultComposer(null));
                    return;
                }

                items.merge(userItem.getBaseItem(), 1, Integer::sum);
            }

            CraftingRecipe recipe = altar.getRecipe(items);

            if (recipe != null) {
                // The unit is taken before the reward exists, so two crafters cannot both pass for the last one.
                if (!recipe.canBeCrafted() || (recipe.isLimited() && !recipe.decrease())) {
                    this.client.sendResponse(new AlertLimitedSoldOutComposer());
                    return;
                }

                // The furni on the altar are deleted and the reward created in one transaction.
                UserItem rewardItem = Emulator.getGameEnvironment()
                        .getItemManager()
                        .craftInTransaction(
                                this.client.getUser().getUserInfo().getId(),
                                recipe.getReward(),
                                userItems,
                                recipe.isLimited() ? recipe.getId() : 0);

                if (rewardItem != null) {
                    this.complete(recipe, rewardItem, userItems);
                    return;
                }

                if (recipe.isLimited()) recipe.increase();
            }
        }

        this.client.sendResponse(new CraftingResultComposer(null));
    }

    /** The altar the furni in the crafter's room is, or null. */
    private CraftingAltar altarAt(int altarId) {
        Room room = this.client.getUser().getUserInfo().getCurrentRoom();
        if (room == null) return null;

        UserItem craftingAltar = room.getUserItem(altarId);
        if (craftingAltar == null) return null;

        return Emulator.getGameEnvironment().getCraftingManager().getAltar(craftingAltar.getBaseItem());
    }

    /** Hands out the reward, records a first discovery and consumes the furni laid on the altar. */
    private void complete(CraftingRecipe recipe, UserItem rewardItem, Set<UserItem> userItems) {
        if (!recipe.getAchievement().isEmpty()) {
            AchievementManager.progressAchievement(
                    this.client.getUser(),
                    Emulator.getGameEnvironment().getAchievementManager().getAchievement(recipe.getAchievement()));
        }

        this.client.sendResponse(new CraftingResultComposer(recipe));
        if (!this.client.getUser().getUserStats().hasRecipe(recipe.getId())) {
            this.client.getUser().getUserStats().addRecipe(recipe.getId());
            AchievementManager.progressAchievement(
                    this.client.getUser(),
                    Emulator.getGameEnvironment().getAchievementManager().getAchievement("AtcgSecret"));
        }
        this.client.getUser().getInventory().getItemsComponent().addItem(rewardItem);
        this.client.sendResponse(new AddUserItemComposer(rewardItem));
        for (UserItem item : userItems) {
            this.client.getUser().getInventory().getItemsComponent().removeUserItem(item);
            this.client.sendResponse(new RemoveUserItemComposer(item.getGiftAdjustedId()));
        }
        this.client.sendResponse(new InventoryRefreshComposer());
    }
}
