package polaris.messages.outgoing.earnings;

import java.util.List;
import polaris.hotel.earnings.EarningsClaimResult;
import polaris.hotel.earnings.EarningsEntry;
import polaris.hotel.earnings.EarningsReward;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class EarningsClaimResultComposer extends MessageComposer {
    private final List<EarningsClaimResult> results;

    public EarningsClaimResultComposer(EarningsClaimResult result) {
        this.results = List.of(result);
    }

    public EarningsClaimResultComposer(List<EarningsClaimResult> results) {
        this.results = results;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.EarningsClaimResultComposer);
        this.response.appendInt(this.results.size());

        for (EarningsClaimResult result : this.results) {
            this.response.appendString(result.getCategoryKey());
            this.response.appendString(result.getStatus().name().toLowerCase());
            this.response.appendBoolean(result.isSuccess());
            serializeEntry(result.getEntry());
        }

        return this.response;
    }

    private void serializeEntry(EarningsEntry entry) {
        this.response.appendBoolean(entry != null);
        if (entry == null) {
            return;
        }

        this.response.appendString(entry.getCategory().getKey());
        this.response.appendBoolean(entry.isEnabled());
        this.response.appendBoolean(entry.isClaimable());
        this.response.appendInt(entry.getNextClaimAt());
        this.response.appendInt(entry.getRewards().size());

        for (EarningsReward reward : entry.getRewards()) {
            this.response.appendString(reward.getType());
            this.response.appendInt(reward.getAmount());
            this.response.appendInt(reward.getPointsType());
            this.response.appendString(reward.getData());
        }
    }
}
