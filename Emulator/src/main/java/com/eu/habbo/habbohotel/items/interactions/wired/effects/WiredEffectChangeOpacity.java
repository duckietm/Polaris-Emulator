package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.WiredPlatform;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.rooms.WiredOpacityState;
import com.eu.habbo.habbohotel.rooms.WiredUserTransparency;
import com.eu.habbo.habbohotel.rooms.WiredVisualizationBroadcaster;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredDelay;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import com.eu.habbo.habbohotel.wired.protocol.WiredOfficialSources;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;
import com.eu.habbo.messages.outgoing.rooms.WiredFurniOpacityComposer;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Official set furni transparency (61). Int params, official: {@code [audience (1 everyone), visibility
 * (0 visible, 100 invisible), click through, easing, target users]}; ours follows: {@code transition
 * seconds}. The visibility is the transparency: in-between values are ours. Furni source slot 0 is
 * the target furni; user slot 0 the audience, slot 1 the target users. AIR's merged target picker
 * chooses furni or users, never both: with target users on, the picked avatars get the transparency
 * through the official avatar packet and no furni are touched. Internally the box keeps its opacity
 * (100 = fully visible).
 */
public class WiredEffectChangeOpacity extends InteractionWiredEffect implements WiredOfficialSources {
    public static final WiredEffectType type = WiredEffectType.CHANGE_OPACITY;

    static final int VISIBILITY_SOURCE_USERS = 0;
    static final int VISIBILITY_EVERYONE = 1;
    static final int EASING_INSTANT = 0;
    static final int MINIMUM_OPACITY = 0;
    static final int MAXIMUM_OPACITY = 100;
    static final int MINIMUM_DURATION_SECONDS = 1;
    static final int MAXIMUM_DURATION_SECONDS = 10;
    // 0 = "use the default transition"; resolved server-side so clients stay dumb.
    static final int DEFAULT_DURATION_SECONDS = 0;
    static final int DEFAULT_DURATION_MS = 400;

    private final List<HabboItem> items = new ArrayList<>();
    private int visibility = VISIBILITY_SOURCE_USERS;
    private int opacity = MAXIMUM_OPACITY;
    private int easing = EASING_INSTANT;
    private int durationSeconds = DEFAULT_DURATION_SECONDS;
    private int furniSource = WiredSourceUtil.SOURCE_SELECTED;
    private int userSource = WiredSourceUtil.SOURCE_TRIGGER;
    private boolean clickThrough;
    private boolean targetUsers;
    private int targetUserSource = WiredSourceUtil.SOURCE_TRIGGER;

