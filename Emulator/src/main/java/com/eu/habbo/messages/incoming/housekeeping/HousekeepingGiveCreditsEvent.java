package com.eu.habbo.messages.incoming.housekeeping;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.economy.EconomyLedger;
import com.eu.habbo.habbohotel.economy.EconomyOperation;
import com.eu.habbo.habbohotel.economy.EconomyOperationId;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.housekeeping.HousekeepingActionResultComposer;

public class HousekeepingGiveCreditsEvent extends MessageHandler {
    private static final String ACTION_KEY = "user.give_credits";

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
        int amount = this.packet.readInt();

        if (userId <= 0 || !HousekeepingMutationGuard.isPositiveGrantAmount(amount)) {
            this.client.sendResponse(new HousekeepingActionResultComposer(ACTION_KEY, false, 0, "housekeeping.error.invalid_input"));
            return;
        }

        if (!HousekeepingTargetRankGuard.canTargetUser(this.client.getHabbo(), userId)) {
            this.client.sendResponse(new HousekeepingActionResultComposer(ACTION_KEY, false, 0, "housekeeping.error.rank_too_high"));
            return;
        }

        Habbo online = Emulator.getGameEnvironment().getHabboManager().getHabbo(userId);
        int actorId = this.client.getHabbo().getHabboInfo().getId();
        String operationId = EconomyOperationId.create("housekeeping:credits:" + userId);

        if (online != null) {
            // giveCredits already pushes UserCreditsComposer and persists via the
            // standard HabboInfo write path; nothing extra needed for the online branch.
            online.giveCredits(amount, "housekeeping.user.give_credits", operationId, actorId);
            this.audit(userId, amount);
            this.client.sendResponse(new HousekeepingActionResultComposer(ACTION_KEY, true, userId, ""));
            return;
        }

        try {
            EconomyLedger.execute(new EconomyOperation(
                    operationId, userId, actorId, "credit_grant", "housekeeping.user.give_credits",
                    EconomyLedger.CREDITS, amount, null, ACTION_KEY));
        } catch (Exception e) {
            this.client.sendResponse(new HousekeepingActionResultComposer(ACTION_KEY, false, 0, "housekeeping.error.db_failed"));
            return;
        }

        this.audit(userId, amount);
        this.client.sendResponse(new HousekeepingActionResultComposer(ACTION_KEY, true, userId, ""));
    }

    private void audit(int userId, int amount) {
        com.eu.habbo.habbohotel.modtool.HousekeepingAuditLog.log(
                this.client.getHabbo().getHabboInfo().getId(),
                this.client.getHabbo().getHabboInfo().getUsername(),
                ACTION_KEY, userId, "amount=" + amount,
                this.client.getHabbo().getHabboInfo().getIpLogin());
    }
}
