package polaris.hotel.items.interactions.wired.triggers;

import java.sql.ResultSet;
import java.sql.SQLException;
import polaris.hotel.items.Item;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.users.User;
import polaris.hotel.users.UserItem;
import polaris.hotel.wired.WiredTriggerType;
import polaris.hotel.wired.core.WiredEvent;

/**
 * Trigger furni {@code wf_trg_username_as_trigger} ("say your username").
 * <p>
 * Unlike {@link WiredTriggerUserSaysKeyword}, where the owner configures a single static keyword
 * that every user must say, this trigger fires when a user says <em>their own username</em>. The
 * keyword is therefore dynamic per-actor: it is resolved at match time from the speaking user's
 * username rather than from an owner-supplied string.
 * </p>
 * <p>
 * It extends {@link WiredTriggerUserSaysKeyword} for its serialization and persistence, but reports
 * its own {@link WiredTriggerType#USERNAME_AS_TRIGGER} so the client draws a dialog without the
 * keyword and match-mode controls this box never reads, and listens on its own event. Only
 * {@link #matches} compares the chat text against the actor's username; the owner-only gate is
 * inherited via {@link #isOwnerOnly()}.
 * </p>
 */
public class WiredTriggerUsernameAsTrigger extends WiredTriggerUserSaysKeyword {

    public WiredTriggerUsernameAsTrigger(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredTriggerUsernameAsTrigger(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public WiredTriggerType getType() {
        return WiredTriggerType.USERNAME_AS_TRIGGER;
    }

    @Override
    public boolean matches(UserItem triggerItem, WiredEvent event) {
        String text = event.getText().orElse(null);
        RoomUnit roomUnit = event.getActor().orElse(null);
        Room room = event.getRoom();

        if (text == null || roomUnit == null || room == null) {
            return false;
        }

        User user = room.getUser(roomUnit);
        if (user == null) {
            return false;
        }

        if (this.isOwnerOnly() && room.getOwnerId() != user.getUserInfo().getId()) {
            return false;
        }

        String username = user.getUserInfo().getUsername();
        if (username == null || username.isEmpty()) {
            return false;
        }

        return text.toLowerCase().trim().contains(username.toLowerCase().trim());
    }
}
