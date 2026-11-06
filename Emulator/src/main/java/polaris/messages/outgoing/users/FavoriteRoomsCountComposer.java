package polaris.messages.outgoing.users;

import polaris.hotel.permissions.RankLimits;
import polaris.hotel.users.User;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class FavoriteRoomsCountComposer extends MessageComposer {
    private final User user;

    public FavoriteRoomsCountComposer(User user) {
        this.user = user;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.FavoriteRoomsCountComposer);
        this.response.appendInt(
                RankLimits.favouriteRooms(this.user.getUserInfo().getRank()));
        this.response.appendInt(this.user.getUserStats().getFavoriteRooms().size());
        for (int roomId : this.user.getUserStats().getFavoriteRooms()) {
            this.response.appendInt(roomId);
        }
        return this.response;
    }

    public User getUser() {
        return user;
    }
}
