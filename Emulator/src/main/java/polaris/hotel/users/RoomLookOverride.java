package polaris.hotel.users;

import polaris.messages.outgoing.users.UpdateUserLookComposer;
import polaris.plugin.EventHandler;
import polaris.plugin.events.users.UserDisconnectEvent;
import polaris.plugin.events.users.UserExitRoomEvent;
import polaris.plugin.events.users.UserSavedLookEvent;

/**
 * A look a room puts on a user (the Give Look wired box) for as long as they stay in it. The user's own
 * look is kept aside and given back when they leave the room or disconnect; a look they save themselves
 * meanwhile is theirs, and nothing is put back over it.
 */
public final class RoomLookOverride {
    static final String ORIGINAL_LOOK_KEY = "room_look_override_original";

    private RoomLookOverride() {}

    /** Dresses the user in {@code look}, keeping their own look aside the first time. */
    public static void apply(User user, String look) {
        user.getUserStats()
                .cache
                .putIfAbsent(ORIGINAL_LOOK_KEY, user.getUserInfo().getLook());
        user.getUserInfo().setLook(look);
    }

    /** Gives the user back the look they had before a room dressed them; false when there was none. */
    public static boolean restore(User user) {
        if (user == null || user.getUserStats() == null) return false;

        Object original = user.getUserStats().cache.remove(ORIGINAL_LOOK_KEY);
        if (!(original instanceof String look)) return false;

        user.getUserInfo().setLook(look);
        if (user.getClient() != null) {
            user.getClient().sendResponse(new UpdateUserLookComposer(user));
        }
        return true;
    }

    @EventHandler
    public static void onUserExitRoomEvent(UserExitRoomEvent event) {
        restore(event.user);
    }

    @EventHandler
    public static void onUserDisconnectEvent(UserDisconnectEvent event) {
        restore(event.user);
    }

    @EventHandler
    public static void onUserSavedLookEvent(UserSavedLookEvent event) {
        if (event.user != null && event.user.getUserStats() != null) {
            event.user.getUserStats().cache.remove(ORIGINAL_LOOK_KEY);
        }
    }
}
