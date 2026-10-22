package com.eu.habbo.messages.outgoing.catalog.marketplace;

import com.eu.habbo.habbohotel.catalog.marketplace.MarketplaceTokenService;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public class MarketplaceConfigComposer extends MessageComposer {
    @Override
    protected ServerMessage composeInternal() {
        // The official client reads the third and fourth fields as the selling-token batch: its price in
        // credits and the number of tokens, shown in the buy_marketplace_tokens window.
        MarketplaceTokenService.Settings tokens = MarketplaceTokenService.currentSettings();
        this.response.init(Outgoing.MarketplaceConfigComposer);
        this.response.appendBoolean(true);
        this.response.appendInt(1); // Commision Percentage.
        this.response.appendInt(tokens.price()); // Credits for one batch of selling tokens
        this.response.appendInt(tokens.amount()); // Selling tokens (advertisements) in one batch
        this.response.appendInt(1); // Min price
        this.response.appendInt(1000000); // Max price
        this.response.appendInt(48); // Hours in marketplace
        this.response.appendInt(7); // Days to display
        return this.response;
    }
}
