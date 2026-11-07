package polaris.hotel.items.interactions;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import polaris.Emulator;
import polaris.hotel.items.Item;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.rooms.RoomUnitType;
import polaris.hotel.users.User;
import polaris.messages.outgoing.users.InClientLinkComposer;
import polaris.threading.runnables.teleport.TeleportEntryRules;

/**
 * A furni that sends the user who steps on it to a room (interaction type "step_link"), as Habbo's
 * {@code room_noob_exit} does: a staff tile that sends a new user on. Each placed furni keeps its own room id, as the
 * custom value {@value #LINK_KEY}; staff set it in the infostand ("Room ID"), which saves it through the object data
 * packet. The user's client is sent {@code navigator/goto/<room id>}, so an exit only ever leads to a room.
 *
 * <p>It goes by the same rules as the arrow ({@code room_wl15_teleblock}): only a walk that ends on the tile fires it,
 * not walking over it on the way somewhere else nor being placed on it (entering a room on it); not while a teleport
 * runs for the user or while they ride a pet; and only to a room that exists and that they may enter (not banned
 * there, and not full unless they may enter full rooms). Bots and pets do not use it, and clicking it does nothing.
 */
public class InteractionStepLink extends InteractionCustomValues {
    /** The custom value holding the room id (the key AIR's internal link furni use for their link). */
    public static final String LINK_KEY = "internalLink";

    private static final Map<String, String> DEFAULTS = Map.of(LINK_KEY, "");

    /** The in-client link that takes a user to a room. */
    static final String ROOM_LINK_PREFIX = "navigator/goto/";

    /** A room id, or a room link as saved before the exits held only the id. */
    private static final Pattern ROOM_ID = Pattern.compile("(?:navigator/goto/)?([1-9][0-9]{0,9})");

    public InteractionStepLink(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem, DEFAULTS);

        // A room link stored as the whole extra data, or saved as a link before, becomes the room id.
        String stored = roomIdOf(this.values.get(LINK_KEY));
        if (stored == null) stored = roomIdOf(set.getString("extra_data"));
        this.values.put(LINK_KEY, stored == null ? "" : stored);
    }

    public InteractionStepLink(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells, DEFAULTS);
    }

    /** The link this exit sends a user to: its room, or null when it has no room. */
    public String getLink() {
        String roomId = roomIdOf(this.values.get(LINK_KEY));

        return roomId == null ? null : ROOM_LINK_PREFIX + roomId;
    }

    /** The room id in a value (a room id, or navigator/goto/ followed by one), or null when it holds none. */
    static String roomIdOf(String value) {
        if (value == null) return null;

        Matcher matcher = ROOM_ID.matcher(value.trim());

        if (!matcher.matches()) return null;

        try {
            return Integer.toString(Integer.parseInt(matcher.group(1)));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** No room: an exit holding it sends nobody anywhere. */
    static final String NO_ROOM = "0";

    /**
     * A save keeps a room id, or clears it with an empty value or 0 (the exit then does nothing); anything else keeps
     * the stored room.
     */
    @Override
    public String sanitizeValue(String key, String value) {
        if (value == null || value.isBlank() || value.trim().equals(NO_ROOM)) return "";

        return roomIdOf(value);
    }

    /** Only staff may point an exit somewhere: it sends every user who steps on it there. */
    public static boolean mayEdit(User user) {
        return user != null && user.hasPermission(polaris.hotel.permissions.Permission.ACC_ANYROOMOWNER);
    }

    @Override
    public boolean canWalkOn(RoomUnit roomUnit, Room room, Object[] objects) {
        return true;
    }

    @Override
    public boolean isWalkable() {
        return true;
    }

    @Override
    public boolean allowWiredResetState() {
        return false;
    }

    @Override
    public void onWalkOn(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {
        super.onWalkOn(roomUnit, room, objects);

        if (room == null || roomUnit == null || roomUnit.getRoomUnitType() != RoomUnitType.USER) return;

        if (!InteractionArrow.isWalkStep(objects) || !InteractionArrow.walkEndsOn(roomUnit, this)) return;

        String link = this.getLink();
        User user = room.getUser(roomUnit);

        if (link == null || user == null || user.getClient() == null || !mayTravel(user)) return;

        Room target = Emulator.getGameEnvironment().getRoomManager().loadRoom(Integer.parseInt(this.roomId()));

        if (target == null || !TeleportEntryRules.mayEnter(user, target)) return;

        user.getClient().sendResponse(new InClientLinkComposer(link));
    }

    /** The stored room id; only called once {@link #getLink()} found one. */
    private String roomId() {
        return roomIdOf(this.values.get(LINK_KEY));
    }

    /** As for a teleport or an arrow: not while riding a pet. */
    static boolean mayTravel(User user) {
        return user.getRoomUnit() != null
                && user.getUserInfo() != null
                && user.getUserInfo().getRiding() == null;
    }
}
