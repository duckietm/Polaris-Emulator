package com.eu.habbo.messages.outgoing.earnings;

import com.eu.habbo.habbohotel.earnings.EarningsCategory;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

/**
 * Official {@code IncomeRewardNotificationMessageEvent} (1753): one byte, the reward category
 * that was just credited. {@code EarningsController.onIncomeRewardNotificationMessageEvent}
 * answers it with the {@code notification.earning.new} bubble linking to
 * {@code habboUI/open/vault} and refreshes the purse indicators.
 */
public class IncomeRewardNotificationComposer extends MessageComposer {
    private final int rewardCategory;

    public IncomeRewardNotificationComposer(int rewardCategory) {
        this.rewardCategory = rewardCategory;
    }

    public IncomeRewardNotificationComposer(EarningsCategory category) {
        this(category == null ? 0 : category.ordinal());
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.IncomeRewardNotificationComposer);
        this.response.appendByte(this.rewardCategory);
        return this.response;
    }

    public int getRewardCategory() {
        return rewardCategory;
    }
}
