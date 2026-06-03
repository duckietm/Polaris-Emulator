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
        this.response.appendInt(this.mention.getId());
        this.response.appendInt(this.mention.getSenderUserId());
        this.response.appendString(this.mention.getSenderUsername());
        this.response.appendString(this.mention.getSenderFigure());
        this.response.appendInt(this.mention.getRoomId());
        this.response.appendString(this.mention.getRoomName());
        this.response.appendString(this.mention.getMessage());
        this.response.appendInt(this.mention.getMentionType());
        this.response.appendInt(this.mention.getTimestamp());
        return this.response;
    }
}
