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

    /**
     * The packet for a package: with the figure of the pet it holds when its state names one (the official client
     * reads it after the id when it is there), the plain one otherwise.
     */
    public static PetPackageComposer of(UserItem item, String figure) {
        return figure == null || figure.isEmpty()
                ? new PetPackageComposer(item)
                : new PetPackageWithFigureComposer(item, figure);
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
