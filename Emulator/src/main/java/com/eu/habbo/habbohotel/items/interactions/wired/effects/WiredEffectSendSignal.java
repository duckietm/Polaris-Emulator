package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredTrigger;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomSpecialTypes;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.core.*;
import com.eu.habbo.habbohotel.wired.core.WiredEvent;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;
import gnu.trove.procedure.TObjectProcedure;
import gnu.trove.set.hash.THashSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;


public class WiredEffectSendSignal extends InteractionWiredEffect {
    private static final Logger LOGGER = LoggerFactory.getLogger(WiredEffectSendSignal.class);

    public static final WiredEffectType type = WiredEffectType.SEND_SIGNAL;

    public static int MAX_SIGNAL_DEPTH = 100;

    private static final int ANTENNA_PICKED  = 0;
    private static final int ANTENNA_TRIGGER = 1;
    private static final String ANTENNA_INTERACTION = "antenna";
    private static final String FORWARD_ITEM_SPLIT_REGEX = "[;,\\t]";
    private static final long ANTENNA_PULSE_MS = 300L;
    private static final ConcurrentHashMap<Integer, Long> ANTENNA_PULSE_TOKENS = new ConcurrentHashMap<>();

    private THashSet<HabboItem> items;
    private THashSet<HabboItem> forwardItems;
    private int     antennaSource   = ANTENNA_PICKED;
    private int     furniForward    = WiredSourceUtil.SOURCE_TRIGGER;
    private int     userForward     = WiredSourceUtil.SOURCE_TRIGGER;
    private boolean signalPerFurni  = false;
    private boolean signalPerUser   = false;
    private int     channel         = 0;

    public WiredEffectSendSignal(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
        this.items = new THashSet<>();
        this.forwardItems = new THashSet<>();
    }

    public WiredEffectSendSignal(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
        this.items = new THashSet<>();
        this.forwardItems = new THashSet<>();
    }

    @Override
    public void execute(WiredContext ctx) {
        Room room = ctx.room();
        if (room == null) return;

        LOGGER.debug("[SendSignal] execute() called, itemId={}, antennaSource={}, pickedItems={}", this.getId(), antennaSource, this.items.size());

        int currentDepth = ctx.event().getCallStackDepth();
        if (currentDepth >= MAX_SIGNAL_DEPTH) {
            LOGGER.debug("[SendSignal] Max signal depth reached ({}), aborting", currentDepth);
            return;
        }

        Collection<HabboItem> antennas;
        if (antennaSource == ANTENNA_TRIGGER) {
            antennas = ctx.sourceItem()
                    .map(Collections::<HabboItem>singleton)
                    .orElse(Collections.emptySet());
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

        if (resolvedAntennas.isEmpty()) {
            LOGGER.debug("[SendSignal] No antennas resolved, aborting. antennaSource={}, selectorModified={}", antennaSource, ctx.targets().isItemsModifiedBySelector());
            return;
        }
        LOGGER.debug("[SendSignal] Resolved {} antenna(s), firing signals", resolvedAntennas.size());

        RoomUnit triggeringUser = ctx.event().getOriginActor().orElseGet(() -> ctx.actor().orElse(null));
        List<RoomUnit> forwardedUsers = WiredSourceUtil.resolveUsersRaw(ctx, this.userForward);
        List<HabboItem> forwardedFurni = WiredSourceUtil.resolveItemsRaw(ctx, this.furniForward, this.forwardItems);

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

            usersToSend = mergedUsers.isEmpty()
                    ? Collections.singletonList(null)
                    : new ArrayList<>(mergedUsers.values());
        } else {
            usersToSend = Collections.singletonList(triggeringUser);
        }

        Collection<HabboItem> furniToSend = !forwardedFurni.isEmpty()
                ? forwardedFurni
                : Collections.singletonList(null);

        int nextDepth = currentDepth + 1;
        int signalUserCount = signalPerUser
                ? (int) usersToSend.stream().filter(Objects::nonNull).count()
                : (!forwardedUsers.isEmpty() ? forwardedUsers.size() : (triggeringUser != null ? 1 : 0));

        for (RoomUnit user : usersToSend) {
            for (HabboItem sourceItem : furniToSend) {
                for (HabboItem antenna : resolvedAntennas) {
                    fireSignalAtAntenna(ctx, room, antenna, user, triggeringUser, sourceItem, signalUserCount, nextDepth);
                }
            }
        }
    }

