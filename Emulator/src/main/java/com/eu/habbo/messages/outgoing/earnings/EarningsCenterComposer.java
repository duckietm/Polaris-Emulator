package com.eu.habbo.messages.outgoing.earnings;

import com.eu.habbo.habbohotel.earnings.EarningsEntry;
import com.eu.habbo.habbohotel.earnings.EarningsReward;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

import java.util.List;

public class EarningsCenterComposer extends MessageComposer {
    private final List<EarningsEntry> entries;

    public EarningsCenterComposer(List<EarningsEntry> entries) {
        this.entries = entries;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.EarningsCenterComposer);
        this.response.appendInt(this.entries.size());

        for (EarningsEntry entry : this.entries) {
            serializeEntry(entry);
        }

        return this.response;
    }

    private void serializeEntry(EarningsEntry entry) {
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
