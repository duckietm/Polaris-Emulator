package polaris.messages.rcon;

import com.google.gson.Gson;
import jakarta.validation.constraints.Positive;
import polaris.Emulator;
import polaris.hotel.messenger.Messenger;
import polaris.hotel.users.User;
import polaris.hotel.users.UserInfo;
import polaris.hotel.users.UserManager;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;
import polaris.messages.outgoing.friends.FriendRequestComposer;

public class FriendRequest extends RCONMessage<FriendRequest.JSON> {
    public FriendRequest() {
        super(FriendRequest.JSON.class);
    }

    @Override
    public void handle(Gson gson, JSON json) {
        if (json.user_id == json.target_id) {
            this.status = RCONMessage.STATUS_ERROR;
            this.message = "cannot friend self";
            return;
        }

        if (!RconUserLookup.userExists(json.user_id) || !RconUserLookup.userExists(json.target_id)) {
            this.status = RCONMessage.USER_NOT_FOUND;
            this.message = "user not found";
            return;
        }

        if (!Messenger.friendRequested(json.target_id, json.user_id)) {
            Messenger.makeFriendRequest(json.user_id, json.target_id);

            User target = Emulator.getGameEnvironment().getUserManager().getUser(json.target_id);
            if (target != null) {
                User from = Emulator.getGameEnvironment().getUserManager().getUser(json.user_id);

                if (from != null) {
                    target.getClient().sendResponse(new FriendRequestComposer(from));
                } else {
                    final UserInfo info = UserManager.getOfflineUserInfo(json.user_id);

                    if (info != null) {
                        target.getClient().sendResponse(new MessageComposer() {
                            @Override
                            protected ServerMessage composeInternal() {
                                this.response.init(Outgoing.FriendRequestComposer);
                                this.response.appendInt(info.getId());
                                this.response.appendString(info.getUsername());
                                this.response.appendString(info.getLook());
                                return this.response;
                            }
                        });
                    }
                }
            }
        }
    }

    static class JSON {

        @Positive(message = "invalid user")
        public int user_id;

        @Positive(message = "invalid target")
        public int target_id;
    }
}
