package com.eu.habbo.messages.incoming.mentions;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.mentions.MentionManager;
import com.eu.habbo.habbohotel.mentions.MentionPage;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.mentions.MentionsListComposer;

public class RequestMentionsEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        if (this.client == null || this.client.getHabbo() == null) return;

        int userId = this.client.getHabbo().getHabboInfo().getId();

        MentionManager manager = Emulator.getGameEnvironment().getMentionManager();

        if (!manager.tryAcquireRequestList(userId)) {
            return;
        }

        MentionPage page = manager.getMentionsPage(userId, manager.pageSize(), 0);
        this.client.sendResponse(new MentionsListComposer(page.mentions(), page.hasMore()));
    }
}
