package polaris.messages.incoming.crafting;

import java.util.HashMap;
import java.util.Map;
import polaris.Emulator;
import polaris.hotel.crafting.CraftingAltar;
import polaris.hotel.crafting.CraftingRecipe;
import polaris.hotel.items.Item;
import polaris.hotel.rooms.BuildersClubItemIds;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.UserItem;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.crafting.CraftingRecipesAvailableComposer;
import polaris.util.PacketGuard;

public class RequestCraftingRecipesAvailableEvent extends MessageHandler {
    static final int MAX_CRAFTING_INGREDIENTS = 50;

    @Override
    public void handle() throws Exception {
        int altarId = BuildersClubItemIds.fromWire(this.currentRoom(), this.packet.readInt());

        Room room = this.client.getUser().getUserInfo().getCurrentRoom();
        if (room == null) {
            return;
        }

        UserItem item = room.getUserItem(altarId);
        if (item == null) {
            return;
        }

        CraftingAltar altar = Emulator.getGameEnvironment().getCraftingManager().getAltar(item.getBaseItem());

        if (altar != null) {
            Map<Item, Integer> items = new HashMap<>();

            int count = this.packet.readInt();
            if (!PacketGuard.isValidIntList(count, this.packet.bytesAvailable(), MAX_CRAFTING_INGREDIENTS)) {
                return;
            }

            for (int i = 0; i < count; i++) {
                UserItem userItem = this.client
                        .getUser()
                        .getInventory()
                        .getItemsComponent()
                        .getUserItem(BuildersClubItemIds.fromWire(this.currentRoom(), this.packet.readInt()));

                if (userItem != null) {
                    if (!items.containsKey(userItem.getBaseItem())) {
                        items.put(userItem.getBaseItem(), 0);
                    }

                    items.put(userItem.getBaseItem(), items.get(userItem.getBaseItem()) + 1);
                }
            }

            CraftingRecipe equalsRecipe = altar.getRecipe(items);
            if (equalsRecipe != null && this.client.getUser().getUserStats().hasRecipe(equalsRecipe.getId())) {
                // this.client.sendResponse(new CraftingRecipesAvailableComposer(-1, true));
                // this.client.sendResponse(new CraftingRecipeComposer(equalsRecipe));
                // this.client.sendResponse(new CraftingResultComposer(equalsRecipe, true));
                return;
            }
            Map<CraftingRecipe, Boolean> recipes = altar.matchRecipes(items);

            boolean found = false;
            int c = recipes.size();
            for (Map.Entry<CraftingRecipe, Boolean> set : recipes.entrySet()) {
                if (this.client.getUser().getUserStats().hasRecipe(set.getKey().getId())) {
                    c--;
                    continue;
                }

                if (set.getValue()) {
                    found = true;
                    break;
                }
            }
            this.client.sendResponse(new CraftingRecipesAvailableComposer(c, found));
        }
    }
}
