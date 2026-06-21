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
            this.response.appendInt(mention.id());
            this.response.appendInt(mention.senderUserId());
            this.response.appendString(mention.senderUsername());
            this.response.appendString(mention.senderFigure());
            this.response.appendInt(mention.roomId());
            this.response.appendString(mention.roomName());
            this.response.appendString(mention.message());
            this.response.appendInt(mention.mentionType());
            this.response.appendInt(mention.timestamp());
            this.response.appendBoolean(mention.read());
        }

        return this.response;
    }
}
