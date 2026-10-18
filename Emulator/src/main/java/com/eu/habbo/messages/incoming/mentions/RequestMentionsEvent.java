package com.eu.habbo.messages.incoming.mentions;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.mentions.HabboMention;
import com.eu.habbo.habbohotel.mentions.MentionManager;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.mentions.MentionsListComposer;

import java.util.List;

public class RequestMentionsEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        if (this.client == null || this.client.getHabbo() == null) return;

        int userId = this.client.getHabbo().getHabboInfo().getId();

        MentionManager manager = Emulator.getGameEnvironment().getMentionManager();

        if (!manager.tryAcquireRequestList(userId)) {
            return;
        }

        int limit = Emulator.getConfig().getInt("mentions.store.limit", 50);

        List<HabboMention> mentions = manager.getMentions(userId, limit);
        this.client.sendResponse(new MentionsListComposer(mentions));
    }
}
