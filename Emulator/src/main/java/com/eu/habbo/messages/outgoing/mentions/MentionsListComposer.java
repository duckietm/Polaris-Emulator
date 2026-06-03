package com.eu.habbo.messages.outgoing.mentions;

import com.eu.habbo.habbohotel.mentions.HabboMention;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

import java.util.List;

public class MentionsListComposer extends MessageComposer {
    private final List<HabboMention> mentions;

    public MentionsListComposer(List<HabboMention> mentions) {
        this.mentions = mentions;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.MentionsListComposer);
        this.response.appendInt(this.mentions.size());

        for (HabboMention mention : this.mentions) {
            this.response.appendInt(mention.getId());
            this.response.appendInt(mention.getSenderUserId());
            this.response.appendString(mention.getSenderUsername());
            this.response.appendString(mention.getSenderFigure());
            this.response.appendInt(mention.getRoomId());
            this.response.appendString(mention.getRoomName());
            this.response.appendString(mention.getMessage());
            this.response.appendInt(mention.getMentionType());
            this.response.appendInt(mention.getTimestamp());
            this.response.appendBoolean(mention.isRead());
        }

        return this.response;
    }
}
