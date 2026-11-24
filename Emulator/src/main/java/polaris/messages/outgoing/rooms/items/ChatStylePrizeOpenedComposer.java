package polaris.messages.outgoing.rooms.items;

import polaris.hotel.catalog.RecyclerPrize;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

/**
 * The "present opened" reply of a Furni-Matic box that held a chat style: the same message as for a furni, with the
 * product type {@code chat_style} and the style's id as the class id. Nothing was placed and no furni was given, so the
 * placed item is 0 with no type.
 */
public class ChatStylePrizeOpenedComposer extends MessageComposer {
    private final int chatStyleId;

    public ChatStylePrizeOpenedComposer(int chatStyleId) {
        this.chatStyleId = chatStyleId;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.PresentItemOpenedComposer);
        this.response.appendString(RecyclerPrize.CHAT_STYLE_TYPE);
        this.response.appendInt(this.chatStyleId);
        this.response.appendString("chat_bubble_" + this.chatStyleId);
        this.response.appendInt(0);
        this.response.appendString("");
        this.response.appendBoolean(false);
        this.response.appendString("");
        return this.response;
    }

    public int getChatStyleId() {
        return chatStyleId;
    }
}
