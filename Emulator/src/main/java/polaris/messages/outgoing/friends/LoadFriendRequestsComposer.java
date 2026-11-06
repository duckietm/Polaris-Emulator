package polaris.messages.outgoing.friends;

import polaris.hotel.messenger.FriendRequest;
import polaris.hotel.users.User;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class LoadFriendRequestsComposer extends MessageComposer {
    private final User user;

    public LoadFriendRequestsComposer(User user) {
        this.user = user;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.LoadFriendRequestsComposer);

        synchronized (this.user.getMessenger().getFriendRequests()) {
            this.response.appendInt(this.user.getMessenger().getFriendRequests().size());
            this.response.appendInt(this.user.getMessenger().getFriendRequests().size());

            for (FriendRequest friendRequest : this.user.getMessenger().getFriendRequests()) {
                this.response.appendInt(friendRequest.getId());
                this.response.appendString(friendRequest.getUsername());
                this.response.appendString(friendRequest.getLook());
            }
        }

        return this.response;
    }

    public User getUser() {
        return user;
    }
}
