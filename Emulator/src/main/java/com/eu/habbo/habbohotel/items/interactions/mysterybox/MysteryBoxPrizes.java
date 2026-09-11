package com.eu.habbo.habbohotel.items.interactions.mysterybox;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.catalog.CatalogItem;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.outgoing.inventory.AddHabboItemComposer;
import com.eu.habbo.messages.outgoing.inventory.InventoryRefreshComposer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * What comes out of a mystery box. The pool is catalog items, so a hotel builds it the way it builds
 * everything it sells: the row names a catalog item and the winner receives whatever that item
 * contains, bundles and all. A colour can have its own pool; rows with no colour belong to every box.
 */
public class MysteryBoxPrizes {
    private static final Logger LOGGER = LoggerFactory.getLogger(MysteryBoxPrizes.class);

    private static final MysteryBoxPrizes INSTANCE = new MysteryBoxPrizes();

    private MysteryBoxPrizes() {}

    public static MysteryBoxPrizes getInstance() {
        return INSTANCE;
    }

    /** One prize, as it is configured: which catalog item, and how likely it is. */
    public record Prize(int catalogItemId, int weight) {}

    /** The pool for a colour: its own rows plus the ones that belong to every colour. */
    public List<Prize> pool(String colour) {
        List<Prize> prizes = new ArrayList<>();

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT catalog_item_id, weight FROM mystery_box_prizes WHERE enabled = 1"
                                + " AND (colour = ? OR colour = '')")) {
            statement.setString(1, colour == null ? "" : colour);

            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    prizes.add(new Prize(set.getInt("catalog_item_id"), Math.max(1, set.getInt("weight"))));
                }
            }
        } catch (SQLException exception) {
            LOGGER.error("Caught SQL exception", exception);
        }

        return prizes;
    }

    /**
     * Draws a prize for this player and hands it over. Returns the class id the client draws in the
     * window, or zero when the pool is empty or the catalog item behind it has gone.
     */
    public int award(Habbo habbo, String colour) {
        if (habbo == null || habbo.getClient() == null) return 0;

        List<Prize> pool = this.pool(colour);

        if (pool.isEmpty()) return 0;

        CatalogItem catalogItem = Emulator.getGameEnvironment()
                .getCatalogManager()
                .getCatalogItem(this.draw(pool).catalogItemId());

        if (catalogItem == null) return 0;

        int classId = 0;
        boolean given = false;

        for (Item baseItem : catalogItem.getBaseItems()) {
            HabboItem item = Emulator.getGameEnvironment()
                    .getItemManager()
                    .createItem(habbo.getHabboInfo().getId(), baseItem, 0, 0, "");

            if (item == null) continue;

            habbo.getInventory().getItemsComponent().addItem(item);
            habbo.getClient().sendResponse(new AddHabboItemComposer(item));

            if (classId == 0) classId = baseItem.getSpriteId();

            given = true;
        }

        if (given) habbo.getClient().sendResponse(new InventoryRefreshComposer());

        return classId;
    }

    /** Picks one prize, each as likely as its weight says. */
    Prize draw(List<Prize> pool) {
        int total = 0;

        for (Prize prize : pool) {
            total += prize.weight();
        }

        int roll = Emulator.getRandom().nextInt(Math.max(1, total));

        for (Prize prize : pool) {
            roll -= prize.weight();

            if (roll < 0) return prize;
        }

        return pool.get(pool.size() - 1);
    }
}
