package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.Emulator;
import com.eu.habbo.WiredCompatibilityDiagnostics;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredTrigger;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredEvent;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import com.eu.habbo.habbohotel.wired.protocol.WiredOfficialSecondaryPicks;
import com.eu.habbo.habbohotel.wired.protocol.WiredOfficialSources;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Official send signal (30) / negative send signal (37). Int params, official: {@code [split per furni,
 * split per user]}, ours follows: {@code channel}. Furni source slot 0 is the antennas (0 = the
 * triggering furni, 100 = the picks), slot 1 the furni to forward (100 = the second picks,
 * {@code stuffIds2}); user source slot 0 the users to forward.
 */
public class WiredEffectSendSignal extends InteractionWiredEffect
        implements WiredOfficialSources, WiredOfficialSecondaryPicks {
    private static final Logger LOGGER = LoggerFactory.getLogger(WiredEffectSendSignal.class);

    public static final WiredEffectType type = WiredEffectType.SEND_SIGNAL;

    public static volatile int MAX_SIGNAL_DEPTH = 100;

    private static final int ANTENNA_PICKED = 0;
    private static final int ANTENNA_TRIGGER = 1;
    private static final String ANTENNA_INTERACTION = "antenna";
    private static final String FORWARD_ITEM_SPLIT_REGEX = "[;,\\t]";
    // Users x furni x antennas can multiply; one firing never sends more than this many signals.
    static final int MAX_SIGNALS_PER_FIRING = 250;
    static final int MAX_FORWARDED = 100;

    private Set<HabboItem> items;
    private Set<HabboItem> forwardItems;
    private int antennaSource = ANTENNA_PICKED;
    private int furniForward = WiredSourceUtil.SOURCE_TRIGGER;
    private int userForward = WiredSourceUtil.SOURCE_TRIGGER;
    private boolean signalPerFurni = false;
    private boolean signalPerUser = false;
    private int channel = 0;

    public WiredEffectSendSignal(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
        this.items = new LinkedHashSet<>();
        this.forwardItems = new LinkedHashSet<>();
    }

    public WiredEffectSendSignal(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
        this.items = new LinkedHashSet<>();
        this.forwardItems = new LinkedHashSet<>();
    }

    @Override
    public void execute(WiredContext ctx) {
        Room room = ctx.room();
        if (room == null) return;

        LOGGER.debug(
                "[SendSignal] execute() called, itemId={}, antennaSource={}, pickedItems={}",
                this.getId(),
                antennaSource,
                this.items.size());

        int currentDepth = ctx.event().getCallStackDepth();
        if (currentDepth >= MAX_SIGNAL_DEPTH) {
            LOGGER.debug("[SendSignal] Max signal depth reached ({}), aborting", currentDepth);
            return;
        }

        Collection<HabboItem> antennas;
        if (antennaSource == ANTENNA_TRIGGER) {
            antennas = ctx.sourceItem().map(Collections::<HabboItem>singleton).orElse(Collections.emptySet());
        } else {
            Collection<HabboItem> baseAntennas = new ArrayList<>(this.items);

            if (baseAntennas.isEmpty() && antennaSource > ANTENNA_TRIGGER) {
                HabboItem antenna = room.getHabboItem(antennaSource);
                antennas = (antenna != null) ? Collections.singleton(antenna) : Collections.emptySet();
            } else {
                antennas = baseAntennas;
            }
        }

        List<HabboItem> resolvedAntennas = antennas.stream()
                .filter(Objects::nonNull)
                .filter(this::isAntennaItem)
                .collect(Collectors.toList());

        // No antenna picked: the stack's selectors can name them instead, as Habbo's box allows.
        if (resolvedAntennas.isEmpty()
                && antennaSource == ANTENNA_PICKED
                && ctx.targets().isItemsModifiedBySelector()) {
            resolvedAntennas = ctx.targets().items().stream()
                    .filter(Objects::nonNull)
                    .filter(this::isAntennaItem)
                    .limit(WiredManager.MAXIMUM_FURNI_SELECTION)
                    .collect(Collectors.toList());
        }

        if (resolvedAntennas.isEmpty()) {
            LOGGER.debug(
                    "[SendSignal] No antennas resolved, aborting. antennaSource={}, selectorModified={}",
                    antennaSource,
                    ctx.targets().isItemsModifiedBySelector());
            return;
        }
        LOGGER.debug("[SendSignal] Resolved {} antenna(s), firing signals", resolvedAntennas.size());

        RoomUnit triggeringUser =
                ctx.event().getOriginActor().orElseGet(() -> ctx.actor().orElse(null));
        List<RoomUnit> forwardedUsers = capped(WiredSourceUtil.resolveUsersRaw(ctx, this.userForward));
        List<HabboItem> forwardedFurni =
                capped(WiredSourceUtil.resolveItemsRaw(ctx, this.furniForward, this.forwardItems));

        List<RoomUnit> usersToSend;
        if (signalPerUser) {
            LinkedHashMap<Integer, RoomUnit> mergedUsers = new LinkedHashMap<>();

            if (triggeringUser != null) {
                mergedUsers.put(triggeringUser.getId(), triggeringUser);
            }

            for (RoomUnit forwardedUser : forwardedUsers) {
                if (forwardedUser == null) {
                    continue;
                }

                mergedUsers.put(forwardedUser.getId(), forwardedUser);
            }

            usersToSend =
                    mergedUsers.isEmpty() ? Collections.singletonList(null) : new ArrayList<>(mergedUsers.values());
        } else {
            usersToSend = Collections.singletonList(triggeringUser);
        }

        // "Send signal for each furni", the mirror of signalPerUser, which this box stored and never
        // read: it always split. Off now means one signal carrying the first forwarded furni, so the
        // receiving stack still has a source item to work with and reads the full count from
        // @signal_furni_count.
        Collection<HabboItem> furniToSend;
        if (forwardedFurni.isEmpty()) {
            furniToSend = Collections.singletonList(null);
        } else if (signalPerFurni) {
            furniToSend = forwardedFurni;
        } else {
            furniToSend = Collections.singletonList(forwardedFurni.get(0));
        }

        int nextDepth = currentDepth + 1;
        int signalUserCount = signalPerUser
                ? (int) usersToSend.stream().filter(Objects::nonNull).count()
                : (!forwardedUsers.isEmpty() ? forwardedUsers.size() : (triggeringUser != null ? 1 : 0));
        int signalFurniCount = forwardedFurni.size();

        // Each antenna lights up once per firing, however many signals go through it.
        for (HabboItem antenna : resolvedAntennas) {
            pulseAntenna(room, antenna);
        }

        // What each signal passes on: the whole forwarded sets, or just its own user or furni when
        // the box splits them - so "users/furni from signal" in the receiving stack sees them all.
        List<RoomUnit> allUsers = !forwardedUsers.isEmpty()
                ? forwardedUsers
                : (triggeringUser != null ? List.of(triggeringUser) : List.of());

        int signalsLeft = MAX_SIGNALS_PER_FIRING;

        for (RoomUnit user : usersToSend) {
            List<RoomUnit> usersCarried = signalPerUser ? (user != null ? List.of(user) : List.of()) : allUsers;

            for (HabboItem sourceItem : furniToSend) {
                List<HabboItem> furniCarried =
                        signalPerFurni ? (sourceItem != null ? List.of(sourceItem) : List.of()) : forwardedFurni;

                for (HabboItem antenna : resolvedAntennas) {
                    if (signalsLeft-- <= 0) {
                        LOGGER.debug(
                                "[SendSignal] Signal cap of {} reached, the rest are dropped", MAX_SIGNALS_PER_FIRING);
                        return;
                    }

                    fireSignalAtAntenna(
                            ctx,
                            room,
                            antenna,
                            user,
                            triggeringUser,
                            sourceItem,
                            usersCarried,
                            furniCarried,
                            signalUserCount,
                            signalFurniCount,
                            nextDepth);
                }
            }
        }
    }

    /** At most MAX_FORWARDED of a forwarded set, as one immutable list every signal shares. */
    private static <T> List<T> capped(List<T> values) {
        if (values == null || values.isEmpty()) return List.of();

        return List.copyOf(
                values.stream().filter(Objects::nonNull).limit(MAX_FORWARDED).toList());
    }

    private void fireSignalAtAntenna(
            WiredContext ctx,
            Room room,
            HabboItem antenna,
            RoomUnit actor,
            RoomUnit originActor,
            HabboItem sourceItem,
            List<RoomUnit> usersCarried,
            List<HabboItem> furniCarried,
            int signalUserCount,
            int signalFurniCount,
            int depth) {
        if (antenna == null) return;
        RoomTile tile = room.getLayout().getTile(antenna.getX(), antenna.getY());
        if (tile == null) return;

        int signalChannel = antenna.getId();

        LOGGER.debug(
                "[SendSignal] fireSignalAtAntenna: antennaId={} tile={},{} depth={} channel={} actor={} sourceItem={}",
                signalChannel,
                tile.x,
                tile.y,
                depth,
                signalChannel,
                actor != null ? actor.getId() : "null",
                sourceItem != null ? sourceItem.getId() : "null");

        // The furni of the signal are reached through "furni from signal" (201) only; the receiving stack's
        // triggering furni is the antenna, as in Habbo.
        List<HabboItem> forwarded = furniCarried.isEmpty() && sourceItem != null ? List.of(sourceItem) : furniCarried;

        WiredEvent.Builder builder = WiredEvent.builder(WiredEvent.Type.SIGNAL_RECEIVED, room)
                .tile(tile)
                .callStackDepth(depth)
                .signalChannel(signalChannel)
                .signalUserCount(signalUserCount)
                .signalFurniCount(signalFurniCount)
                .forwardedUsers(usersCarried)
                .forwardedItems(forwarded)
                .sourceItem(antenna)
                .contextVariableScope(ctx.contextVariables().copy())
                .triggeredByEffect(true);

        if (actor != null) builder.actor(actor);
        if (originActor != null) builder.originActor(originActor);

        boolean result = dispatchSignalEvent(builder.build());
        LOGGER.debug("[SendSignal] handleEvent returned: {}", result);
    }

    // Habbo flips an antenna on every signal it carries (it does not light up and reset).
    private void pulseAntenna(Room room, HabboItem antenna) {
        if (room == null || antenna == null || antenna.getBaseItem() == null) return;
        if (antenna.getBaseItem().getStateCount() <= 1) return;

        synchronized (antenna) {
            antenna.setExtradata("1".equals(antenna.getExtradata()) ? "0" : "1");
        }

        room.updateItemState(antenna);
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        List<HabboItem> itemsSnapshot = new ArrayList<>(this.items);

        itemsSnapshot.removeIf(item -> item.getRoomId() != this.getRoomId() || room.getHabboItem(item.getId()) == null);
        this.items.retainAll(itemsSnapshot);

        List<HabboItem> forwardSnapshot = new ArrayList<>(this.forwardItems);
        forwardSnapshot.removeIf(
                item -> item.getRoomId() != this.getRoomId() || room.getHabboItem(item.getId()) == null);
        this.forwardItems.retainAll(forwardSnapshot);

        message.appendBoolean(false);
        message.appendInt(WiredManager.MAXIMUM_FURNI_SELECTION);
        message.appendInt(itemsSnapshot.size());
        for (HabboItem item : itemsSnapshot) {
            message.appendInt(item.getId());
        }
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString("");

        message.appendInt(3);
        message.appendInt(signalPerFurni ? 1 : 0);
        message.appendInt(signalPerUser ? 1 : 0);
        message.appendInt(channel);

        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(this.getDelay());

        if (this.requiresTriggeringUser()) {
            List<Integer> invalidTriggers = new ArrayList<>();
            for (InteractionWiredTrigger trigger : room.getRoomSpecialTypes().getTriggers(this.getX(), this.getY())) {
                if (!trigger.isTriggeredByRoomUnit()) {
                    invalidTriggers.add(trigger.getBaseItem().getSpriteId());
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
        int itemsCount = settings.getFurniIds().length;
        if (itemsCount > Emulator.getConfig().getInt("hotel.wired.furni.selection.count")) {
            throw new WiredSaveException("Too many furni selected");
        }

        List<HabboItem> newItems = new ArrayList<>();
        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());
        for (int i = 0; i < itemsCount; i++) {
            int itemId = settings.getFurniIds()[i];
            HabboItem it = room.getHabboItem(itemId);
            if (it == null) throw new WiredSaveException(String.format("Item %s not found", itemId));
            newItems.add(it);
        }

        for (HabboItem receiver : newItems) {
            if (!isAntennaItem(receiver)) {
                throw new WiredSaveException("wiredfurni.error.require_antenna_furni");
            }
        }

        int delay = settings.getDelay();
        if (delay > Emulator.getConfig().getInt("hotel.wired.max_delay", 20)) {
            throw new WiredSaveException("Delay too long");
        }

        int[] params = settings.getIntParams();
        int requestedAntennaSource =
                (settings.furniSource(0, WiredSourceUtil.SOURCE_SELECTED) == WiredSourceUtil.SOURCE_TRIGGER)
                        ? ANTENNA_TRIGGER
                        : ANTENNA_PICKED;
        this.furniForward = normalizeSource(settings.furniSource(1, WiredSourceUtil.SOURCE_TRIGGER));
        this.userForward = normalizeSource(settings.userSource(0, WiredSourceUtil.SOURCE_TRIGGER));
        this.signalPerFurni = params.length > 0 && params[0] == 1;
        this.signalPerUser = params.length > 1 && params[1] == 1;
        this.channel = params.length > 2 ? params[2] : 0;
        this.antennaSource = requestedAntennaSource;
        // "Use the triggering furni" is a deliberate choice; picking antennas alongside it used to
        // silently turn it into the first picked antenna.
        if (!newItems.isEmpty() && requestedAntennaSource != ANTENNA_TRIGGER) {
            this.antennaSource = newItems.get(0).getId();
        }

        List<HabboItem> newForwardItems = new ArrayList<>();
        if (this.furniForward == WiredSourceUtil.SOURCE_SELECTED && room != null) {
            // The official second picks; an older editor sent them as text.
            int[] picks2 = settings.getFurniIds2();
            newForwardItems = (picks2.length > 0)
                    ? parseForwardItems(
                            Arrays.stream(picks2).mapToObj(Integer::toString).collect(Collectors.joining(";")), room)
                    : parseForwardItems(settings.getStringParam(), room);
        }
        if (newForwardItems.size() > Emulator.getConfig().getInt("hotel.wired.furni.selection.count")) {
            throw new WiredSaveException("Too many furni selected");
        }

        this.items.clear();
        this.items.addAll(newItems);

        this.forwardItems.clear();
        if (this.furniForward == WiredSourceUtil.SOURCE_SELECTED) {
            this.forwardItems.addAll(newForwardItems);
        }
        this.setDelay(delay);

        LOGGER.debug(
                "[SendSignal] saveData: antennaSource={}, furniForward={}, userForward={}, signalPerFurni={}, signalPerUser={}, channel={}, items={}, forwardItems={}",
                antennaSource,
                furniForward,
                userForward,
                signalPerFurni,
                signalPerUser,
                channel,
                items.size(),
                forwardItems.size());

        return true;
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public String getWiredData() {
        List<HabboItem> itemsSnapshot = new ArrayList<>(this.items);
        List<HabboItem> forwardSnapshot = new ArrayList<>(this.forwardItems);
        return WiredManager.getGson()
                .toJson(new JsonData(
                        this.getDelay(),
                        itemsSnapshot.stream().map(HabboItem::getId).collect(Collectors.toList()),
                        forwardSnapshot.stream().map(HabboItem::getId).collect(Collectors.toList()),
                        antennaSource,
                        furniForward,
                        userForward,
                        signalPerFurni,
                        signalPerUser,
                        channel));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.items = new LinkedHashSet<>();
        this.forwardItems = new LinkedHashSet<>();
        String wiredData = set.getString("wired_data");

        JsonData data = WiredUtilityPayloadGuard.fromJson(wiredData, JsonData.class);
        if (data != null) {
            this.setDelay(WiredUtilityPayloadGuard.delay(data.delay));
            this.antennaSource = data.antennaSource;
            this.furniForward = normalizeSource(data.furniForward);
            this.userForward = normalizeSource(data.userForward);
            this.signalPerFurni = data.signalPerFurni;
            this.signalPerUser = data.signalPerUser;
            this.channel = data.channel;
            if (data.itemIds != null) {
                for (Integer id : data.itemIds) {
                    HabboItem item = room.getHabboItem(id);
                    if (item != null) this.items.add(item);
                }
            }
            if (data.forwardItemIds != null) {
                for (Integer id : data.forwardItemIds) {
                    HabboItem item = room.getHabboItem(id);
                    if (item != null) this.forwardItems.add(item);
                }
            }

            // The same rule as saveData: a stored "triggering furni" choice survives a reload.
            if (this.antennaSource < ANTENNA_TRIGGER && !this.items.isEmpty()) {
                HabboItem first = this.items.iterator().next();
                if (first != null) this.antennaSource = first.getId();
            }
        }
    }

    @Override
    public void onPickUp() {
        this.items.clear();
        this.forwardItems.clear();
        this.antennaSource = ANTENNA_PICKED;
        this.furniForward = WiredSourceUtil.SOURCE_TRIGGER;
        this.userForward = WiredSourceUtil.SOURCE_TRIGGER;
        this.signalPerFurni = false;
        this.signalPerUser = false;
        this.channel = 0;
        this.setDelay(0);
    }

    @Override
    public int[] officialFurniSources() {
        return new int[] {
            (this.antennaSource == ANTENNA_TRIGGER) ? WiredSourceUtil.SOURCE_TRIGGER : WiredSourceUtil.SOURCE_SELECTED,
            this.furniForward
        };
    }

    @Override
    public int[] officialUserSources() {
        return new int[] {this.userForward};
    }

    @Override
    public int[] officialFurniIds2() {
        return new ArrayList<>(this.forwardItems)
                .stream().mapToInt(HabboItem::getId).toArray();
    }

    private int normalizeSource(int source) {
        if (source == 1) return WiredSourceUtil.SOURCE_TRIGGER;
        if (source == WiredSourceUtil.SOURCE_TRIGGER
                || source == WiredSourceUtil.SOURCE_SELECTED
                || source == WiredSourceUtil.SOURCE_SELECTOR
                || source == WiredSourceUtil.SOURCE_SIGNAL) {
            return source;
        }
        return WiredSourceUtil.SOURCE_TRIGGER;
    }

    private List<HabboItem> parseForwardItems(String data, Room room) throws WiredSaveException {
        List<HabboItem> results = new ArrayList<>();
        if (data == null || data.trim().isEmpty() || room == null) return results;

        Set<Integer> seen = new HashSet<>();
        String[] parts = data.split(FORWARD_ITEM_SPLIT_REGEX);

        for (String part : parts) {
            if (part == null) continue;

            String trimmed = part.trim();
            if (trimmed.isEmpty()) continue;

            int itemId;
            try {
                itemId = Integer.parseInt(trimmed);
            } catch (NumberFormatException e) {
                continue;
            }

            if (itemId <= 0 || !seen.add(itemId)) continue;

            HabboItem item = room.getHabboItem(itemId);
            if (item == null) throw new WiredSaveException(String.format("Item %s not found", itemId));

            results.add(item);
        }

        return results;
    }

    private boolean isAntennaItem(HabboItem item) {
        if (item == null || item.getBaseItem() == null || item.getBaseItem().getInteractionType() == null) return false;
        String interaction = item.getBaseItem().getInteractionType().getName();
        if (interaction == null) return false;

        String normalized = interaction.toLowerCase();
        return normalized.equals(ANTENNA_INTERACTION);
    }

    @Override
    public WiredEffectType getType() {
        return type;
    }

    public int getChannel() {
        return channel;
    }

    public boolean hasPickedItem(int itemId) {
        try {
            for (HabboItem item : new ArrayList<>(this.items)) {
                if (item != null && item.getId() == itemId) return true;
            }
        } catch (Exception e) {
            WiredCompatibilityDiagnostics.record(
                    WiredCompatibilityDiagnostics.FailurePoint.EFFECT_SEND_SIGNAL_SELECTION,
                    this.getRoomId(),
                    this.getId(),
                    e);
        }
        return false;
    }

    public boolean unlinkAntenna(int antennaItemId) {
        if (antennaItemId <= 0) {
            return false;
        }

        boolean changed = false;

        Iterator<HabboItem> iterator = this.items.iterator();
        while (iterator.hasNext()) {
            HabboItem item = iterator.next();

            if (item == null || item.getId() != antennaItemId) {
                continue;
            }

            iterator.remove();
            changed = true;
        }

        if (this.antennaSource == antennaItemId) {
            if (!this.items.isEmpty()) {
                HabboItem firstItem = this.items.iterator().next();
                this.antennaSource = (firstItem != null) ? firstItem.getId() : ANTENNA_PICKED;
            } else {
                this.antennaSource = ANTENNA_PICKED;
            }

            changed = true;
        }

        if (changed) {
            this.needsUpdate(true);
        }

        return changed;
    }

    @Override
    protected long requiredCooldown() {
        return COOLDOWN_TRIGGER_STACKS;
    }

    protected boolean dispatchSignalEvent(WiredEvent event) {
        return WiredManager.dispatchEffectTriggeredEvent(event);
    }

    static class JsonData {
        int delay;
        List<Integer> itemIds;
        List<Integer> forwardItemIds;
        int antennaSource;
        int furniForward;
        int userForward;
        boolean signalPerFurni;
        boolean signalPerUser;
        int channel;

        public JsonData(
                int delay,
                List<Integer> itemIds,
                List<Integer> forwardItemIds,
                int antennaSource,
                int furniForward,
                int userForward,
                boolean signalPerFurni,
                boolean signalPerUser,
                int channel) {
            this.delay = delay;
            this.itemIds = itemIds;
            this.forwardItemIds = forwardItemIds;
            this.antennaSource = antennaSource;
            this.furniForward = furniForward;
            this.userForward = userForward;
            this.signalPerFurni = signalPerFurni;
            this.signalPerUser = signalPerUser;
            this.channel = channel;
        }
    }
}
