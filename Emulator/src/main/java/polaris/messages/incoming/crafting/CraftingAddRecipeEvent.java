package polaris.messages.incoming.crafting;

import polaris.Emulator;
import polaris.hotel.crafting.CraftingRecipe;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.catalog.AlertLimitedSoldOutComposer;
import polaris.messages.outgoing.crafting.CraftingRecipeComposer;

public class CraftingAddRecipeEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        String recipeName = this.packet.readString();
        CraftingRecipe recipe =
                Emulator.getGameEnvironment().getCraftingManager().getRecipe(recipeName);

        if (recipe != null) {
            if (!recipe.canBeCrafted()) {
                this.client.sendResponse(new AlertLimitedSoldOutComposer());
                return;
            }

            this.client.sendResponse(new CraftingRecipeComposer(recipe));
        }
    }
}
