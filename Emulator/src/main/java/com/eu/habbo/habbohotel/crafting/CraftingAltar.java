package com.eu.habbo.habbohotel.crafting;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.users.Habbo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CraftingAltar {
    private final Item baseItem;
    private final Set<Item> ingredients;
    private final Map<Integer, CraftingRecipe> recipes;

    public CraftingAltar(Item baseItem) {
        this.baseItem = baseItem;

        this.ingredients = new HashSet<>(1);
        this.recipes = new HashMap<>(1);
    }

    public void addIngredient(Item item) {
        this.ingredients.add(item);
    }

    public boolean hasIngredient(Item item) {
        return this.ingredients.contains(item);
    }

    public Map<CraftingRecipe, Boolean> matchRecipes(Map<Item, Integer> amountMap) {
        Map<CraftingRecipe, Boolean> foundRecepies = new HashMap<>(Math.max(1, this.recipes.size() / 3));

        for (Map.Entry<Integer, CraftingRecipe> set : this.recipes.entrySet()) {
            boolean contains = true;
            boolean equals;

            if (set.getValue().isLimited() && !set.getValue().canBeCrafted()) {
                continue;
            }

            equals = amountMap.size() == set.getValue().getIngredients().size();

            for (Map.Entry<Item, Integer> entry : amountMap.entrySet()) {
                if (contains) {
                    if (set.getValue().getIngredients().containsKey(entry.getKey())) {
                        if (set.getValue().getIngredients().get(entry.getKey()).equals(entry.getValue())) {
                            continue;
                        }

                        equals = false;

                        if (set.getValue().getIngredients().get(entry.getKey()) > entry.getValue()) {
                            continue;
                        }
                    }

                    contains = false;
                }
            }

            if (contains) {
                foundRecepies.put(set.getValue(), equals);
            }
        }

        return foundRecepies;
    }


    public void addRecipe(CraftingRecipe recipe) {
        this.recipes.put(recipe.getId(), recipe);
    }

    public CraftingRecipe getRecipe(int id) {
        return this.recipes.get(id);
    }

    public CraftingRecipe getRecipe(String name) {
        for (Map.Entry<Integer, CraftingRecipe> set : this.recipes.entrySet()) {
            if (set.getValue().getName().equals(name)) {
                return set.getValue();
            }
        }

        return null;
    }

    public CraftingRecipe getRecipe(Map<Item, Integer> items) {
        for (Map.Entry<Integer, CraftingRecipe> set : this.recipes.entrySet()) {
            CraftingRecipe recipe = set.getValue();

            for (Map.Entry<Item, Integer> entry : recipe.getIngredients().entrySet()) {
                if (!(items.containsKey(entry.getKey()) && items.get(entry.getKey()).equals(entry.getValue()))) {
                    recipe = null;
                    break;
                }
            }

            if (recipe != null) {
                return recipe;
            }
        }

        return null;
    }

    public List<CraftingRecipe> getRecipesForHabbo(Habbo habbo) {
        List<CraftingRecipe> recipeList = new ArrayList<>();

        for (Map.Entry<Integer, CraftingRecipe> set : this.recipes.entrySet()) {
            if (!set.getValue().isSecret() || habbo.getHabboStats().hasRecipe(set.getValue().getId())) {
                recipeList.add(set.getValue());
            }
        }

        return recipeList;
    }

    public Item getBaseItem() {
        return this.baseItem;
    }

    public Collection<Item> getIngredients() {
        return this.ingredients;
    }

    public Collection<CraftingRecipe> getRecipes() {
        return this.recipes.values();
    }
}
