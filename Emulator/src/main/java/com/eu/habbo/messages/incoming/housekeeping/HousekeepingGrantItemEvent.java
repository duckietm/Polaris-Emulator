package com.eu.habbo.messages.incoming.housekeeping;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.housekeeping.HousekeepingActionResultComposer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Grant a furni item (by items_base id) `quantity` times. Each row in
 * the `items` table is one furni unit; quantity > 1 just batches the
 * insert. The online user's HabboInventory isn't proactively refreshed
 * — they'll see the new items next time they open the hand inventory
 * (or after a relog).
 */
public class HousekeepingGrantItemEvent extends MessageHandler {
    private static final String ACTION_KEY = "user.grant_item";
    private static final int MAX_QUANTITY_PER_CALL = 100;

    @Override
    public int getRatelimit() {
        return 2000;
    }

    @Override
    public void handle() throws Exception {
        if (!this.client.getHabbo().hasPermission(Permission.ACC_HOUSEKEEPING)) {
            return;
        }

        int userId = this.packet.readInt();
        int itemId = this.packet.readInt();
        int quantity = this.packet.readInt();

        if (userId <= 0 || itemId <= 0 || quantity <= 0) {
            this.client.sendResponse(new HousekeepingActionResultComposer(ACTION_KEY, false, 0, "housekeeping.error.invalid_input"));
            return;
        }

        if (!HousekeepingTargetRankGuard.canTargetUser(this.client.getHabbo(), userId)) {
            this.client.sendResponse(new HousekeepingActionResultComposer(ACTION_KEY, false, 0, "housekeeping.error.rank_too_high"));
            return;
        }

        if (!HousekeepingMutationGuard.userExists(userId)) {
            this.client.sendResponse(new HousekeepingActionResultComposer(ACTION_KEY, false, 0, "housekeeping.error.user_not_found"));
            return;
        }

        if (!HousekeepingMutationGuard.itemExists(itemId)) {
            this.client.sendResponse(new HousekeepingActionResultComposer(ACTION_KEY, false, 0, "housekeeping.error.item_not_found"));
            return;
        }

        if (quantity > MAX_QUANTITY_PER_CALL) {
            quantity = MAX_QUANTITY_PER_CALL;
        }

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement("INSERT INTO items (user_id, item_id, extra_data) VALUES (?, ?, '')")) {
            for (int i = 0; i < quantity; i++) {
                statement.setInt(1, userId);
                statement.setInt(2, itemId);
                statement.addBatch();
            }
            statement.executeBatch();
        } catch (SQLException e) {
            this.client.sendResponse(new HousekeepingActionResultComposer(ACTION_KEY, false, 0, "housekeeping.error.economy_failed"));
            return;
        }

        com.eu.habbo.habbohotel.modtool.HousekeepingAuditLog.log(
                this.client.getHabbo().getHabboInfo().getId(),
                this.client.getHabbo().getHabboInfo().getUsername(),
                ACTION_KEY, userId, "itemId=" + itemId + " quantity=" + quantity,
                this.client.getHabbo().getHabboInfo().getIpLogin());
        this.client.sendResponse(new HousekeepingActionResultComposer(ACTION_KEY, true, userId, ""));
    }
}
