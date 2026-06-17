package com.eu.habbo.messages.incoming.housekeeping;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.housekeeping.HousekeepingActionResultComposer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Generic non-credits currency grant. Wire field `currencyType`:
 * 0 => duckets / pixels, 5 => diamonds, 101 => seasonal-primary.
 * Online users go through Habbo.givePoints / givePixels which dispatches
 * a UserCurrencyComposer; offline goes straight to `users_currency`.
 */
public class HousekeepingGiveCurrencyEvent extends MessageHandler {
    private static final int CURRENCY_DUCKETS = 0;

    @Override
    public int getRatelimit() {
        return 1000;
    }

    @Override
    public void handle() throws Exception {
        if (!this.client.getHabbo().hasPermission(Permission.ACC_HOUSEKEEPING)) {
            return;
        }

        int userId = this.packet.readInt();
        int currencyType = this.packet.readInt();
        int amount = this.packet.readInt();

        String actionKey = "user.give_currency_" + currencyType;

        if (userId <= 0 || !HousekeepingMutationGuard.isCurrencyType(currencyType) || !HousekeepingMutationGuard.isPositiveGrantAmount(amount)) {
            this.client.sendResponse(new HousekeepingActionResultComposer(actionKey, false, 0, "housekeeping.error.invalid_input"));
            return;
        }

        if (!HousekeepingTargetRankGuard.canTargetUser(this.client.getHabbo(), userId)) {
            this.client.sendResponse(new HousekeepingActionResultComposer(actionKey, false, 0, "housekeeping.error.rank_too_high"));
            return;
        }

        if (!HousekeepingMutationGuard.userExists(userId)) {
            this.client.sendResponse(new HousekeepingActionResultComposer(actionKey, false, 0, "housekeeping.error.user_not_found"));
            return;
        }

        Habbo online = Emulator.getGameEnvironment().getHabboManager().getHabbo(userId);

        if (online != null) {
            // givePixels writes users_currency type=0 and ships UserCurrency;
            // givePoints(type, amount) is the generalised path for everything else.
            if (currencyType == CURRENCY_DUCKETS) {
                online.givePixels(amount);
            } else {
                online.givePoints(currencyType, amount);
            }

            this.audit(actionKey, userId, currencyType, amount);
            this.client.sendResponse(new HousekeepingActionResultComposer(actionKey, true, userId, ""));
            return;
        }

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO users_currency (user_id, type, amount) VALUES (?, ?, ?) " +
                     "ON DUPLICATE KEY UPDATE amount = amount + VALUES(amount)")) {
            statement.setInt(1, userId);
            statement.setInt(2, currencyType);
            statement.setInt(3, amount);
            statement.executeUpdate();
        } catch (SQLException e) {
            this.client.sendResponse(new HousekeepingActionResultComposer(actionKey, false, 0, "housekeeping.error.db_failed"));
            return;
        }

        this.audit(actionKey, userId, currencyType, amount);
        this.client.sendResponse(new HousekeepingActionResultComposer(actionKey, true, userId, ""));
    }

    private void audit(String actionKey, int userId, int currencyType, int amount) {
        com.eu.habbo.habbohotel.modtool.HousekeepingAuditLog.log(
                this.client.getHabbo().getHabboInfo().getId(),
                this.client.getHabbo().getHabboInfo().getUsername(),
                actionKey, userId, "type=" + currencyType + " amount=" + amount,
                this.client.getHabbo().getHabboInfo().getIpLogin());
    }
}
