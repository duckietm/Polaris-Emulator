package polaris.messages.outgoing.camera;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class CameraPriceComposer extends MessageComposer {
    public final int credits;
    public final int points;
    public final int pointsType;

    public CameraPriceComposer(int credits, int points, int pointsType) {
        this.credits = credits;
        this.points = points;
        this.pointsType = pointsType;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.CameraPriceComposer);
        this.response.appendInt(this.credits);
        this.response.appendInt(this.points);
        this.response.appendInt(this.pointsType);
        return this.response;
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
