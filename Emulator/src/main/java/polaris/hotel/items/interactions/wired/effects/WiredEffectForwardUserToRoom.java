package polaris.hotel.items.interactions.wired.effects;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import polaris.WiredPlatform;
import polaris.hotel.GameEnvironment;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.items.Item;
import polaris.hotel.items.interactions.InteractionCustomValues;
import polaris.hotel.items.interactions.InteractionTeleport;
import polaris.hotel.items.interactions.InteractionWiredEffect;
import polaris.hotel.items.interactions.InteractionWiredTrigger;
import polaris.hotel.items.interactions.wired.WiredInputGuard;
import polaris.hotel.items.interactions.wired.WiredSaveRefusedException;
import polaris.hotel.items.interactions.wired.WiredSettings;
import polaris.hotel.permissions.Permission;
import polaris.hotel.rooms.PendingRoomEntry;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomForwardGlow;
import polaris.hotel.rooms.RoomState;
import polaris.hotel.rooms.RoomTile;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.rooms.RoomUserRotation;
import polaris.hotel.users.User;
import polaris.hotel.users.UserItem;
import polaris.hotel.wired.WiredEffectType;
import polaris.hotel.wired.core.WiredContext;
import polaris.hotel.wired.core.WiredDelay;
import polaris.hotel.wired.core.WiredManager;
import polaris.hotel.wired.core.WiredSourceUtil;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.rooms.ForwardToRoomComposer;
import polaris.messages.outgoing.rooms.users.RoomUserStatusComposer;
import polaris.threading.ThreadPooling;
import polaris.threading.runnables.RoomUnitTeleport;

/**
 * Habbo's "teleport to room" ({@code wf_act_teleport_to_room}, also {@code wf_act_forward_user_to_room}
 * and {@code wf_act_tele_room}): sends the selected users to another room. The room is the one a
 * furni of the furni source leads to - a room link names it, a room linker or a teleporter leads to
 * wherever its pair stands and they arrive on that pair - and otherwise the typed room id.
 *
 * <p>Users are only forwarded: their client asks to enter like any navigator visit, so the doorbell,
 * the password, bans and the room's own limits still decide. Each user is forwarded at most once per
 * {@link #FORWARD_INTERVAL_MS}.
 *
 * <p>Int params {@code [user source, furni source]}; string param the room id, which may be empty
 * when furni are picked; stuff ids the picked room linkers, room links or teleporters.
 */
public class WiredEffectForwardUserToRoom extends InteractionWiredEffect {
    public static final WiredEffectType type = WiredEffectType.TELEPORT_TO_ROOM;

    static final String ROOM_LINK_KEY = "internalLink";
    static final long FORWARD_INTERVAL_MS = 2000L;
    static final long FORWARD_DELAY_MS = 950L;
    static final long GLOW_END_IF_STAYED_MS = 3000L;
    static final int MAX_FORWARDS_PER_RUN = 50;
    static final int MAX_FURNI_CHECKED = 20;
    static final long PAIR_LOOKUP_INTERVAL_MS = 1000L;
    static final long PAIR_CACHE_MS = 5000L;
    private static final int MAX_ROOM_ID_DIGITS = 10;

    private final List<UserItem> items = new ArrayList<>();
    private String roomIdText = "";
    private int userSource = WiredSourceUtil.SOURCE_TRIGGER;
    private int furniSource = WiredSourceUtil.SOURCE_SELECTED;
    private int cachedPairTeleportId;
    private int cachedPairRoomId;
    private int cachedPairItemId;
    private long cachedPairAt;
    private long lastPairLookupAt;

