package com.eu.habbo.messages.incoming.mentions;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.mentions.HabboMention;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.mentions.MentionsListComposer;

import java.util.List;

public class RequestMentionsEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int userId = this.client.getHabbo().getHabboInfo().getId();
        int limit = Emulator.getConfig().getInt("mentions.store.limit", 50);

        List<HabboMention> mentions = Emulator.getGameEnvironment().getMentionManager().getMentions(userId, limit);
        this.client.sendResponse(new MentionsListComposer(mentions));
    }
}
