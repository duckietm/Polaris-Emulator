package com.eu.habbo.messages.incoming.collectibles;

import com.eu.habbo.habbohotel.collectibles.CollectibleData.MintTokenOffer;
import com.eu.habbo.habbohotel.collectibles.CollectiblesService;
import com.eu.habbo.habbohotel.collectibles.CollectiblesSettings;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.collectibles.CollectibleMintTokensComposer;
import com.eu.habbo.messages.outgoing.collectibles.CollectiblePurchaseResultComposer;
import java.util.Optional;

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
        Habbo habbo = this.client.getHabbo();
        if (habbo == null) return;

        int offerId = this.packet.readInt();
        String wallet = this.packet.readString();

        CollectiblesSettings settings = CollectiblesSupport.settings();
        CollectiblesService service = CollectiblesSupport.service(settings);
        if (service == null) return;

        Optional<MintTokenOffer> offer = service.mintTokenOffer(offerId);
        if (offer.isEmpty() || offer.get().amount() <= 0 || !CollectiblesSupport.ownsWallet(habbo, wallet)) {
            this.client.sendResponse(new CollectiblePurchaseResultComposer(1));
            return;
        }

        int price = offer.get().silverPrice();
        if (!CollectiblesSupport.pay(habbo, settings.silverType(), price, "collectibles.stamps")) {
            this.client.sendResponse(new CollectiblePurchaseResultComposer(1));
            return;
        }

        int userId = habbo.getHabboInfo().getId();
        if (!service.addMintTokens(userId, offer.get().amount())) {
            CollectiblesSupport.refund(habbo, settings.silverType(), price, "collectibles.stamps.refund");
            this.client.sendResponse(new CollectiblePurchaseResultComposer(1));
            return;
        }

        this.client.sendResponse(new CollectiblePurchaseResultComposer(0));
        this.client.sendResponse(new CollectibleMintTokensComposer(service.mintTokens(userId)));
        CollectiblesSupport.sendBalances(habbo, settings);
    }
}