    public WiredEffectForwardUserToRoom(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectForwardUserToRoom(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        List<UserItem> picked = this.pickedItems(room);

        message.appendBoolean(false);
        message.appendInt(WiredManager.MAXIMUM_FURNI_SELECTION);
        message.appendInt(picked.size());
        for (UserItem item : picked) {
            message.appendInt(item.getId());
        }
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString(this.roomIdText);
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(type.code);
        message.appendInt(this.getDelay());

        if (this.requiresTriggeringUser()) {
            List<Integer> invalidTriggers = new ArrayList<>();
            for (InteractionWiredTrigger object : room.getRoomSpecialTypes().getTriggers(this.getX(), this.getY())) {
                if (!object.isTriggeredByRoomUnit()) {
                    invalidTriggers.add(object.getBaseItem().getSpriteId());
                }
            }
            message.appendInt(invalidTriggers.size());
            for (Integer i : invalidTriggers) {
                message.appendInt(i);
            }
        } else {
            message.appendInt(0);
        }
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) {
        String text =
                (settings.getStringParam() != null) ? settings.getStringParam().trim() : "";
        if (!text.isEmpty() && parseRoomId(text) <= 0) {
            return false;
        }

        int[] params = settings.getIntParams();
        // Official: no int params; the user and furni sources are the official source slots. Our
        // room id (text) and teleporter picks stay.
        int users = WiredMovementPayloadGuard.userSource(settings.userSource(0, WiredSourceUtil.SOURCE_TRIGGER));
        int furni = WiredMovementPayloadGuard.furniSource(settings.furniSource(0, WiredSourceUtil.SOURCE_SELECTED));

        int[] furniIds = settings.getFurniIds();
        if (furniIds.length > WiredManager.MAXIMUM_FURNI_SELECTION) {
            return false;
        }

        List<UserItem> picked = new ArrayList<>();
        if (furni == WiredSourceUtil.SOURCE_SELECTED && furniIds.length > 0) {
            Room room = this.getRoom();
            if (room == null) {
                return false;
            }
            for (int itemId : furniIds) {
                UserItem item = room.getUserItem(itemId);
                if (item == null) {
                    return false;
                }
                // Room linkers are teleporters, so they pass here with plain teleporters and room links.
                // Any other pick refuses the save with the room-linker text, as on the official server.
                if (!isRoomLink(item) && !(item instanceof InteractionTeleport)) {
                    throw new WiredSaveRefusedException("wiredfurni.error.require_room_linker");
                }
                if (!picked.contains(item)) {
                    picked.add(item);
                }
            }
        }

        if (text.isEmpty() && furni == WiredSourceUtil.SOURCE_SELECTED && picked.isEmpty()) {
            return false;
        }

        int delay = settings.getDelay();
        int maxDelay = (WiredPlatform.configuration() == null)
                ? 20
                : WiredPlatform.configuration().getInt("hotel.wired.max_delay", WiredInputGuard.DEFAULT_MAX_DELAY);
        if (!WiredDelay.isValid(delay, maxDelay)) {
            return false;
        }

        synchronized (this.items) {
            this.items.clear();
            this.items.addAll(picked);
        }
        this.roomIdText = text;
        this.userSource = users;
        this.furniSource = furni;
        this.clearPairCache();
        this.setDelay(delay);

        return true;
    }

    @Override
    public WiredEffectType getType() {
        return type;
    }

    @Override
    public void execute(WiredContext ctx) {
        Room room = ctx.room();
        if (room == null) {
            return;
        }

        List<User> candidates = new ArrayList<>();
        for (RoomUnit unit : WiredSourceUtil.resolveUsers(ctx, this.userSource)) {
            if (candidates.size() >= MAX_FORWARDS_PER_RUN) {
                break;
            }
            User user = room.getUser(unit);
            if (user != null && user.getClient() != null && user.getUserInfo() != null) {
                candidates.add(user);
            }
        }
        if (candidates.isEmpty()) {
            return;
        }

        long now = System.currentTimeMillis();
        GameEnvironment environment = WiredPlatform.gameEnvironment();
        if (environment == null || environment.getRoomManager() == null) {
            return;
        }

        Destination destination = this.resolveDestination(ctx, room, environment, now);
        if (destination == null) {
            return;
        }

        // A teleporter whose partner stands in this room: AIR moves the user onto the partner, no room switch.
        if (destination.roomId() == room.getId()) {
            if (destination.teleportItemId() > 0) {
                this.toSameRoomPartner(room, candidates, destination.teleportItemId(), now);
            }
            return;
        }

        // The once-a-second limit is taken before the room is loaded: a box firing in a loop used to load its
        // destination (any room id) into memory every time, even when nobody could be forwarded.
        List<User> ready = new ArrayList<>();
        for (User user : candidates) {
            if (user.getUserInfo().tryAcquireRoomForward(now, FORWARD_INTERVAL_MS)) {
                ready.add(user);
            }
        }
        if (ready.isEmpty()) {
            return;
        }

        Room target = environment.getRoomManager().loadRoom(destination.roomId());
        if (target == null) {
            return;
        }

        ThreadPooling threading = WiredPlatform.threading();
        for (User user : ready) {
            if (!mayForward(user, target)) {
                continue;
            }

            if (threading == null) {
                forward(user, room, target, destination, now);
                continue;
            }

            // As in Habbo: the user glows from the firing, is taken into the linked room about a
            // second later, and arrives there still glowing. One the client did not take away gets
            // their own effect back a little later.
            RoomUnit unit = user.getRoomUnit();
            RoomForwardGlow.show(room, unit);
            threading.run(
                    () -> {
                        if (user.getClient() != null && user.getUserInfo().getCurrentRoom() == room) {
                            forward(user, room, target, destination, System.currentTimeMillis());
                        }
                    },
                    FORWARD_DELAY_MS);
            threading.run(() -> RoomForwardGlow.end(room, unit), GLOW_END_IF_STAYED_MS);
        }
    }

    private void toSameRoomPartner(Room room, List<User> candidates, int partnerId, long now) {
        ThreadPooling threading = WiredPlatform.threading();

        for (User user : candidates) {
            if (!user.getUserInfo().tryAcquireRoomForward(now, FORWARD_INTERVAL_MS)) {
                continue;
            }

            RoomUnit unit = user.getRoomUnit();
            RoomForwardGlow.show(room, unit);
            Runnable land = () -> {
                if (user.getUserInfo().getCurrentRoom() == room) {
                    landOnPartner(room, unit, room.getUserItem(partnerId));
                }
            };

            if (threading == null) {
                land.run();
                continue;
            }

            threading.run(land, FORWARD_DELAY_MS);
            threading.run(() -> RoomForwardGlow.end(room, unit), GLOW_END_IF_STAYED_MS);
        }
    }

    /** Puts the user on the partner's tile, whoever else stands there, facing the way the partner faces. */
    static void landOnPartner(Room room, RoomUnit unit, UserItem partner) {
        if (room == null || unit == null || partner == null || room.getLayout() == null) {
            return;
        }

        RoomTile tile = room.getLayout().getTile(partner.getX(), partner.getY());
        if (tile == null) {
            return;
        }

        new RoomUnitTeleport(unit, room, tile.x, tile.y, tile.getStackHeight(), unit.getEffectId()).run();
        unit.setRotation(RoomUserRotation.fromValue(partner.getRotation()));
        room.sendComposer(new RoomUserStatusComposer(unit).compose());
    }

    private static void forward(User user, Room room, Room target, Destination destination, long now) {
        user.getUserInfo().setPendingRoomEntry(destination.entry(now));
        if (room != null && user.getRoomUnit() != null) {
            user.getUserInfo().setRoomForwardGlow(target.getId(), now + PendingRoomEntry.LIFETIME_MS);
        }
        user.getClient().sendResponse(new ForwardToRoomComposer(target.getId()));
    }

    Destination resolveDestination(WiredContext ctx, Room room, GameEnvironment environment, long now) {
        List<UserItem> furni = WiredSourceUtil.resolveItems(ctx, this.furniSource, this.pickedItems(room));
        boolean pairLooked = false;
        int checked = 0;

        for (UserItem item : furni) {
            if (checked++ >= MAX_FURNI_CHECKED) {
                break;
            }

            int linkedRoomId = linkedRoomId(item);
            if (linkedRoomId > 0) {
                return new Destination(linkedRoomId, PendingRoomEntry.METHOD_ROOM_NETWORK, 0);
            }

            if (item instanceof InteractionTeleport && !pairLooked) {
                pairLooked = true;
                Destination pair = this.teleportDestination(item, environment, now);
                if (pair != null) {
                    return pair;
                }
            }
        }

        int typed = parseRoomId(this.roomIdText);
        return (typed > 0) ? new Destination(typed, PendingRoomEntry.METHOD_DOOR, 0) : null;
    }

    /** Where the teleporter's pair stands now, looked up at most once a second per box. */
    private Destination teleportDestination(UserItem teleport, GameEnvironment environment, long now) {
        if (this.cachedPairTeleportId == teleport.getId() && now - this.cachedPairAt < PAIR_CACHE_MS) {
            return (this.cachedPairRoomId > 0)
                    ? new Destination(this.cachedPairRoomId, PendingRoomEntry.METHOD_TELEPORT, this.cachedPairItemId)
                    : null;
        }
        if (now - this.lastPairLookupAt < PAIR_LOOKUP_INTERVAL_MS || environment.getItemManager() == null) {
            return null;
        }
        this.lastPairLookupAt = now;

        int[] pair = environment.getItemManager().getTargetTeleportRoomId(teleport);
        boolean paired = pair != null && pair.length == 2 && pair[0] > 0 && pair[1] > 0;

        this.cachedPairTeleportId = teleport.getId();
        this.cachedPairRoomId = paired ? pair[0] : 0;
        this.cachedPairItemId = paired ? pair[1] : 0;
        this.cachedPairAt = now;

        return paired ? new Destination(pair[0], PendingRoomEntry.METHOD_TELEPORT, pair[1]) : null;
    }

    /** The checks the navigator makes before it opens a room; a doorbell or password is left to it. */
    static boolean mayForward(User user, Room target) {
        Room current = user.getUserInfo().getCurrentRoom();
        if (current != null && current.getId() == target.getId()) {
            return false;
        }

        boolean staff =
                user.hasPermission(Permission.ACC_ANYROOMOWNER) || user.hasPermission(Permission.ACC_ENTERANYROOM);
        if (staff) {
            return true;
        }
        if (target.isBanned(user)) {
            return false;
        }

        return target.getState() != RoomState.INVISIBLE || target.isOwner(user) || target.hasRights(user);
    }

    static boolean isRoomLink(UserItem item) {
        if (!(item instanceof InteractionCustomValues custom)) {
            return false;
        }
        synchronized (custom.values) {
            return custom.values.containsKey(ROOM_LINK_KEY);
        }
    }

    static int linkedRoomId(UserItem item) {
        if (!(item instanceof InteractionCustomValues custom)) {
            return 0;
        }
        String link;
        synchronized (custom.values) {
            link = custom.values.get(ROOM_LINK_KEY);
        }
        return parseRoomId(link);
    }

    /** A positive room id written as plain digits, or 0. */
    static int parseRoomId(String text) {
        if (text == null) {
            return 0;
        }
        String trimmed = text.trim();
        if (trimmed.isEmpty() || trimmed.length() > MAX_ROOM_ID_DIGITS) {
            return 0;
        }
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (c < '0' || c > '9') {
                return 0;
            }
        }
        long value = Long.parseLong(trimmed);
        return (value > 0 && value <= Integer.MAX_VALUE) ? (int) value : 0;
    }

