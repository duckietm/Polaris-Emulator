package com.eu.habbo.messages.incoming.crafting;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.crafting.CraftingAltar;
import com.eu.habbo.habbohotel.crafting.CraftingRecipe;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.util.PacketGuard;
import com.eu.habbo.messages.outgoing.crafting.CraftingRecipesAvailableComposer;

import java.util.HashMap;
import java.util.Map;

public class RequestCraftingRecipesAvailableEvent extends MessageHandler {
    static final int MAX_CRAFTING_INGREDIENTS = 50;

    @Override
    public void handle() throws Exception {
        int altarId = this.packet.readInt();

        Room room = this.client.getHabbo().getHabboInfo().getCurrentRoom();
        if (room == null) {
            return;
        }

        HabboItem item = room.getHabboItem(altarId);
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
                HabboItem habboItem = this.client.getHabbo().getInventory().getItemsComponent().getHabboItem(this.packet.readInt());

                if (habboItem != null) {
                    if (!items.containsKey(habboItem.getBaseItem())) {
                        items.put(habboItem.getBaseItem(), 0);
                    }

                    items.put(habboItem.getBaseItem(), items.get(habboItem.getBaseItem()) + 1);
                }
            }

            CraftingRecipe equalsRecipe = altar.getRecipe(items);
            if (equalsRecipe != null && this.client.getHabbo().getHabboStats().hasRecipe(equalsRecipe.getId())) {
                //this.client.sendResponse(new CraftingRecipesAvailableComposer(-1, true));
                //this.client.sendResponse(new CraftingRecipeComposer(equalsRecipe));
                //this.client.sendResponse(new CraftingResultComposer(equalsRecipe, true));
                return;
            }
            Map<CraftingRecipe, Boolean> recipes = altar.matchRecipes(items);

            boolean found = false;
            int c = recipes.size();
            for (Map.Entry<CraftingRecipe, Boolean> set : recipes.entrySet()) {
                if (this.client.getHabbo().getHabboStats().hasRecipe(set.getKey().getId())) {
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
