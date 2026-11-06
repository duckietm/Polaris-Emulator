package polaris.messages.outgoing.crafting;

import polaris.hotel.crafting.CraftingRecipe;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class CraftingResultComposer extends MessageComposer {
    private final CraftingRecipe recipe;
    private final boolean succes;

    public CraftingResultComposer(CraftingRecipe recipe) {
        this.recipe = recipe;
        this.succes = this.recipe != null;
    }

    public CraftingResultComposer(CraftingRecipe recipe, boolean success) {
        this.recipe = recipe;
        this.succes = success;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.CraftingResultComposer);

        this.response.appendBoolean(this.succes); // succes

        if (this.recipe != null) {
            this.response.appendString(this.recipe.getName());
            this.response.appendString(this.recipe.getReward().getName());
        }

        return this.response;
    }

    public CraftingRecipe getRecipe() {
        return recipe;
    }

    public boolean isSucces() {
        return succes;
    }
}
