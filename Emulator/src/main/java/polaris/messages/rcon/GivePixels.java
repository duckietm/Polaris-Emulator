package polaris.messages.rcon;

import com.google.gson.Gson;
import jakarta.validation.constraints.Positive;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.Emulator;
import polaris.hotel.economy.EconomyLedger;
import polaris.hotel.economy.EconomyOperation;
import polaris.hotel.economy.EconomyOperationId;
import polaris.hotel.users.User;

public class GivePixels extends RCONMessage<GivePixels.JSONGivePixels> {
    private static final Logger LOGGER = LoggerFactory.getLogger(GivePixels.class);

    public GivePixels() {
        super(JSONGivePixels.class);
    }

    @Override
    public void handle(Gson gson, JSONGivePixels object) {
        int maxAmount = RconGrantGuard.parseMaxAmount(Emulator.getConfig()
                .getValue("rcon.grant.max_amount", String.valueOf(RconGrantGuard.DEFAULT_MAX_AMOUNT)));
        String validationError = RconGrantGuard.validateUserId(object.user_id);
        if (validationError == null) {
            validationError = RconGrantGuard.validatePositiveAmount(object.pixels, maxAmount, "pixels");
        }
        if (validationError != null) {
            this.status = RCONMessage.STATUS_ERROR;
            this.message = validationError;
            return;
        }

        User user = Emulator.getGameEnvironment().getUserManager().getUser(object.user_id);
        String operationId = EconomyOperationId.create("rcon:pixels:" + object.user_id);

        if (user != null) {
            user.givePoints(0, object.pixels, "rcon.givepixels", operationId, null);
        } else {
            if (!RconUserLookup.userExists(object.user_id)) {
                this.status = RCONMessage.USER_NOT_FOUND;
                this.message = "user not found";
                return;
            }

            try {
                EconomyLedger.execute(new EconomyOperation(
                        operationId,
                        object.user_id,
                        null,
                        "currency_grant",
                        "rcon.givepixels",
                        0,
                        object.pixels,
                        null,
                        ""));
            } catch (Exception e) {
                this.status = RCONMessage.SYSTEM_ERROR;
                LOGGER.error("Caught SQL exception", e);
            }

            this.message = "offline";
        }
    }

    static class JSONGivePixels {

        @Positive(message = "invalid user")
        public int user_id;

        @Positive(message = "invalid pixels")
        public int pixels;
    }
}
