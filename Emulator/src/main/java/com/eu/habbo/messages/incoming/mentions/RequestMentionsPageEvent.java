package com.eu.habbo.messages.incoming.mentions;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.mentions.MentionManager;
import com.eu.habbo.habbohotel.mentions.MentionPage;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.mentions.MentionsPageComposer;

/** The next page of mentions: those older than the id the client already has at the bottom. */
public class RequestMentionsPageEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        if (this.client == null || this.client.getHabbo() == null) return;

        int beforeId = this.packet.readInt();

        if (beforeId <= 0) {
            return;
        }

        int userId = this.client.getHabbo().getHabboInfo().getId();
        MentionManager manager = Emulator.getGameEnvironment().getMentionManager();

        if (!manager.tryAcquireRequestPage(userId)) {
            return;
        }

        MentionPage page = manager.getMentionsPage(userId, manager.pageSize(), beforeId);
        this.client.sendResponse(new MentionsPageComposer(page));
    }
}
