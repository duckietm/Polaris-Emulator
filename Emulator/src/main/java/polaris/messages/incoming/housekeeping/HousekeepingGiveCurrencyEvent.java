package polaris.messages.incoming.housekeeping;

import polaris.Emulator;
import polaris.hotel.economy.EconomyLedger;
import polaris.hotel.economy.EconomyOperation;
import polaris.hotel.economy.EconomyOperationId;
import polaris.hotel.users.User;
import polaris.messages.outgoing.housekeeping.HousekeepingActionResultComposer;

/**
 * Generic non-credits currency grant. Wire field `currencyType`:
 * 0 => duckets / pixels, 5 => diamonds, 101 => seasonal-primary.
 * Online users go through User.givePoints / givePixels which dispatches
 * a UserCurrencyComposer; offline goes straight to `users_currency`.
 */
public class HousekeepingGiveCurrencyEvent extends HousekeepingHandler {
    @Override
    protected String requiredPermission() {
        return HousekeepingAreas.ECONOMY;
    }

    private static final int CURRENCY_DUCKETS = 0;

    @Override
    public int getRatelimit() {
        return 1000;
    }

    @Override
    public void handle() throws Exception {
        if (!this.allowed()) {
            return;
        }

        int userId = this.packet.readInt();
        int currencyType = this.packet.readInt();
        int amount = this.packet.readInt();

        String actionKey = "user.give_currency_" + currencyType;

        if (userId <= 0
                || !HousekeepingMutationGuard.isCurrencyType(currencyType)
                || !HousekeepingMutationGuard.isPositiveGrantAmount(amount)) {
            this.client.sendResponse(
                    new HousekeepingActionResultComposer(actionKey, false, 0, "housekeeping.error.invalid_input"));
            return;
        }

        if (!HousekeepingTargetRankGuard.canTargetUser(this.client.getUser(), userId)) {
            this.client.sendResponse(
                    new HousekeepingActionResultComposer(actionKey, false, 0, "housekeeping.error.rank_too_high"));
            return;
        }

        if (!HousekeepingMutationGuard.userExists(userId)) {
            this.client.sendResponse(
                    new HousekeepingActionResultComposer(actionKey, false, 0, "housekeeping.error.user_not_found"));
            return;
        }

        User online = Emulator.getGameEnvironment().getUserManager().getUser(userId);
        int actorId = this.client.getUser().getUserInfo().getId();
        String operationId = EconomyOperationId.create("housekeeping:currency:" + userId + ":" + currencyType);

        if (online != null) {
            // givePixels writes users_currency type=0 and ships UserCurrency;
            // givePoints(type, amount) is the generalised path for everything else.
            if (currencyType == CURRENCY_DUCKETS) {
                online.givePoints(currencyType, amount, "housekeeping.user.give_currency", operationId, actorId);
            } else {
                online.givePoints(currencyType, amount, "housekeeping.user.give_currency", operationId, actorId);
            }

            this.audit(actionKey, userId, currencyType, amount);
            this.client.sendResponse(new HousekeepingActionResultComposer(actionKey, true, userId, ""));
            return;
        }

        try {
            EconomyLedger.execute(new EconomyOperation(
                    operationId,
                    userId,
                    actorId,
                    "currency_grant",
                    "housekeeping.user.give_currency",
                    currencyType,
                    amount,
                    null,
                    actionKey));
        } catch (Exception e) {
            this.client.sendResponse(
                    new HousekeepingActionResultComposer(actionKey, false, 0, "housekeeping.error.db_failed"));
            return;
        }

        this.audit(actionKey, userId, currencyType, amount);
        this.client.sendResponse(new HousekeepingActionResultComposer(actionKey, true, userId, ""));
    }

    private void audit(String actionKey, int userId, int currencyType, int amount) {
        polaris.hotel.modtool.HousekeepingAuditLog.log(
                this.client.getUser().getUserInfo().getId(),
                this.client.getUser().getUserInfo().getUsername(),
                actionKey,
                userId,
                "type=" + currencyType + " amount=" + amount,
                this.client.getUser().getUserInfo().getIpLogin());
    }
}
