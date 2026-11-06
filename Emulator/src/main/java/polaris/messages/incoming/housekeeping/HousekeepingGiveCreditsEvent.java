package polaris.messages.incoming.housekeeping;

import polaris.Emulator;
import polaris.hotel.economy.EconomyLedger;
import polaris.hotel.economy.EconomyOperation;
import polaris.hotel.economy.EconomyOperationId;
import polaris.hotel.users.User;
import polaris.messages.outgoing.housekeeping.HousekeepingActionResultComposer;

public class HousekeepingGiveCreditsEvent extends HousekeepingHandler {
    @Override
    protected String requiredPermission() {
        return HousekeepingAreas.ECONOMY;
    }

    private static final String ACTION_KEY = "user.give_credits";

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
        int amount = this.packet.readInt();

        if (userId <= 0 || !HousekeepingMutationGuard.isPositiveGrantAmount(amount)) {
            this.client.sendResponse(
                    new HousekeepingActionResultComposer(ACTION_KEY, false, 0, "housekeeping.error.invalid_input"));
            return;
        }

        if (!HousekeepingTargetRankGuard.canTargetUser(this.client.getUser(), userId)) {
            this.client.sendResponse(
                    new HousekeepingActionResultComposer(ACTION_KEY, false, 0, "housekeeping.error.rank_too_high"));
            return;
        }

        User online = Emulator.getGameEnvironment().getUserManager().getUser(userId);
        int actorId = this.client.getUser().getUserInfo().getId();
        String operationId = EconomyOperationId.create("housekeeping:credits:" + userId);

        if (online != null) {
            // giveCredits already pushes UserCreditsComposer and persists via the
            // standard UserInfo write path; nothing extra needed for the online branch.
            online.giveCredits(amount, "housekeeping.user.give_credits", operationId, actorId);
            this.audit(userId, amount);
            this.client.sendResponse(new HousekeepingActionResultComposer(ACTION_KEY, true, userId, ""));
            return;
        }

        try {
            EconomyLedger.execute(new EconomyOperation(
                    operationId,
                    userId,
                    actorId,
                    "credit_grant",
                    "housekeeping.user.give_credits",
                    EconomyLedger.CREDITS,
                    amount,
                    null,
                    ACTION_KEY));
        } catch (Exception e) {
            this.client.sendResponse(
                    new HousekeepingActionResultComposer(ACTION_KEY, false, 0, "housekeeping.error.db_failed"));
            return;
        }

        this.audit(userId, amount);
        this.client.sendResponse(new HousekeepingActionResultComposer(ACTION_KEY, true, userId, ""));
    }

    private void audit(int userId, int amount) {
        polaris.hotel.modtool.HousekeepingAuditLog.log(
                this.client.getUser().getUserInfo().getId(),
                this.client.getUser().getUserInfo().getUsername(),
                ACTION_KEY,
                userId,
                "amount=" + amount,
                this.client.getUser().getUserInfo().getIpLogin());
    }
}
