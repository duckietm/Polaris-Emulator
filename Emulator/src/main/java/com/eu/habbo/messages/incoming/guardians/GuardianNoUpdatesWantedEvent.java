package com.eu.habbo.messages.incoming.guardians;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.guides.GuardianTicket;
import com.eu.habbo.habbohotel.guides.GuardianVote;
import com.eu.habbo.messages.incoming.MessageHandler;

/**
 * Official {@code ChatReviewGuideDetachedMessageComposer}: the guardian closed the
 * chat-review results window ({@code GuideSessionController.onGuardianChatReviewResultsEvent})
 * and no longer wants updates for that ticket, so stop pushing vote updates to them.
 */
public class GuardianNoUpdatesWantedEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        GuardianTicket ticket =
                Emulator.getGameEnvironment().getGuideManager().getTicketForGuardian(this.client.getHabbo());

        if (ticket == null) {
            return;
        }

        GuardianVote vote = ticket.getVoteForGuardian(this.client.getHabbo());

        if (vote != null) {
            vote.ignore();
        }
    }
}
