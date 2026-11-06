package polaris.messages.outgoing.friends;

import java.util.List;
import polaris.hotel.messenger.MessengerCategory;
import polaris.hotel.permissions.RankLimits;
import polaris.hotel.users.User;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class MessengerInitComposer extends MessageComposer {
    private final User user;

    public MessengerInitComposer(User user) {
        this.user = user;
    }

    @Override
    protected ServerMessage composeInternal() {

        this.response.init(Outgoing.MessengerInitComposer);
        if (this.user.hasPermission("acc_infinite_friends")) {
            this.response.appendInt(Integer.MAX_VALUE);
            this.response.appendInt(1337);
            this.response.appendInt(Integer.MAX_VALUE);
        } else {
            // AIR MessengerInitParser: the user's own limit (the one the server enforces), the normal limit and the
            // Habbo Club limit. The first was the normal limit, so a Club member's client stopped at 200 of 500.
            this.response.appendInt(RankLimits.friends(this.user));
            this.response.appendInt(RankLimits.friendsShown(this.user, false));
            this.response.appendInt(RankLimits.friendsShown(this.user, true));
        }
        if (!this.user.getUserInfo().getMessengerCategories().isEmpty()) {

            List<MessengerCategory> messengerCategories =
                    this.user.getUserInfo().getMessengerCategories();
            this.response.appendInt(messengerCategories.size());

            for (MessengerCategory mc : messengerCategories) {
                this.response.appendInt(mc.getId());
                this.response.appendString(mc.getName());
            }
        } else {
            this.response.appendInt(0);
        }
        return this.response;
    }

    public User getUser() {
        return user;
    }
}
