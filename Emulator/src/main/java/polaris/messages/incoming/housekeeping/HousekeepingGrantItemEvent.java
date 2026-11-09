package polaris.messages.incoming.housekeeping;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.function.BooleanSupplier;
import polaris.Emulator;
import polaris.WiredPlatform;
import polaris.hotel.GameEnvironment;
import polaris.hotel.items.Item;
import polaris.hotel.items.interactions.wired.extra.WiredWebApiOwnership;

/**
 * Grant a furni item (by items_base id) `quantity` times. Each row in
 * the `items` table is one furni unit; quantity > 1 just batches the
 * insert. The online user's UserInventory isn't proactively refreshed
 * — they'll see the new items next time they open the hand inventory
 * (or after a relog).
 */
public class HousekeepingGrantItemEvent extends HousekeepingHandler {
    @Override
    protected String requiredPermission() {
        return HousekeepingAreas.ECONOMY;
    }

    public static final String ACTION_KEY = "user.grant_item";
    private static final int MAX_QUANTITY_PER_CALL = 100;

    @Override
    public int getRatelimit() {
        return 2000;
    }

    @Override
    public void handle() throws Exception {
        if (!this.allowed()) {
            return;
        }

        int userId = this.packet.readInt();
        int itemId = this.packet.readInt();
        int quantity = this.packet.readInt();

        this.reply(
                ACTION_KEY,
                grantItem(
                        this.operator(),
                        userId,
                        itemId,
                        quantity,
                        () -> HousekeepingTargetRankGuard.canTargetUser(this.client.getUser(), userId)));
    }

    /**
     * Gives a user {@code quantity} (capped at 100) units of a base item.
     *
     * @param mayTarget the operator's rank guard on the target
     */
    public static HousekeepingResult grantItem(
            HousekeepingOperator operator, int userId, int itemId, int quantity, BooleanSupplier mayTarget) {
        if (userId <= 0 || itemId <= 0 || quantity <= 0) {
            return HousekeepingResult.fail("housekeeping.error.invalid_input");
        }

        if (!mayTarget.getAsBoolean()) {
            return HousekeepingResult.fail("housekeeping.error.rank_too_high");
        }

        if (!HousekeepingMutationGuard.userExists(userId)) {
            return HousekeepingResult.fail("housekeeping.error.user_not_found");
        }

        if (!HousekeepingMutationGuard.itemExists(itemId)) {
            return HousekeepingResult.fail("housekeeping.error.item_not_found");
        }

        GameEnvironment environment = WiredPlatform.gameEnvironment();
        Item baseItem =
                environment == null ? null : environment.getItemManager().getItem(itemId);
        if (WiredWebApiOwnership.isWebApiItem(baseItem) && !WiredWebApiOwnership.canOwnMore(userId, quantity)) {
            return HousekeepingResult.fail(WiredWebApiOwnership.limitKey());
        }

        if (quantity > MAX_QUANTITY_PER_CALL) {
            quantity = MAX_QUANTITY_PER_CALL;
        }

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO items (user_id, item_id, extra_data) VALUES (?, ?, '')")) {
            for (int i = 0; i < quantity; i++) {
                statement.setInt(1, userId);
                statement.setInt(2, itemId);
                statement.addBatch();
            }
            statement.executeBatch();
        } catch (SQLException e) {
            return HousekeepingResult.fail("housekeeping.error.economy_failed");
        }

        polaris.hotel.modtool.HousekeepingAuditLog.log(
                operator.id(),
                operator.name(),
                ACTION_KEY,
                userId,
                "itemId=" + itemId + " quantity=" + quantity,
                operator.ip());
        return HousekeepingResult.ok(userId);
    }
}
