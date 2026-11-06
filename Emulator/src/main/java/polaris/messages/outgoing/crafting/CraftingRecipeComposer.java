package polaris.messages.outgoing.crafting;

import java.util.Map;
import polaris.hotel.crafting.CraftingRecipe;
import polaris.hotel.items.Item;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class CraftingRecipeComposer extends MessageComposer {
    private final CraftingRecipe recipe;

    public CraftingRecipeComposer(CraftingRecipe recipe) {
        this.recipe = recipe;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.CraftingRecipeComposer);
        this.response.appendInt(this.recipe.getIngredients().size());

        for (Map.Entry<Item, Integer> ingredient : this.recipe.getIngredients().entrySet()) {
            this.response.appendInt(ingredient.getValue());
            this.response.appendString(ingredient.getKey().getName());
        }
        return this.response;
    }

    public CraftingRecipe getRecipe() {
        return recipe;
    }
}
