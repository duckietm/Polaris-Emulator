package polaris.messages.outgoing.friends;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import polaris.hotel.messenger.MessengerBuddy;
import polaris.hotel.messenger.MessengerCategory;
import polaris.hotel.users.User;
import polaris.hotel.users.UserGender;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class UpdateFriendComposer extends MessageComposer {
    private Collection<MessengerBuddy> buddies;

    private User user;
    private int action;

    public UpdateFriendComposer(User user, MessengerBuddy buddy, Integer action) {
        this.user = user;
        this.buddies = Collections.singletonList(buddy);
        this.action = action;
    }

    public UpdateFriendComposer(User user, Collection<MessengerBuddy> buddies, Integer action) {
        this.user = user;
        this.buddies = buddies;
        this.action = action;
    }

    @Override
    protected ServerMessage composeInternal() {

        this.response.init(Outgoing.UpdateFriendComposer);
        if (this.user != null
                && !this.user.getUserInfo().getMessengerCategories().isEmpty()) {

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

        // The count is of what is written: a null buddy was skipped but counted, and the client lost the whole list.
        List<MessengerBuddy> present = buddies.stream().filter(Objects::nonNull).toList();
        this.response.appendInt(present.size()); // totalbuddies

        for (MessengerBuddy buddy : present) {

            if (buddy != null) {
                this.response.appendInt(this.action); // -1 = removed friendId / 0 = updated friend / 1 = added friend
                this.response.appendInt(buddy.getId());
                if (this.action == -1) {
                    continue;
                }
                this.response.appendString(buddy.getUsername());
                this.response.appendInt(buddy.getGender().equals(UserGender.M) ? 0 : 1);
                this.response.appendBoolean(buddy.isVisibleOnline());
                this.response.appendBoolean(buddy.isVisibleInRoom()); // In room
                this.response.appendString(buddy.getLook());
                this.response.appendInt(buddy.getCategoryId());
                this.response.appendString(buddy.getMotto());
                this.response.appendString(""); // Last seen as DATETIMESTRING
                this.response.appendString(""); // Realname or Facebookame as String
                this.response.appendBoolean(true); // Offline messaging.
                this.response.appendBoolean(false);
                this.response.appendBoolean(false);
                this.response.appendShort(buddy.getRelation());
            }
        }
        return this.response;
    }

    public Collection<MessengerBuddy> getBuddies() {
        return buddies;
    }

    public User getUser() {
        return user;
    }

    public int getAction() {
        return action;
    }
}
