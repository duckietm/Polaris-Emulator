package polaris.messages.outgoing.hotelview;

import java.util.List;
import polaris.hotel.communitygoals.CommunityGoalPrize;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

/**
 * AIR {@code CommunityGoalEarnedPrizes} (3319): {@code int count}, then per prize the
 * {@code PrizeData} fields {@code int communityGoalId, string communityGoalCode,
 * int userRank, string rewardCode, bool badge, string localizedName}.
 */
public class CommunityGoalEarnedPrizesComposer extends MessageComposer {
    private final List<CommunityGoalPrize> prizes;

    public CommunityGoalEarnedPrizesComposer(List<CommunityGoalPrize> prizes) {
        this.prizes = prizes == null ? List.of() : List.copyOf(prizes);
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.CommunityGoalEarnedPrizesComposer);
        this.response.appendInt(this.prizes.size());

        for (CommunityGoalPrize prize : this.prizes) {
            this.response.appendInt(prize.communityGoalId());
            this.response.appendString(prize.communityGoalCode());
            this.response.appendInt(prize.userRank());
            this.response.appendString(prize.rewardCode());
            this.response.appendBoolean(prize.badge());
            this.response.appendString(prize.localizedName());
        }

        return this.response;
    }

    public List<CommunityGoalPrize> getPrizes() {
        return prizes;
    }
}
