package polaris.messages.outgoing.catalog;

import polaris.hotel.catalog.TargetOffer;
import polaris.hotel.users.User;
import polaris.hotel.users.cache.UserOfferPurchase;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class TargetedOfferComposer extends MessageComposer {
    private final User user;
    private final TargetOffer offer;

    public TargetedOfferComposer(User user, TargetOffer offer) {
        this.user = user;
        this.offer = offer;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.TargetedOfferComposer);
        UserOfferPurchase purchase = UserOfferPurchase.getOrCreate(this.user, this.offer.getId());
        this.offer.serialize(this.response, purchase);
        return this.response;
    }

    public User getUser() {
        return user;
    }

    public TargetOffer getOffer() {
        return offer;
    }
}
