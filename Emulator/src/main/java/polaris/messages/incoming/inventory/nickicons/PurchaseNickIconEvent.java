package polaris.messages.incoming.inventory.nickicons;

import java.sql.SQLException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.Emulator;
import polaris.hotel.users.User;
import polaris.hotel.users.customization.UserCustomizationPurchaseService;
import polaris.hotel.users.customization.UserCustomizationPurchaseService.PurchaseResult;
import polaris.hotel.users.customization.UserCustomizationRepository;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.generic.alerts.BubbleAlertComposer;
import polaris.messages.outgoing.generic.alerts.BubbleAlertKeys;
import polaris.messages.outgoing.inventory.nickicons.UserNickIconsComposer;
import polaris.messages.outgoing.users.UserCurrencyComposer;

public class PurchaseNickIconEvent extends MessageHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(PurchaseNickIconEvent.class);

    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() throws Exception {
        User user = this.client.getUser();
        if (user == null) {
            return;
        }

        String requestedIconKey = this.normalizeIconKey(this.packet.readString());
        if (requestedIconKey.isEmpty()) {
            this.fail("Invalid nick icon selected.");
            return;
        }

        UserCustomizationPurchaseService service = new UserCustomizationPurchaseService(
                new UserCustomizationRepository(Emulator.getDatabase().getDataSource()), List.of());
        try {
            PurchaseResult result = service.purchaseNickIcon(user, requestedIconKey);
            if (result.status() != UserCustomizationPurchaseService.Status.SUCCESS) {
                this.fail(result.message());
                return;
            }
            if (result.currencyChanged()) {
                this.client.sendResponse(new UserCurrencyComposer(user));
            }
            result.nickIcon().run();
            user.getInventory().getNickIconsComponent().addNickIcon(result.nickIcon());
            this.client.sendResponse(new UserNickIconsComposer(user));
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
