package polaris.messages.outgoing.catalog;

import polaris.hotel.catalog.ClubOffer;
import polaris.hotel.users.User;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class ClubExtendOfferComposer extends MessageComposer {
    private final User user;
    private final ClubOffer offer;
    private final int normalCreditCost;
    private final int normalPointsCost;
    private final int pointsType;
    private final int daysRemaining;

    public ClubExtendOfferComposer(
            User user, ClubOffer offer, int normalCreditCost, int normalPointsCost, int pointsType, int daysRemaining) {
        this.user = user;
        this.offer = offer;
        this.normalCreditCost = normalCreditCost;
        this.normalPointsCost = normalPointsCost;
        this.pointsType = pointsType;
        this.daysRemaining = daysRemaining;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.ClubExtendOfferComposer);
        this.offer.serialize(this.response, this.user.getUserStats().getClubExpireTimestamp());

        this.response.appendInt(this.normalCreditCost);
        this.response.appendInt(this.normalPointsCost);
        this.response.appendInt(this.pointsType);
        this.response.appendInt(this.daysRemaining);
        return this.response;
    }

    public User getUser() {
        return user;
    }

    public ClubOffer getOffer() {
        return offer;
    }

    public int getNormalCreditCost() {
        return normalCreditCost;
    }

    public int getNormalPointsCost() {
        return normalPointsCost;
    }

    public int getPointsType() {
        return pointsType;
    }

    public int getDaysRemaining() {
        return daysRemaining;
    }
}