    private List<UserItem> pickedItems(Room room) {
        synchronized (this.items) {
            if (room != null) {
                this.items.removeIf(item ->
                        item == null || item.getRoomId() != this.getRoomId() || room.getUserItem(item.getId()) == null);
            }
            return new ArrayList<>(this.items);
        }
    }

    private void clearPairCache() {
        this.cachedPairTeleportId = 0;
        this.cachedPairRoomId = 0;
        this.cachedPairItemId = 0;
        this.cachedPairAt = 0;
    }

    @Override
    @Deprecated
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public String getWiredData() {
        List<Integer> itemIds = new ArrayList<>();
        synchronized (this.items) {
            for (UserItem item : this.items) {
                itemIds.add(item.getId());
            }
        }
        return WiredManager.getGson()
                .toJson(new JsonData(this.roomIdText, this.getDelay(), this.userSource, this.furniSource, itemIds));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        String wiredData = set.getString("wired_data");
        JsonData data = WiredMovementPayloadGuard.fromJson(wiredData, JsonData.class);

        synchronized (this.items) {
            this.items.clear();
            if (data != null && data.itemIds != null && room != null) {
                for (Integer id : data.itemIds) {
                    if (this.items.size() >= WiredManager.MAXIMUM_FURNI_SELECTION) {
                        break;
                    }
                    UserItem item = (id != null) ? room.getUserItem(id) : null;
                    if (item != null && !this.items.contains(item)) {
                        this.items.add(item);
                    }
                }
            }
        }

        if (data != null) {
            String text = (data.roomIdText == null) ? "" : data.roomIdText.trim();
            this.roomIdText = (parseRoomId(text) > 0) ? text : "";
            this.setDelay(WiredMovementPayloadGuard.delay(data.delay));
            this.userSource = WiredMovementPayloadGuard.userSource(data.userSource);
            // Boxes saved before the furni source existed only had the typed room.
            this.furniSource = (data.furniSource != null)
                    ? WiredMovementPayloadGuard.furniSource(data.furniSource)
                    : WiredSourceUtil.SOURCE_SELECTED;
        } else {
            this.roomIdText = "";
            this.userSource = WiredSourceUtil.SOURCE_TRIGGER;
            this.furniSource = WiredSourceUtil.SOURCE_SELECTED;
            this.setDelay(0);
        }
        this.clearPairCache();
    }

