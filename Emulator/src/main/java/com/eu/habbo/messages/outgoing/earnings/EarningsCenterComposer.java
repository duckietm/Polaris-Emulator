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
        this.response.appendString(entry.category().getKey());
        this.response.appendBoolean(entry.enabled());
        this.response.appendBoolean(entry.claimable());
        this.response.appendInt(entry.nextClaimAt());
        this.response.appendInt(entry.rewards().size());

        for (EarningsReward reward : entry.rewards()) {
            this.response.appendString(reward.type());
            this.response.appendInt(reward.amount());
            this.response.appendInt(reward.pointsType());
            this.response.appendString(reward.data());
        }
    }
}
