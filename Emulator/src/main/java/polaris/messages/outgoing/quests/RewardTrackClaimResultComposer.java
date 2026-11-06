package polaris.messages.outgoing.quests;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

/** RewardTrackClaimResult (9451, the official 596 collides): 0 is success, else the fail code. */
public class RewardTrackClaimResultComposer extends MessageComposer {
    private final String trackId;
    private final String rewardId;
    private final int resultCode;

    public RewardTrackClaimResultComposer(String trackId, String rewardId, int resultCode) {
        this.trackId = trackId;
        this.rewardId = rewardId;
        this.resultCode = resultCode;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.RewardTrackClaimResultComposer);
        this.response.appendString(this.trackId);
        this.response.appendString(this.rewardId);
        this.response.appendInt(this.resultCode);
        return this.response;
    }

    public String getTrackId() {
        return trackId;
    }

    public String getRewardId() {
        return rewardId;
    }

    public int getResultCode() {
        return resultCode;
    }
}