    @Override
    public void onPickUp() {
        synchronized (this.items) {
            this.items.clear();
        }
        this.roomIdText = "";
        this.userSource = WiredSourceUtil.SOURCE_TRIGGER;
        this.furniSource = WiredSourceUtil.SOURCE_SELECTED;
        this.clearPairCache();
        this.setDelay(0);
    }

    @Override
    public boolean requiresTriggeringUser() {
        return this.userSource == WiredSourceUtil.SOURCE_TRIGGER;
    }

    @Override
    protected long requiredCooldown() {
        return COOLDOWN_TELEPORT;
    }

    record Destination(int roomId, String method, int teleportItemId) {
        PendingRoomEntry entry(long now) {
            return switch (this.method) {
                case PendingRoomEntry.METHOD_TELEPORT ->
                    PendingRoomEntry.teleport(this.roomId, this.teleportItemId, now);
                case PendingRoomEntry.METHOD_ROOM_NETWORK -> PendingRoomEntry.roomNetwork(this.roomId, now);
                default -> null;
            };
        }
    }

    static class JsonData {
        String roomIdText;
        int delay;
        int userSource;
        Integer furniSource;
        List<Integer> itemIds;

        public JsonData(String roomIdText, int delay, int userSource, int furniSource, List<Integer> itemIds) {
            this.roomIdText = roomIdText;
            this.delay = delay;
            this.userSource = userSource;
            this.furniSource = furniSource;
            this.itemIds = itemIds;
        }
    }
}