    private void fireSignalAtAntenna(WiredContext ctx, Room room, HabboItem antenna, RoomUnit actor, RoomUnit originActor, HabboItem sourceItem, int signalUserCount, int depth) {
        if (antenna == null) return;
        RoomTile tile = room.getLayout().getTile(antenna.getX(), antenna.getY());
        if (tile == null) return;

        pulseAntenna(room, antenna);

        int signalChannel = antenna.getId();

        LOGGER.debug("[SendSignal] fireSignalAtAntenna: antennaId={} tile={},{} depth={} channel={} actor={} sourceItem={}",
                signalChannel, tile.x, tile.y, depth, signalChannel, actor != null ? actor.getId() : "null", sourceItem != null ? sourceItem.getId() : "null");

        WiredEvent.Builder builder = WiredEvent.builder(WiredEvent.Type.SIGNAL_RECEIVED, room)
                .tile(tile)
                .callStackDepth(depth)
                .signalChannel(signalChannel)
                .signalUserCount(signalUserCount)
                .signalFurniCount(sourceItem != null ? 1 : 0)
                .contextVariableScope(ctx.contextVariables().copy())
                .triggeredByEffect(true);

        if (actor != null) builder.actor(actor);
        if (originActor != null) builder.originActor(originActor);
        if (sourceItem != null) builder.sourceItem(sourceItem);

        boolean result = dispatchSignalEvent(builder.build());
        LOGGER.debug("[SendSignal] handleEvent returned: {}", result);
    }

    private void pulseAntenna(Room room, HabboItem antenna) {
        if (room == null || antenna == null || antenna.getBaseItem() == null) return;
        if (antenna.getBaseItem().getStateCount() <= 1) return;

        final long token = System.currentTimeMillis();
        ANTENNA_PULSE_TOKENS.put(antenna.getId(), token);

        if ("1".equals(antenna.getExtradata())) {
            antenna.setExtradata("0");
            room.updateItemState(antenna);
        }

        antenna.setExtradata("1");
        room.updateItemState(antenna);

        Emulator.getThreading().run(() -> {
            if (!room.isLoaded()) return;

            Long currentToken = ANTENNA_PULSE_TOKENS.get(antenna.getId());
            if (currentToken == null || currentToken.longValue() != token) return;

            antenna.setExtradata("0");
            room.updateItemState(antenna);
            ANTENNA_PULSE_TOKENS.remove(antenna.getId(), token);
        }, ANTENNA_PULSE_MS);
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        List<HabboItem> itemsSnapshot = new ArrayList<>(this.items);

        itemsSnapshot.removeIf(item ->
                item.getRoomId() != this.getRoomId() || room.getHabboItem(item.getId()) == null);
        this.items.retainAll(itemsSnapshot);

        List<HabboItem> forwardSnapshot = new ArrayList<>(this.forwardItems);
        forwardSnapshot.removeIf(item ->
                item.getRoomId() != this.getRoomId() || room.getHabboItem(item.getId()) == null);
        this.forwardItems.retainAll(forwardSnapshot);

        String forwardString = forwardSnapshot.stream()
                .filter(Objects::nonNull)
                .map(item -> Integer.toString(item.getId()))
                .collect(Collectors.joining(";"));

        message.appendBoolean(false);
        message.appendInt(WiredManager.MAXIMUM_FURNI_SELECTION);
        message.appendInt(itemsSnapshot.size());
        for (HabboItem item : itemsSnapshot) {
            message.appendInt(item.getId());
        }
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString(forwardString);

        message.appendInt(6);
        message.appendInt(antennaSource);
        message.appendInt(furniForward);
        message.appendInt(userForward);
        message.appendInt(signalPerFurni ? 1 : 0);
        message.appendInt(signalPerUser ? 1 : 0);
        message.appendInt(channel);

        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(this.getDelay());

        if (this.requiresTriggeringUser()) {
            List<Integer> invalidTriggers = new ArrayList<>();
            room.getRoomSpecialTypes().getTriggers(this.getX(), this.getY()).forEach(new TObjectProcedure<InteractionWiredTrigger>() {
                @Override
                public boolean execute(InteractionWiredTrigger object) {
                    if (!object.isTriggeredByRoomUnit()) {
                        invalidTriggers.add(object.getBaseItem().getSpriteId());
                    }
                    return true;
                }
            });
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
                throw new WiredSaveException("Only antenna furni can be selected");
            }
        }

