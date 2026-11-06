package polaris.messages.outgoing.rooms.items.rentablespaces;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class FurniRentOrBuyoutOfferComposer extends MessageComposer {
    private final boolean unknownBoolean1;
    private final String unknownString1;
    private final boolean unknownBoolean2;
    private final int credits;
    private final int points;
    private final int pointsType;

    public FurniRentOrBuyoutOfferComposer(
            boolean unknownBoolean1,
            String unknownString1,
            boolean unknownBoolean2,
            int credits,
            int points,
            int pointsType) {
        this.unknownBoolean1 = unknownBoolean1;
        this.unknownString1 = unknownString1;
        this.unknownBoolean2 = unknownBoolean2;
        this.credits = credits;
        this.points = points;
        this.pointsType = pointsType;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.FurniRentOrBuyoutOfferComposer);
        this.response.appendBoolean(this.unknownBoolean1);
        this.response.appendString(this.unknownString1);
        this.response.appendBoolean(this.unknownBoolean2);
        this.response.appendInt(this.credits);
        this.response.appendInt(this.points);
        this.response.appendInt(this.pointsType);
        return this.response;
    }

    public boolean isUnknownBoolean1() {
        return unknownBoolean1;
    }

    public String getUnknownString1() {
        return unknownString1;
    }

    public boolean isUnknownBoolean2() {
        return unknownBoolean2;
    }

    public int getCredits() {
        return credits;
    }

    public int getPoints() {
        return points;
    }

    public int getPointsType() {
        return pointsType;
    }
}
