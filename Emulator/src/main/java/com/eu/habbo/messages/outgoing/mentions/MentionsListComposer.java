package com.eu.habbo.messages.outgoing.mentions;

import com.eu.habbo.habbohotel.mentions.HabboMention;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;
import java.util.List;

public class MentionsListComposer extends MessageComposer {
    private final List<HabboMention> mentions;
    private final boolean hasMore;

    public MentionsListComposer(List<HabboMention> mentions) {
        this(mentions, false);
    }

    public MentionsListComposer(List<HabboMention> mentions, boolean hasMore) {
        this.mentions = mentions;
        this.hasMore = hasMore;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.MentionsListComposer);
        appendMentions(this.response, this.mentions, this.hasMore);

        return this.response;
    }

    /** Count, the mentions, then whether older ones remain: the body shared with the paging packet. */
    static void appendMentions(ServerMessage response, List<HabboMention> mentions, boolean hasMore) {
        response.appendInt(mentions.size());

        for (HabboMention mention : mentions) {
            response.appendInt(mention.getId());
            response.appendInt(mention.getSenderUserId());
            response.appendString(mention.getSenderUsername());
            response.appendString(mention.getSenderFigure());
            response.appendInt(mention.getRoomId());
            response.appendString(mention.getRoomName());
            response.appendString(mention.getMessage());
            response.appendInt(mention.getMentionType());
            response.appendInt(mention.getTimestamp());
            response.appendBoolean(mention.isRead());
        }

        response.appendBoolean(hasMore);
    }
}
