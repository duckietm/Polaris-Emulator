package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.WiredPlatform;
import com.eu.habbo.habbohotel.GameEnvironment;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionCustomValues;
import com.eu.habbo.habbohotel.items.interactions.InteractionTeleport;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredTrigger;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.rooms.PendingRoomEntry;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomState;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;
import com.eu.habbo.messages.outgoing.rooms.ForwardToRoomComposer;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

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
 * when furni are picked; stuff ids the picked room linkers, room links or teleporters. Any other pick
 * is refused with the room-linker error the client shows.
 */
public class WiredEffectForwardUserToRoom extends InteractionWiredEffect {
    public static final WiredEffectType type = WiredEffectType.TELEPORT_TO_ROOM;

    static final String ROOM_LINK_KEY = "internalLink";
    static final long FORWARD_INTERVAL_MS = 2000L;
    static final int MAX_FORWARDS_PER_RUN = 50;
    static final int MAX_FURNI_CHECKED = 20;
    static final long PAIR_LOOKUP_INTERVAL_MS = 1000L;
    static final long PAIR_CACHE_MS = 5000L;
    private static final int MAX_ROOM_ID_DIGITS = 10;

    private final List<HabboItem> items = new ArrayList<>();
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
        List<HabboItem> picked = this.pickedItems(room);

        message.appendBoolean(false);
        message.appendInt(WiredManager.MAXIMUM_FURNI_SELECTION);
        message.appendInt(picked.size());
        for (HabboItem item : picked) {
            message.appendInt(item.getId());
        }
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString(this.roomIdText);
        message.appendInt(2);
        message.appendInt(this.userSource);
        message.appendInt(this.furniSource);
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
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        String text =
                (settings.getStringParam() != null) ? settings.getStringParam().trim() : "";
        if (!text.isEmpty() && parseRoomId(text) <= 0) {
            return false;
        }

        int[] params = settings.getIntParams();
        int users =
                WiredMovementPayloadGuard.userSource((params.length > 0) ? params[0] : WiredSourceUtil.SOURCE_TRIGGER);
        int furni = (params.length > 1)
                ? WiredMovementPayloadGuard.furniSource(params[1])
                : WiredSourceUtil.SOURCE_SELECTED;

        int[] furniIds = settings.getFurniIds();
        if (furniIds.length > WiredManager.MAXIMUM_FURNI_SELECTION) {
            return false;
        }

        List<HabboItem> picked = new ArrayList<>();
        if (furni == WiredSourceUtil.SOURCE_SELECTED && furniIds.length > 0) {
            Room room = this.getRoom();
            if (room == null) {
                return false;
            }
            for (int itemId : furniIds) {
                HabboItem item = room.getHabboItem(itemId);
                if (item == null) {
                    return false;
                }
                // Room linkers are teleporters, so they pass here with plain teleporters and room links.
                if (!isRoomLink(item) && !(item instanceof InteractionTeleport)) {
                    throw new WiredSaveException("wiredfurni.error.require_room_linker");
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
                : WiredPlatform.configuration().getInt("hotel.wired.max_delay", 20);
        if (delay < 0 || delay > maxDelay) {
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

        List<Habbo> candidates = new ArrayList<>();
        for (RoomUnit unit : WiredSourceUtil.resolveUsers(ctx, this.userSource)) {
            if (candidates.size() >= MAX_FORWARDS_PER_RUN) {
                break;
            }
            Habbo habbo = room.getHabbo(unit);
            if (habbo != null && habbo.getClient() != null && habbo.getHabboInfo() != null) {
                candidates.add(habbo);
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
        if (destination == null || destination.roomId() == room.getId()) {
            return;
        }

        Room target = environment.getRoomManager().loadRoom(destination.roomId());
        if (target == null) {
            return;
        }

        for (Habbo habbo : candidates) {
            if (!mayForward(habbo, target)) {
                continue;
            }
            if (!habbo.getHabboInfo().tryAcquireRoomForward(now, FORWARD_INTERVAL_MS)) {
                continue;
            }
            habbo.getHabboInfo().setPendingRoomEntry(destination.entry(now));
            habbo.getClient().sendResponse(new ForwardToRoomComposer(target.getId()));
        }
    }

    Destination resolveDestination(WiredContext ctx, Room room, GameEnvironment environment, long now) {
        List<HabboItem> furni = WiredSourceUtil.resolveItems(ctx, this.furniSource, this.pickedItems(room));
        boolean pairLooked = false;
        int checked = 0;

        for (HabboItem item : furni) {
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
    private Destination teleportDestination(HabboItem teleport, GameEnvironment environment, long now) {
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
    static boolean mayForward(Habbo habbo, Room target) {
        Room current = habbo.getHabboInfo().getCurrentRoom();
        if (current != null && current.getId() == target.getId()) {
            return false;
        }

        boolean staff =
                habbo.hasPermission(Permission.ACC_ANYROOMOWNER) || habbo.hasPermission(Permission.ACC_ENTERANYROOM);
        if (staff) {
            return true;
        }
        if (target.isBanned(habbo)) {
            return false;
        }

        return target.getState() != RoomState.INVISIBLE || target.isOwner(habbo) || target.hasRights(habbo);
    }

    static boolean isRoomLink(HabboItem item) {
        if (!(item instanceof InteractionCustomValues custom)) {
            return false;
        }
        synchronized (custom.values) {
            return custom.values.containsKey(ROOM_LINK_KEY);
        }
    }

    static int linkedRoomId(HabboItem item) {
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

    private List<HabboItem> pickedItems(Room room) {
        synchronized (this.items) {
            if (room != null) {
                this.items.removeIf(item -> item == null
                        || item.getRoomId() != this.getRoomId()
                        || room.getHabboItem(item.getId()) == null);
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
            for (HabboItem item : this.items) {
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
                    HabboItem item = (id != null) ? room.getHabboItem(id) : null;
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
