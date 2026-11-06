package polaris.messages.outgoing.mentions;

import java.util.List;
import polaris.hotel.mentions.UserMention;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class MentionsListComposer extends MessageComposer {
    private final List<UserMention> mentions;
    private final boolean hasMore;

    public MentionsListComposer(List<UserMention> mentions) {
        this(mentions, false);
    }

    public MentionsListComposer(List<UserMention> mentions, boolean hasMore) {
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
    static void appendMentions(ServerMessage response, List<UserMention> mentions, boolean hasMore) {
        response.appendInt(mentions.size());

        for (UserMention mention : mentions) {
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
