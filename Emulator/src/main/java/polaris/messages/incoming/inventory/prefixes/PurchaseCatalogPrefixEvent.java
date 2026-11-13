package polaris.messages.incoming.inventory.prefixes;

import java.sql.SQLException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.Emulator;
import polaris.hotel.users.User;
import polaris.hotel.users.customization.UserCustomizationPurchaseService;
import polaris.hotel.users.customization.UserCustomizationPurchaseService.PurchaseResult;
import polaris.hotel.users.customization.UserCustomizationRepository;
import polaris.hotel.users.inventory.PrefixUpdates;
import polaris.hotel.users.inventory.PrefixesComponent;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.generic.alerts.BubbleAlertComposer;
import polaris.messages.outgoing.generic.alerts.BubbleAlertKeys;
import polaris.messages.outgoing.inventory.nickicons.UserNickIconsComposer;
import polaris.messages.outgoing.inventory.prefixes.CustomPrefixPurchaseFailedComposer;
import polaris.messages.outgoing.users.UserCurrencyComposer;

public class PurchaseCatalogPrefixEvent extends MessageHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(PurchaseCatalogPrefixEvent.class);
    private static final String UNAVAILABLE_MESSAGE = "This prefix is no longer available.";

    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() throws Exception {
        int catalogPrefixId = this.packet.readInt();
        User user = this.client.getUser();
        if (user == null || catalogPrefixId <= 0) {
            return;
        }

        UserCustomizationPurchaseService service = new UserCustomizationPurchaseService(
                new UserCustomizationRepository(Emulator.getDatabase().getDataSource()), List.of());
        PrefixesComponent prefixes = user.getInventory().getPrefixesComponent();
        PurchaseResult result;
        try {
            // One purchase at a time per user: the ownership and limit checks and the insert would otherwise race.
            synchronized (prefixes) {
                result = service.purchaseCatalogPrefix(user, catalogPrefixId);
                if (result.status() == UserCustomizationPurchaseService.Status.SUCCESS) {
                    prefixes.addPrefix(result.prefix());
                    prefixes.setActive(result.prefix().getId());
                }
            }
        } catch (SQLException exception) {
            LOGGER.error("Caught SQL exception while purchasing catalog prefix", exception);
            this.fail("Unable to complete the purchase.");
            return;
        }

        switch (result.status()) {
            case UNAVAILABLE -> this.fail(UNAVAILABLE_MESSAGE);
            case FAILURE -> this.fail(result.message());
            case REFRESH ->
                // Already owned: the inventory refresh puts the client back in step.
                this.client.sendResponse(new UserNickIconsComposer(user));
            case SUCCESS -> {
                if (result.currencyChanged()) {
                    this.client.sendResponse(new UserCurrencyComposer(user));
                }
                // A bought prefix is worn straight away, the same as a custom one.
                PrefixUpdates.received(user, result.prefix());
            }
        }
    }

    // The failure packet ends the purchase in the customizer, which otherwise stayed locked waiting for an answer.
    private void fail(String message) {
        this.client.sendResponse(new BubbleAlertComposer(BubbleAlertKeys.FURNITURE_PLACEMENT_ERROR.key, message));
        this.client.sendResponse(new CustomPrefixPurchaseFailedComposer(message));
    }
}