    public WiredEffectChangeOpacity(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectChangeOpacity(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void execute(WiredContext context) {
        if (context == null || context.room() == null) {
            return;
        }

        Room room = context.room();
        if (this.targetUsers) {
            applyToUsers(room, context);
            return;
        }
        List<HabboItem> targets =
                currentTargets(room, WiredSourceUtil.resolveItems(context, this.furniSource, this.items));
        if (targets.isEmpty()) {
            return;
        }

        int durationMs = this.easing == EASING_INSTANT
                ? 0
                : this.durationSeconds == DEFAULT_DURATION_SECONDS ? DEFAULT_DURATION_MS : this.durationSeconds * 1_000;
        List<Integer> itemIds = targets.stream().map(HabboItem::getId).toList();
        if (this.visibility == VISIBILITY_EVERYONE) {
            List<WiredOpacityState> applied =
                    room.getWiredRuntime().applyGlobalOpacity(targets, this.opacity, this.clickThrough);
            if (applied.isEmpty()) {
                return;
            }
            for (Habbo recipient : room.getHabbos()) {
                if (!supportsOpacity(recipient)) {
                    continue;
                }
                List<WiredOpacityState> effective = room.getWiredRuntime()
                        .effectiveOpacity(recipient.getHabboInfo().getId(), itemIds);
                recipient
                        .getClient()
                        .sendResponse(new WiredFurniOpacityComposer(room.getId(), effective, this.easing, durationMs));
            }
            return;
        }

        Map<Integer, Habbo> recipients = resolveRecipients(room, context);
        for (Habbo recipient : recipients.values()) {
            int userId = recipient.getHabboInfo().getId();
            List<WiredOpacityState> applied =
                    room.getWiredRuntime().applyUserOpacity(userId, targets, this.opacity, this.clickThrough);
            if (!applied.isEmpty() && supportsOpacity(recipient)) {
                recipient
                        .getClient()
                        .sendResponse(new WiredFurniOpacityComposer(room.getId(), applied, this.easing, durationMs));
            }
        }
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        Room room = getRoom();
        int[] parameters = settings == null ? null : settings.getIntParams();
        int[] selectedIds = settings == null || settings.getFurniIds() == null ? new int[0] : settings.getFurniIds();
        if (room == null || parameters == null || parameters.length < 4) {
            throw new WiredSaveException("Invalid opacity effect data");
        }
        if (selectedIds.length > WiredManager.MAXIMUM_FURNI_SELECTION) {
            throw new WiredSaveException("Too many furni selected");
        }

        int delay = settings.getDelay();
        int maximumDelay = WiredPlatform.configuration().getInt("hotel.wired.max_delay", 20);
        if (!WiredDelay.isValid(delay, maximumDelay)) {
            throw new WiredSaveException("Delay out of range");
        }

        this.items.clear();
        for (int itemId : selectedIds) {
            HabboItem item = room.getHabboItem(itemId);
            if (item == null) {
                throw new WiredSaveException("Selected furni not found");
            }
            this.items.add(item);
        }

        this.visibility = normalizeVisibility(parameters[0]);
        this.opacity = MAXIMUM_OPACITY - clamp(parameters[1], MINIMUM_OPACITY, MAXIMUM_OPACITY);
        this.clickThrough = parameters[2] == 1;
        this.easing = clamp(parameters[3], 0, 4);
        this.targetUsers = parameters.length > 4 && parameters[4] == 1;
        this.durationSeconds = normalizeDuration(parameters.length > 5 ? parameters[5] : DEFAULT_DURATION_SECONDS);
        this.furniSource =
                WiredMovementPayloadGuard.furniSource(settings.furniSource(0, WiredSourceUtil.SOURCE_SELECTED));
        this.userSource = WiredMovementPayloadGuard.userSource(settings.userSource(0, WiredSourceUtil.SOURCE_TRIGGER));
        this.targetUserSource =
                WiredMovementPayloadGuard.userSource(settings.userSource(1, WiredSourceUtil.SOURCE_TRIGGER));
        if (!this.items.isEmpty() && this.furniSource == WiredSourceUtil.SOURCE_TRIGGER) {
            this.furniSource = WiredSourceUtil.SOURCE_SELECTED;
        }
        this.setDelay(delay);
        return true;
    }

    @Override
    public String getWiredData() {
        this.validateItems(this.items);
        JsonData data = new JsonData(
                this.visibility,
                this.opacity,
                this.easing,
                this.durationSeconds,
                this.furniSource,
                this.userSource,
                this.clickThrough,
                this.getDelay(),
                this.items.stream().map(HabboItem::getId).toList());
        data.targetUsers = this.targetUsers;
        data.targetUserSource = this.targetUserSource;
        return WiredManager.getGson().toJson(data);
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        reset();
        JsonData data = WiredMovementPayloadGuard.fromJson(set.getString("wired_data"), JsonData.class);
        if (data == null) {
            return;
        }
        this.targetUsers = data.targetUsers;
        this.targetUserSource = WiredMovementPayloadGuard.userSource(data.targetUserSource);

        this.visibility = normalizeVisibility(data.visibility);
        this.opacity = clamp(data.opacity, MINIMUM_OPACITY, MAXIMUM_OPACITY);
        this.easing = clamp(data.easing, 0, 4);
        this.durationSeconds = normalizeDuration(data.durationSeconds);
        this.furniSource = WiredMovementPayloadGuard.furniSource(data.furniSource);
        this.userSource = WiredMovementPayloadGuard.userSource(data.userSource);
        this.clickThrough = data.clickThrough;
        this.setDelay(WiredMovementPayloadGuard.delay(data.delay));
        if (data.itemIds != null) {
            for (Integer itemId : data.itemIds) {
                HabboItem item = itemId == null ? null : room.getHabboItem(itemId);
                if (item != null && this.items.size() < WiredManager.MAXIMUM_FURNI_SELECTION) {
                    this.items.add(item);
                }
            }
        }
        if (!this.items.isEmpty() && this.furniSource == WiredSourceUtil.SOURCE_TRIGGER) {
            this.furniSource = WiredSourceUtil.SOURCE_SELECTED;
        }
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        List<HabboItem> selected = currentTargets(room, this.items);
        message.appendBoolean(false);
        message.appendInt(WiredManager.MAXIMUM_FURNI_SELECTION);
        message.appendInt(selected.size());
        for (HabboItem item : selected) {
            message.appendInt(item.getId());
        }
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString("");
        message.appendInt(6);
        message.appendInt(this.visibility);
        message.appendInt(MAXIMUM_OPACITY - this.opacity);
        message.appendInt(this.clickThrough ? 1 : 0);
        message.appendInt(this.easing);
        message.appendInt(this.targetUsers ? 1 : 0);
        message.appendInt(this.durationSeconds);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(this.getDelay());
        message.appendInt(0);
    }

    @Override
    public WiredEffectType getType() {
        return type;
    }

    @Override
    public boolean requiresTriggeringUser() {
        return (this.visibility == VISIBILITY_SOURCE_USERS && this.userSource == WiredSourceUtil.SOURCE_TRIGGER)
                || (this.targetUsers && this.targetUserSource == WiredSourceUtil.SOURCE_TRIGGER);
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public void onPickUp() {
        reset();
    }

    private void applyToUsers(Room room, WiredContext context) {
        List<RoomUnit> users = WiredSourceUtil.resolveUsers(context, this.targetUserSource);
        if (users.isEmpty()) {
            return;
        }
        WiredUserTransparency transparency =
                new WiredUserTransparency(MAXIMUM_OPACITY - this.opacity, this.clickThrough, this.easing);
        WiredVisualizationBroadcaster.setUserTransparency(
                room,
                this.visibility == VISIBILITY_EVERYONE
                        ? null
                        : resolveRecipients(room, context).values(),
                users,
                transparency);
    }

    private Map<Integer, Habbo> resolveRecipients(Room room, WiredContext context) {
        Map<Integer, Habbo> recipients = new LinkedHashMap<>();
        for (RoomUnit unit : WiredSourceUtil.resolveUsers(context, this.userSource)) {
            Habbo habbo = room.getHabbo(unit);
            if (habbo != null && habbo.getHabboInfo() != null) {
                recipients.put(habbo.getHabboInfo().getId(), habbo);
            }
        }
        return recipients;
    }

    private static List<HabboItem> currentTargets(Room room, Collection<HabboItem> candidates) {
        if (room == null || candidates == null) {
            return List.of();
        }
        return candidates.stream()
                .filter(item ->
                        item != null && item.getRoomId() == room.getId() && room.getHabboItem(item.getId()) == item)
                .distinct()
                .sorted(java.util.Comparator.comparingInt(HabboItem::getId))
                .limit(WiredManager.MAXIMUM_FURNI_SELECTION)
                .toList();
    }

    private static boolean supportsOpacity(Habbo habbo) {
        return habbo != null
                && habbo.getClient() != null
                && habbo.getClient()
                        .supportsWiredFeature(
                                GameClient.WIRED_FEATURE_PROTOCOL_VERSION, GameClient.WIRED_FEATURE_OPACITY);
    }

    private void reset() {
        this.items.clear();
        this.visibility = VISIBILITY_SOURCE_USERS;
        this.opacity = MAXIMUM_OPACITY;
        this.easing = EASING_INSTANT;
        this.durationSeconds = DEFAULT_DURATION_SECONDS;
        this.furniSource = WiredSourceUtil.SOURCE_SELECTED;
        this.userSource = WiredSourceUtil.SOURCE_TRIGGER;
        this.clickThrough = false;
        this.targetUsers = false;
        this.targetUserSource = WiredSourceUtil.SOURCE_TRIGGER;
        this.setDelay(0);
    }

    @Override
    public int[] officialFurniSources() {
        return new int[] {this.furniSource};
    }

    @Override
    public int[] officialUserSources() {
        return new int[] {this.userSource, this.targetUserSource};
    }

    private static int normalizeVisibility(int value) {
        return value == VISIBILITY_EVERYONE ? VISIBILITY_EVERYONE : VISIBILITY_SOURCE_USERS;
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static int normalizeDuration(int value) {
        return value <= DEFAULT_DURATION_SECONDS
                ? DEFAULT_DURATION_SECONDS
                : clamp(value, MINIMUM_DURATION_SECONDS, MAXIMUM_DURATION_SECONDS);
    }

    static class JsonData {
        int visibility;
        int opacity = MAXIMUM_OPACITY;
        int easing;
        int durationSeconds = MINIMUM_DURATION_SECONDS;
        int furniSource = WiredSourceUtil.SOURCE_SELECTED;
        int userSource = WiredSourceUtil.SOURCE_TRIGGER;
        boolean clickThrough;
        int delay;
        List<Integer> itemIds;
        boolean targetUsers;
        int targetUserSource;

        JsonData() {}

        JsonData(
                int visibility,
                int opacity,
                int easing,
                int durationSeconds,
                int furniSource,
                int userSource,
                boolean clickThrough,
                int delay,
                List<Integer> itemIds) {
            this.visibility = visibility;
            this.opacity = opacity;
            this.easing = easing;
            this.durationSeconds = durationSeconds;
            this.furniSource = furniSource;
            this.userSource = userSource;
            this.clickThrough = clickThrough;
            this.delay = delay;
            this.itemIds = itemIds;
        }
    }
}
