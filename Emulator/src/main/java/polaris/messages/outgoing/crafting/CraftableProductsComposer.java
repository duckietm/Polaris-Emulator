package polaris.messages.outgoing.crafting;

import java.util.Collection;
import java.util.List;
import polaris.hotel.crafting.CraftingRecipe;
import polaris.hotel.items.Item;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class CraftableProductsComposer extends MessageComposer {
    private final List<CraftingRecipe> recipes;
    private final Collection<Item> ingredients;

    public CraftableProductsComposer(List<CraftingRecipe> recipes, Collection<Item> ingredients) {
        this.recipes = recipes;
        this.ingredients = ingredients;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.CraftableProductsComposer);

        this.response.appendInt(this.recipes.size());
        for (CraftingRecipe recipe : this.recipes) {
            this.response.appendString(recipe.getName());
            this.response.appendString(recipe.getReward().getName());
        }

        this.response.appendInt(this.ingredients.size());
        for (Item item : this.ingredients) {
            this.response.appendString(item.getName());
        }

        return this.response;
    }

    public List<CraftingRecipe> getRecipes() {
        return recipes;
    }

    public Collection<Item> getIngredients() {
        return ingredients;
    }
}
