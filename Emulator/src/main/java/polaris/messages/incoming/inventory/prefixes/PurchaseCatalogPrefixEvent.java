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
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.generic.alerts.BubbleAlertComposer;
import polaris.messages.outgoing.generic.alerts.BubbleAlertKeys;
import polaris.messages.outgoing.inventory.nickicons.UserNickIconsComposer;
import polaris.messages.outgoing.users.UserCurrencyComposer;

public class PurchaseCatalogPrefixEvent extends MessageHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(PurchaseCatalogPrefixEvent.class);

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
        try {
            PurchaseResult result = service.purchaseCatalogPrefix(user, catalogPrefixId);
            switch (result.status()) {
                case UNAVAILABLE -> {
                    return;
                }
                case FAILURE -> {
                    this.client.sendResponse(
                            new BubbleAlertComposer(BubbleAlertKeys.FURNITURE_PLACEMENT_ERROR.key, result.message()));
                    return;
                }
                case SUCCESS -> {
                    if (result.currencyChanged()) {
                        this.client.sendResponse(new UserCurrencyComposer(user));
                    }
                    result.prefix().run();
                    user.getInventory().getPrefixesComponent().addPrefix(result.prefix());
                }
                case REFRESH -> {
                    // The established response is the same inventory refresh
                    // used after a successful purchase.
                }
            }
            this.client.sendResponse(new UserNickIconsComposer(user));
        } catch (SQLException exception) {
            LOGGER.error("Caught SQL exception while purchasing catalog prefix", exception);
        }
    }
}
