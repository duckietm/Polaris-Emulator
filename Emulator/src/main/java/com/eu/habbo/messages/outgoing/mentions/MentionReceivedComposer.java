package com.eu.habbo.messages.outgoing.mentions;

import com.eu.habbo.habbohotel.mentions.HabboMention;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public class MentionReceivedComposer extends MessageComposer {
    private final HabboMention mention;

    public MentionReceivedComposer(HabboMention mention) {
        this.mention = mention;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.MentionReceivedComposer);
        this.response.appendInt(this.mention.id());
        this.response.appendInt(this.mention.senderUserId());
        this.response.appendString(this.mention.senderUsername());
        this.response.appendString(this.mention.senderFigure());
        this.response.appendInt(this.mention.roomId());
        this.response.appendString(this.mention.roomName());
        this.response.appendString(this.mention.message());
        this.response.appendInt(this.mention.mentionType());
        this.response.appendInt(this.mention.timestamp());
        return this.response;
    }
}
