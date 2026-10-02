package com.eu.habbo.messages.incoming.inventory.nickicons;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.customization.UserCustomizationPurchaseService;
import com.eu.habbo.habbohotel.users.customization.UserCustomizationPurchaseService.PurchaseResult;
import com.eu.habbo.habbohotel.users.customization.UserCustomizationRepository;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertComposer;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertKeys;
import com.eu.habbo.messages.outgoing.inventory.nickicons.UserNickIconsComposer;
import com.eu.habbo.messages.outgoing.users.UserCurrencyComposer;
import java.sql.SQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PurchaseNickIconEvent extends MessageHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(PurchaseNickIconEvent.class);

    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() throws Exception {
        Habbo habbo = this.client.getHabbo();
        if (habbo == null) {
            return;
        }

        String requestedIconKey = this.normalizeIconKey(this.packet.readString());
        if (requestedIconKey.isEmpty()) {
            this.fail("Invalid nick icon selected.");
            return;
        }

        UserCustomizationPurchaseService service = new UserCustomizationPurchaseService(
                new UserCustomizationRepository(Emulator.getDatabase().getDataSource()));
        try {
            PurchaseResult result = service.purchaseNickIcon(habbo, requestedIconKey);
            if (result.status() != UserCustomizationPurchaseService.Status.SUCCESS) {
                this.fail(result.message());
                return;
            }
            if (result.currencyChanged()) {
                this.client.sendResponse(new UserCurrencyComposer(habbo));
            }
            result.nickIcon().run();
            habbo.getInventory().getNickIconsComponent().addNickIcon(result.nickIcon());
            this.client.sendResponse(new UserNickIconsComposer(habbo));
        } catch (SQLException exception) {
            LOGGER.error("Caught SQL exception", exception);
            this.fail("Unable to purchase this nick icon right now.");
        }
    }

    private void fail(String message) {
        this.client.sendResponse(new BubbleAlertComposer(BubbleAlertKeys.FURNITURE_PLACEMENT_ERROR.key, message));
    }

    private String normalizeIconKey(String iconKey) {
        if (iconKey == null) {
            return "";
        }

        String normalized = iconKey.trim().toLowerCase();
        if (normalized.endsWith(".gif")) {
            normalized = normalized.substring(0, normalized.length() - 4);
        }
        return normalized.matches("^[a-z0-9_-]+$") ? normalized : "";
    }
}
