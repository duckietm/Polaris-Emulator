package com.eu.habbo.messages.outgoing.mentions;

import com.eu.habbo.habbohotel.mentions.MentionPage;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

/** The page after the one the client already has; it is appended to the list, not replacing it. */
public class MentionsPageComposer extends MessageComposer {
    private final MentionPage page;

    public MentionsPageComposer(MentionPage page) {
        this.page = page;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.MentionsPageComposer);
        MentionsListComposer.appendMentions(this.response, this.page.mentions(), this.page.hasMore());

        return this.response;
    }
}
