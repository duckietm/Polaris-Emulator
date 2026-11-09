package polaris.messages.outgoing.catalog.marketplace;

import polaris.hotel.catalog.marketplace.MarketplaceTokenService;
import polaris.hotel.catalog.marketplace.NftMarketplaceSettings;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

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
        // AIR reads three more before the collectibles part: the seller's fee percentage, the revenue limit and the
        // half-tax limit. Furni keep the commission on the buyer's side, so the seller's fee is 0 and the half-tax
        // never applies (AIR divides by the limit, so "none" is the largest int rather than 0).
        this.response.appendInt(0);
        this.response.appendInt(1000000);
        this.response.appendInt(Integer.MAX_VALUE);
        // AIR's Collectibles marketplace, read while bytes remain: on/off, the price range, the offer time in
        // minutes, the open offers one user may have, the fee percentage and the half-tax limit.
        NftMarketplaceSettings nft = NftMarketplaceSettings.current();
        this.response.appendBoolean(nft.active());
        this.response.appendInt(nft.minPrice());
        this.response.appendInt(nft.maxPrice());
        this.response.appendInt(nft.offerMinutes());
        this.response.appendInt(nft.maxOpenOffers());
        this.response.appendInt(nft.feePercent());
        this.response.appendInt(nft.halfTaxLimit() > 0 ? nft.halfTaxLimit() : Integer.MAX_VALUE);
        return this.response;
    }
}
