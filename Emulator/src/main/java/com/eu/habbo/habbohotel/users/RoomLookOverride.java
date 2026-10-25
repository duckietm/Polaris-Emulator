package com.eu.habbo.habbohotel.users;

import com.eu.habbo.messages.outgoing.users.UpdateUserLookComposer;
import com.eu.habbo.plugin.EventHandler;
import com.eu.habbo.plugin.events.users.UserDisconnectEvent;
import com.eu.habbo.plugin.events.users.UserExitRoomEvent;
import com.eu.habbo.plugin.events.users.UserSavedLookEvent;

/**
 * A look a room puts on a user (the Give Look wired box) for as long as they stay in it. The user's own
 * look is kept aside and given back when they leave the room or disconnect; a look they save themselves
 * meanwhile is theirs, and nothing is put back over it.
 */
public final class RoomLookOverride {
    static final String ORIGINAL_LOOK_KEY = "room_look_override_original";

    private RoomLookOverride() {}

    /** Dresses the user in {@code look}, keeping their own look aside the first time. */
    public static void apply(Habbo habbo, String look) {
        habbo.getHabboStats()
                .cache
                .putIfAbsent(ORIGINAL_LOOK_KEY, habbo.getHabboInfo().getLook());
        habbo.getHabboInfo().setLook(look);
    }

    /** Gives the user back the look they had before a room dressed them; false when there was none. */
    public static boolean restore(Habbo habbo) {
        if (habbo == null || habbo.getHabboStats() == null) return false;

        Object original = habbo.getHabboStats().cache.remove(ORIGINAL_LOOK_KEY);
        if (!(original instanceof String look)) return false;

        habbo.getHabboInfo().setLook(look);
        if (habbo.getClient() != null) {
            habbo.getClient().sendResponse(new UpdateUserLookComposer(habbo));
        }
        return true;
    }

    @EventHandler
    public static void onUserExitRoomEvent(UserExitRoomEvent event) {
        restore(event.habbo);
    }

    @EventHandler
    public static void onUserDisconnectEvent(UserDisconnectEvent event) {
        restore(event.habbo);
    }

    @EventHandler
    public static void onUserSavedLookEvent(UserSavedLookEvent event) {
        if (event.habbo != null && event.habbo.getHabboStats() != null) {
            event.habbo.getHabboStats().cache.remove(ORIGINAL_LOOK_KEY);
        }
    }
}
