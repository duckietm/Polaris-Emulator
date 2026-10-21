package com.eu.habbo.messages.incoming.guardians;

import com.eu.habbo.Emulator;
import com.eu.habbo.messages.incoming.MessageHandler;

/**
 * Official {@code ChatReviewGuideDetachedMessageComposer}: the guardian closed one of the
 * chat-review windows (vote, waiting for voters, waiting for results or the results) and no longer
 * wants updates for that ticket. Their vote, if any, still counts, and they are free for the next.
 */
public class GuardianNoUpdatesWantedEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        Emulator.getGameEnvironment().getGuideManager().detachGuardian(this.client.getHabbo());
    }
}
