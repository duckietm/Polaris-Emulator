package polaris.messages.outgoing.rooms.items;

import polaris.hotel.rooms.BuildersClubItemIds;
import polaris.hotel.users.UserItem;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class PresentItemOpenedComposer extends MessageComposer {
    private final UserItem item;
    private final String text;
    private final boolean unknown;

    public PresentItemOpenedComposer(UserItem item, String text, boolean unknown) {
        this.item = item;
        this.text = text;
        this.unknown = unknown;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.PresentItemOpenedComposer);
        this.response.appendString(this.item.getBaseItem().getType().code.toLowerCase());
        this.response.appendInt(this.item.getBaseItem().getSpriteId());
        this.response.appendString(this.item.getBaseItem().getName());
        this.response.appendInt(BuildersClubItemIds.toWire(this.item.getId()));
        this.response.appendString(this.item.getBaseItem().getType().code.toLowerCase());
        this.response.appendBoolean(this.unknown);
        this.response.appendString(this.text);
        return this.response;
    }

    public UserItem getItem() {
        return item;
    }

    public String getText() {
        return text;
    }

    public boolean isUnknown() {
        return unknown;
    }
}
