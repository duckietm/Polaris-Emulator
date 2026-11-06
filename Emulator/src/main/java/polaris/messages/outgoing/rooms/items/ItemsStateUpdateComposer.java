package polaris.messages.outgoing.rooms.items;

import java.util.Collection;
import polaris.hotel.rooms.BuildersClubItemIds;
import polaris.hotel.users.UserItem;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

/**
 * AIR 13 {@code ItemsStateUpdate} (official id 3697, parser
 * {@code class_3328}): {@code int count [ int id str itemData ]}, the batched
 * form of the single wall-item state update.
 */
public class ItemsStateUpdateComposer extends MessageComposer {

    private final Collection<UserItem> items;

    public ItemsStateUpdateComposer(Collection<UserItem> items) {
        this.items = items;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.ItemsStateUpdateComposer);

        this.response.appendInt(this.items.size());
        for (UserItem item : this.items) {
            this.response.appendInt(BuildersClubItemIds.toWire(item.getId()));
            this.response.appendString(item.getExtradata());
        }

        return this.response;
    }

    public Collection<UserItem> getItems() {
        return this.items;
    }
}
