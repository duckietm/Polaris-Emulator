package polaris.messages.incoming.collectibles;

import java.util.Optional;
import polaris.hotel.collectibles.CollectibleData.MintTokenOffer;
import polaris.hotel.collectibles.CollectiblesService;
import polaris.hotel.collectibles.CollectiblesSettings;
import polaris.hotel.users.User;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.collectibles.CollectibleMintTokensComposer;
import polaris.messages.outgoing.collectibles.CollectiblePurchaseResultComposer;

/**
 * AIR 15 stamp purchase (189, {@code offerId, wallet}), the purchase confirmation of a
 * {@code MINT_TOKEN} offer: takes its silver price and adds its stamps. Answers {@code 169}, the stamp
 * balance and the currencies.
 */
public class PurchaseMintTokensEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 1000;
    }

    @Override
    public void handle() throws Exception {
        User user = this.client.getUser();
        if (user == null) return;

        int offerId = this.packet.readInt();
        String wallet = this.packet.readString();

        CollectiblesSettings settings = CollectiblesSupport.settings();
        CollectiblesService service = CollectiblesSupport.service(settings);
        if (service == null) return;

        Optional<MintTokenOffer> offer = service.mintTokenOffer(offerId);
        if (offer.isEmpty() || offer.get().amount() <= 0 || !CollectiblesSupport.ownsWallet(user, wallet)) {
            this.client.sendResponse(new CollectiblePurchaseResultComposer(1));
            return;
        }

        int price = offer.get().silverPrice();
        if (!CollectiblesSupport.pay(user, settings.silverType(), price, "collectibles.stamps")) {
            this.client.sendResponse(new CollectiblePurchaseResultComposer(1));
            return;
        }

        int userId = user.getUserInfo().getId();
        boolean added;
        try {
            added = service.addMintTokens(userId, offer.get().amount());
        } catch (Exception exception) {
            // The silver is already taken: stamps that cannot be stored refund it, as a refused store does.
            CollectiblesSupport.refund(user, settings.silverType(), price, "collectibles.stamps.refund");
            this.client.sendResponse(new CollectiblePurchaseResultComposer(1));
            throw exception;
        }
        if (!added) {
            CollectiblesSupport.refund(user, settings.silverType(), price, "collectibles.stamps.refund");
            this.client.sendResponse(new CollectiblePurchaseResultComposer(1));
            return;
        }

        this.client.sendResponse(new CollectiblePurchaseResultComposer(0));
        this.client.sendResponse(new CollectibleMintTokensComposer(service.mintTokens(userId)));
        CollectiblesSupport.sendBalances(user, settings);
    }
}
