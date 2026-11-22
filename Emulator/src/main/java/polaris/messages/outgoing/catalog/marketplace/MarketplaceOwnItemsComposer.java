package polaris.messages.outgoing.catalog.marketplace;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.Emulator;
import polaris.hotel.catalog.marketplace.MarketPlaceOffer;
import polaris.hotel.catalog.marketplace.MarketPlaceState;
import polaris.hotel.users.User;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class MarketplaceOwnItemsComposer extends MessageComposer {
    private static final Logger LOGGER = LoggerFactory.getLogger(MarketplaceOwnItemsComposer.class);

    private final User user;

    public MarketplaceOwnItemsComposer(User user) {
        this.user = user;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.MarketplaceOwnItemsComposer);
        this.response.appendInt(this.user.getInventory().getSoldPriceTotal());
        this.response.appendInt(this.user.getInventory().getMarketplaceItems().size());

        for (MarketPlaceOffer offer : this.user.getInventory().getMarketplaceItems()) {
            try {
                if (offer.getState() == MarketPlaceState.OPEN) {
                    if (offer.secondsLeft(Emulator.getIntUnixTimestamp()) <= 0) {
                        offer.setState(MarketPlaceState.CLOSED);
                        Emulator.getThreading().run(offer);
                    }
                }

                this.response.appendInt(offer.getOfferId());
                this.response.appendInt(offer.getState().getState());
                this.response.appendInt(offer.getType());
                if (offer.getType() == 5) {
                    // A collectible: AIR reads the product code, the furni and its kind (1 floor, 2 wall).
                    this.response.appendString(offer.getProductCode());
                    this.response.appendInt(offer.getItemId());
                    this.response.appendInt(offer.getNftFurniType());
                } else {
                    this.response.appendInt(offer.getItemId());
                    if (offer.getType() == 3) {
                        this.response.appendInt(offer.getLimitedNumber());
                        this.response.appendInt(offer.getLimitedStack());
                    } else if (offer.getType() == 2) {
                        this.response.appendString("");
                    } else {
                        this.response.appendInt(0);
                        this.response.appendString("");
                    }
                }

                this.response.appendInt(offer.getPrice());

                if (offer.getState() == MarketPlaceState.OPEN)
                    this.response.appendInt(offer.secondsLeft(Emulator.getIntUnixTimestamp()) / 60);
                else this.response.appendInt(0);

                this.response.appendInt(0);

                // A sold or expired offer carries when that happened: a 64-bit time in milliseconds, as its high
                // and low halves. The client reads it for exactly these two states.
                if (offer.getState() == MarketPlaceState.SOLD || offer.getState() == MarketPlaceState.CLOSED) {
                    long statusTime = statusTimeMillis(offer);
                    this.response.appendInt((int) (statusTime >>> 32));
                    this.response.appendInt((int) statusTime);
                }
            } catch (Exception e) {
                LOGGER.error("Caught exception", e);
            }
        }

        return this.response;
    }

    /** When a sold offer was sold, or when an expired one ran out. */
    static long statusTimeMillis(MarketPlaceOffer offer) {
        if (offer.getState() == MarketPlaceState.SOLD && offer.getSoldTimestamp() > 0) {
            return offer.getSoldTimestamp() * 1000L;
        }
        return (offer.getTimestamp() + (long) Math.max(0, offer.secondsLeft(offer.getTimestamp()))) * 1000L;
    }

    public User getUser() {
        return user;
    }
}
