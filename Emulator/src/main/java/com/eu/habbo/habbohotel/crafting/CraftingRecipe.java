package com.eu.habbo.habbohotel.crafting;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.Item;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

public class CraftingRecipe {
    private final int id;
    private final String name;
    private final Item reward;
    private final boolean secret;
    private final String achievement;
    private final boolean limited;
    private final Map<Item, Integer> ingredients;
    private int remaining;

    public CraftingRecipe(ResultSet set) throws SQLException {
        this.id = set.getInt("id");
        this.name = set.getString("product_name");
        this.reward = Emulator.getGameEnvironment().getItemManager().getItem(set.getInt("reward"));
        this.secret = set.getString("secret").equals("1");
        this.achievement = set.getString("achievement");
        this.limited = set.getString("limited").equals("1");
        this.remaining = set.getInt("remaining");

        this.ingredients = new HashMap<>();
    }

    public boolean canBeCrafted() {
        return !this.limited || this.remaining > 0;
    }

    public synchronized boolean decrease() {
        if (this.remaining > 0) {
            this.remaining--;
            return true;
        }

        return false;
    }

    public void addIngredient(Item item, int amount) {
        this.ingredients.put(item, amount);
    }

    public int getAmountNeeded(Item item) {
        return this.ingredients.get(item);
    }

    public boolean hasIngredient(Item item) {
        return this.ingredients.containsKey(item);
    }

    public int getId() {
        return this.id;
    }

    public String getName() {
        return this.name;
    }

    public Item getReward() {
        return this.reward;
    }

    public boolean isSecret() {
        return this.secret;
    }

    public String getAchievement() {
        return this.achievement;
    }

    public boolean isLimited() {
        return this.limited;
    }

    public Map<Item, Integer> getIngredients() {
        return this.ingredients;
    }

    public int getRemaining() {
        return this.remaining;
    }
}
