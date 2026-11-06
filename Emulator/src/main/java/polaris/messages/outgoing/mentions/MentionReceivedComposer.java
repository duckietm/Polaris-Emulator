package polaris.messages.outgoing.mentions;

import polaris.hotel.mentions.UserMention;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class MentionReceivedComposer extends MessageComposer {
    private final UserMention mention;

    public MentionReceivedComposer(UserMention mention) {
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
