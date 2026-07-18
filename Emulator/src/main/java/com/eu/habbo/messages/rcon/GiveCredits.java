package com.eu.habbo.messages.rcon;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.economy.EconomyLedger;
import com.eu.habbo.habbohotel.economy.EconomyOperation;
import com.eu.habbo.habbohotel.economy.EconomyOperationId;
import com.eu.habbo.habbohotel.users.Habbo;
import com.google.gson.Gson;
import jakarta.validation.constraints.Positive;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GiveCredits extends RCONMessage<GiveCredits.JSONGiveCredits> {
    private static final Logger LOGGER = LoggerFactory.getLogger(GiveCredits.class);


    public GiveCredits() {
        super(JSONGiveCredits.class);
    }

    @Override
    public void handle(Gson gson, JSONGiveCredits object) {
        int maxAmount = RconGrantGuard.parseMaxAmount(
                Emulator.getConfig().getValue("rcon.grant.max_amount", String.valueOf(RconGrantGuard.DEFAULT_MAX_AMOUNT)));
        String validationError = RconGrantGuard.validateUserId(object.user_id);
        if (validationError == null) {
            validationError = RconGrantGuard.validatePositiveAmount(object.credits, maxAmount, "credits");
        }
        if (validationError != null) {
            this.status = RCONMessage.STATUS_ERROR;
            this.message = validationError;
            return;
        }

        Habbo habbo = Emulator.getGameEnvironment().getHabboManager().getHabbo(object.user_id);
        String operationId = EconomyOperationId.create("rcon:credits:" + object.user_id);

        if (habbo != null) {
            habbo.giveCredits(object.credits, "rcon.givecredits", operationId, null);
        } else {
            if (!RconUserLookup.userExists(object.user_id)) {
                this.status = RCONMessage.HABBO_NOT_FOUND;
                this.message = "user not found";
                return;
            }
            try {
                EconomyLedger.execute(new EconomyOperation(
                        operationId, object.user_id, null, "credit_grant", "rcon.givecredits",
                        EconomyLedger.CREDITS, object.credits, null, ""));
            } catch (Exception e) {
                this.status = RCONMessage.SYSTEM_ERROR;
                LOGGER.error("Caught SQL exception", e);
                return;
            }

            this.message = "offline";
        }
    }

    static class JSONGiveCredits {

        @Positive(message = "invalid user")
        public int user_id;


        @Positive(message = "invalid credits")
        public int credits;
    }
}
