package polaris.hotel.crafting;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.Emulator;
import polaris.hotel.items.Item;

public class CraftingManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(CraftingManager.class);

    // Built whole by reload and swapped in: a reload used to clear the altars and fill them after the query, so a
    // player crafting during :update_catalogue found no recipe, and a failed query left crafting empty.
    private volatile Map<Item, CraftingAltar> altars;

    public CraftingManager() {
        this.altars = new HashMap<>();

        this.reload();
    }

    public void reload() {
        this.saveRemaining();

        Map<Item, CraftingAltar> loaded = loadAltars();
        if (loaded != null) this.altars = loaded;
    }

    /** The altars and their recipes as the database holds them now; null when it could not be read. */
    private static Map<Item, CraftingAltar> loadAltars() {
        Map<Item, CraftingAltar> altars = new HashMap<>();

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement("SELECT * FROM crafting_altars_recipes "
                        + "INNER JOIN crafting_recipes ON crafting_altars_recipes.recipe_id = crafting_recipes.id "
                        + "INNER JOIN crafting_recipes_ingredients ON crafting_recipes.id = crafting_recipes_ingredients.recipe_id "
                        + "WHERE crafting_recipes.enabled = ? ORDER BY altar_id ASC")) {
            statement.setString(1, "1");
            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    Item item = Emulator.getGameEnvironment().getItemManager().getItem(set.getInt("altar_id"));

                    if (item == null) continue;

                    CraftingAltar altar = altars.computeIfAbsent(item, CraftingAltar::new);
                    CraftingRecipe recipe = altar.getRecipe(set.getInt("crafting_recipes_ingredients.recipe_id"));

                    if (recipe == null) {
                        recipe = new CraftingRecipe(set);
                        altar.addRecipe(recipe);
                    }

                    Item ingredientItem = Emulator.getGameEnvironment()
                            .getItemManager()
                            .getItem(set.getInt("crafting_recipes_ingredients.item_id"));

                    if (ingredientItem != null) {
                        recipe.addIngredient(ingredientItem, set.getInt("crafting_recipes_ingredients.amount"));
                        altar.addIngredient(ingredientItem);
                    } else {
                        LOGGER.error("Unknown ingredient item {}", set.getInt("crafting_recipes_ingredients.item_id"));
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
            return null;
        }

        return altars;
    }

    public int getRecipesWithItemCount(final Item item) {
        int count = 0;

        for (CraftingAltar altar : this.altars.values()) {
            if (altar.hasIngredient(item)) {
                count++;
            }
        }

        return count;
    }

    public CraftingRecipe getRecipe(String recipeName) {
        CraftingRecipe recipe;
        for (CraftingAltar altar : this.altars.values()) {
            recipe = altar.getRecipe(recipeName);

            if (recipe != null) {
                return recipe;
            }
        }

        return null;
    }

    public CraftingAltar getAltar(Item item) {
        return this.altars.get(item);
    }

    /** Writes how many of each limited recipe are left, so a reload or a restart reads the count it reached. */
    private void saveRemaining() {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement =
                        connection.prepareStatement("UPDATE crafting_recipes SET remaining = ? WHERE id = ? LIMIT 1")) {
            for (CraftingAltar altar : this.altars.values()) {
                for (CraftingRecipe recipe : altar.getRecipes()) {
                    if (recipe.isLimited()) {
                        statement.setInt(1, recipe.getRemaining());
                        statement.setInt(2, recipe.getId());

                        statement.addBatch();
                    }
                }
            }
            statement.executeBatch();
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
    }

    public void dispose() {
        this.saveRemaining();
        this.altars = new HashMap<>();
    }
}
