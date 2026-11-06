package polaris.messages.outgoing.rooms.pets;

import polaris.hotel.rooms.BuildersClubItemIds;
import polaris.hotel.users.UserItem;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class PetPackageComposer extends MessageComposer {
    private final UserItem item;

    public PetPackageComposer(UserItem item) {
        this.item = item;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.LeprechaunStarterBundleComposer);
        this.response.appendInt(BuildersClubItemIds.toWire(this.item.getId()));
        return this.response;
    }

    public UserItem getItem() {
        return item;
    }
}
