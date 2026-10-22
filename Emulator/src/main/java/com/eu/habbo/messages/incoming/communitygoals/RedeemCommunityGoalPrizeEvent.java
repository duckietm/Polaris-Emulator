package com.eu.habbo.messages.incoming.communitygoals;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.communitygoals.CommunityGoalManager;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.hotelview.CommunityGoalEarnedPrizesComposer;

/**
 * AIR {@code RedeemCommunityGoalPrize} (90, {@code int communityGoalId}): hands out
 * the earned prize of that goal once, then answers with the remaining
 * {@code CommunityGoalEarnedPrizes} (3319) so the redeem button disappears.
 */
public class RedeemCommunityGoalPrizeEvent extends MessageHandler {

    @Override
    public int getRatelimit() {
        return 1000;
    }

    @Override
    public void handle() {
        Habbo habbo = this.client.getHabbo();

        if (habbo == null) return;

        int communityGoalId = this.packet.readInt();
        CommunityGoalManager manager = Emulator.getGameEnvironment().getCommunityGoalManager();

        manager.redeemPrize(habbo, communityGoalId);

        this.client.sendResponse(new CommunityGoalEarnedPrizesComposer(
                manager.getEarnedPrizes(habbo.getHabboInfo().getId())));
    }
}
