package polaris.messages.outgoing.quests;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

/** RewardTrackPremiumPurchaseResult (2248): 0 is success, else the fail code; points after the purchase. */
public class RewardTrackPremiumPurchaseResultComposer extends MessageComposer {
    private final String trackId;
    private final int resultCode;
    private final int points;

    public RewardTrackPremiumPurchaseResultComposer(String trackId, int resultCode, int points) {
        this.trackId = trackId;
        this.resultCode = resultCode;
        this.points = points;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.RewardTrackPremiumPurchaseResultComposer);
        this.response.appendString(this.trackId);
        this.response.appendInt(this.resultCode);
        this.response.appendInt(this.points);
        return this.response;
    }

    public String getTrackId() {
        return trackId;
    }

    public int getResultCode() {
        return resultCode;
    }

    public int getPoints() {
        return points;
    }
}
