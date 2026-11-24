package polaris.messages.outgoing.rooms.pets;

import polaris.hotel.users.UserItem;
import polaris.messages.ServerMessage;

/** {@link PetPackageComposer} followed by the figure of the pet in the package, which the client shows in the name window. */
public class PetPackageWithFigureComposer extends PetPackageComposer {
    private final String figure;

    PetPackageWithFigureComposer(UserItem item, String figure) {
        super(item);
        this.figure = figure;
    }

    @Override
    protected ServerMessage composeInternal() {
        super.composeInternal();
        this.response.appendString(this.figure);
        return this.response;
    }
}