        int delay = settings.getDelay();
        if (delay > Emulator.getConfig().getInt("hotel.wired.max_delay", 20)) {
            throw new WiredSaveException("Delay too long");
        }

        int[] params = settings.getIntParams();
        int requestedAntennaSource = params.length > 0 ? params[0] : ANTENNA_PICKED;
        this.furniForward   = normalizeSource(params.length > 1 ? params[1] : WiredSourceUtil.SOURCE_TRIGGER);
        this.userForward    = normalizeSource(params.length > 2 ? params[2] : WiredSourceUtil.SOURCE_TRIGGER);
        this.signalPerFurni = params.length > 3 && params[3] == 1;
        this.signalPerUser  = params.length > 4 && params[4] == 1;
        this.channel = params.length > 5 ? params[5] : 0;
        this.antennaSource = requestedAntennaSource;
        if (!newItems.isEmpty()) {
            this.antennaSource = newItems.get(0).getId();
        }

        List<HabboItem> newForwardItems = new ArrayList<>();
        if (this.furniForward == WiredSourceUtil.SOURCE_SELECTED && room != null) {
            newForwardItems = parseForwardItems(settings.getStringParam(), room);
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

        LOGGER.debug("[SendSignal] saveData: antennaSource={}, furniForward={}, userForward={}, signalPerFurni={}, signalPerUser={}, channel={}, items={}, forwardItems={}",
                antennaSource, furniForward, userForward, signalPerFurni, signalPerUser, channel, items.size(), forwardItems.size());

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
        return WiredManager.getGson().toJson(new JsonData(
                this.getDelay(),
                itemsSnapshot.stream().map(HabboItem::getId).collect(Collectors.toList()),
                forwardSnapshot.stream().map(HabboItem::getId).collect(Collectors.toList()),
                antennaSource, furniForward, userForward, signalPerFurni, signalPerUser, channel
        ));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.items = new THashSet<>();
        this.forwardItems = new THashSet<>();
        String wiredData = set.getString("wired_data");

        JsonData data = WiredUtilityPayloadGuard.fromJson(wiredData, JsonData.class);
        if (data != null) {
            this.setDelay(WiredUtilityPayloadGuard.delay(data.delay));
            this.antennaSource  = data.antennaSource;
            this.furniForward   = normalizeSource(data.furniForward);
            this.userForward    = normalizeSource(data.userForward);
            this.signalPerFurni = data.signalPerFurni;
            this.signalPerUser  = data.signalPerUser;
            this.channel        = data.channel;
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

            if (this.antennaSource <= ANTENNA_TRIGGER && !this.items.isEmpty()) {
                HabboItem first = this.items.iterator().next();
                if (first != null) this.antennaSource = first.getId();
            }
        }
    }

    @Override
    public void onPickUp() {
        this.items.clear();
        this.forwardItems.clear();
        this.antennaSource  = ANTENNA_PICKED;
        this.furniForward   = WiredSourceUtil.SOURCE_TRIGGER;
        this.userForward    = WiredSourceUtil.SOURCE_TRIGGER;
        this.signalPerFurni = false;
        this.signalPerUser  = false;
        this.channel        = 0;
        this.setDelay(0);
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

        public JsonData(int delay, List<Integer> itemIds, List<Integer> forwardItemIds, int antennaSource, int furniForward,
                        int userForward, boolean signalPerFurni, boolean signalPerUser, int channel) {
            this.delay          = delay;
            this.itemIds        = itemIds;
            this.forwardItemIds = forwardItemIds;
            this.antennaSource  = antennaSource;
            this.furniForward   = furniForward;
            this.userForward    = userForward;
            this.signalPerFurni = signalPerFurni;
            this.signalPerUser  = signalPerUser;
            this.channel        = channel;
        }
    }
}
