package polaris.messages.outgoing.rooms.items;

import polaris.hotel.rooms.BuildersClubItemIds;
import polaris.hotel.users.UserItem;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class ItemStateComposer extends MessageComposer {
    private final UserItem item;

    public ItemStateComposer(UserItem item) {
        this.item = item;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.ItemStateComposer);
        this.response.appendInt(BuildersClubItemIds.toWire(this.item.getId()));
        try {
            int state = Integer.parseInt(this.item.getExtradata());
            this.response.appendInt(state);
        } catch (Exception e) {
            this.response.appendInt(0);
        }

        return this.response;
    }

    public UserItem getItem() {
        return item;
    }
}
