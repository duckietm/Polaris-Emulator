package polaris.messages.outgoing.rooms.items;

import java.util.Set;
import polaris.hotel.rooms.BuildersClubItemIds;
import polaris.hotel.users.UserItem;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class ItemsDataUpdateComposer extends MessageComposer {
    private final Set<UserItem> items;

    public ItemsDataUpdateComposer(Set<UserItem> items) {
        this.items = items;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.ItemsDataUpdateComposer);
        this.response.appendInt(this.items.size());

        for (UserItem item : this.items) {
            this.response.appendInt(BuildersClubItemIds.toWire(item.getId()));
            item.serializeExtradata(this.response);
        }

        return this.response;
    }

    public Set<UserItem> getItems() {
        return items;
    }
}
