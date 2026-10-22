package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.WiredPlatform;
import com.eu.habbo.core.ConfigurationManager;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredInputGuard;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.rooms.WiredHighlight;
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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Official set furni highlight (62). Int params: {@code [audience (1 everyone), type (0 outline, 1 soft
 * glow, 2 darken, 3 colour tint, 4 brighten, 5 neon), strength 0..100, colour 0xRRGGBB, animation (0
 * none, 1 pulse), easing, target users]}. Furni source slot 0 is the target furni; user slot 0 the
 * audience, slot 1 the target users, highlighted too when target users is on. A strength of 0 clears
 * the highlight. Brighten keeps the half strength its editor saves; the client is sent the whole.
 */
public class WiredEffectSetHighlight extends InteractionWiredEffect implements WiredOfficialSources {
    public static final WiredEffectType type = WiredEffectType.SET_HIGHLIGHT;

    static final int AUDIENCE_SOURCE_USERS = 0;
    static final int AUDIENCE_EVERYONE = 1;
    static final int DEFAULT_STRENGTH = 50;
    static final int PARAMETER_COUNT = 7;

    private final List<HabboItem> items = new ArrayList<>();
    private int audience = AUDIENCE_SOURCE_USERS;
    private WiredHighlight highlight = defaultHighlight();
    private boolean targetUsers;
    private int furniSource = WiredSourceUtil.SOURCE_SELECTED;
    private int userSource = WiredSourceUtil.SOURCE_TRIGGER;
    private int targetUserSource = WiredSourceUtil.SOURCE_TRIGGER;

    public WiredEffectSetHighlight(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectSetHighlight(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void execute(WiredContext context) {
        if (context == null || context.room() == null) {
            return;
        }

        Room room = context.room();
        List<HabboItem> furni =
                currentTargets(room, WiredSourceUtil.resolveItems(context, this.furniSource, this.items));
        List<RoomUnit> users =
                this.targetUsers ? WiredSourceUtil.resolveUsers(context, this.targetUserSource) : List.of();
        if (furni.isEmpty() && users.isEmpty()) {
            return;
        }

        Collection<Habbo> viewers = this.audience == AUDIENCE_EVERYONE
                ? null
                : resolveViewers(room, context).values();
        WiredVisualizationBroadcaster.highlight(room, viewers, furni, users, this.highlight);
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        Room room = getRoom();
        int[] parameters = settings == null ? null : settings.getIntParams();
        int[] selectedIds = settings == null || settings.getFurniIds() == null ? new int[0] : settings.getFurniIds();
        if (room == null || parameters == null || parameters.length < 6) {
            throw new WiredSaveException("Invalid highlight effect data");
        }
        if (selectedIds.length > WiredManager.MAXIMUM_FURNI_SELECTION) {
            throw new WiredSaveException("Too many furni selected");
        }

        int delay = settings.getDelay();
        ConfigurationManager configuration = WiredPlatform.configuration();
        int maximumDelay = configuration == null
                ? WiredInputGuard.DEFAULT_MAX_DELAY
                : configuration.getInt("hotel.wired.max_delay", WiredInputGuard.DEFAULT_MAX_DELAY);
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

        this.audience = normalizeAudience(parameters[0]);
        this.highlight = new WiredHighlight(parameters[1], parameters[2], parameters[3], parameters[4], parameters[5]);
        this.targetUsers = parameters.length > 6 && parameters[6] == 1;
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
        JsonData data = new JsonData();
        data.audience = this.audience;
        data.highlightType = this.highlight.type();
        data.strength = this.highlight.strength();
        data.color = this.highlight.color();
        data.animation = this.highlight.animation();
        data.easing = this.highlight.easing();
        data.targetUsers = this.targetUsers;
        data.furniSource = this.furniSource;
        data.userSource = this.userSource;
        data.targetUserSource = this.targetUserSource;
        data.delay = this.getDelay();
        data.itemIds = this.items.stream().map(HabboItem::getId).toList();
        return WiredManager.getGson().toJson(data);
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        reset();
        JsonData data = WiredMovementPayloadGuard.fromJson(set.getString("wired_data"), JsonData.class);
        if (data == null) {
            return;
        }

        this.audience = normalizeAudience(data.audience);
        this.highlight = new WiredHighlight(data.highlightType, data.strength, data.color, data.animation, data.easing);
        this.targetUsers = data.targetUsers;
        this.furniSource = WiredMovementPayloadGuard.furniSource(data.furniSource);
        this.userSource = WiredMovementPayloadGuard.userSource(data.userSource);
        this.targetUserSource = WiredMovementPayloadGuard.userSource(data.targetUserSource);
        this.setDelay(WiredMovementPayloadGuard.delay(data.delay));
        if (data.itemIds != null && room != null) {
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
        message.appendInt(PARAMETER_COUNT);
        message.appendInt(this.audience);
        message.appendInt(this.highlight.type());
        message.appendInt(this.highlight.strength());
        message.appendInt(this.highlight.color());
        message.appendInt(this.highlight.animation());
        message.appendInt(this.highlight.easing());
        message.appendInt(this.targetUsers ? 1 : 0);
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
        return this.audience == AUDIENCE_SOURCE_USERS && this.userSource == WiredSourceUtil.SOURCE_TRIGGER;
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

    @Override
    public int[] officialFurniSources() {
        return new int[] {this.furniSource};
    }

    @Override
    public int[] officialUserSources() {
        return new int[] {this.userSource, this.targetUserSource};
    }

    /** The highlight this box applies, after the editor rules. */
    WiredHighlight highlight() {
        return this.highlight;
    }

    private Map<Integer, Habbo> resolveViewers(Room room, WiredContext context) {
        Map<Integer, Habbo> viewers = new LinkedHashMap<>();
        for (RoomUnit unit : WiredSourceUtil.resolveUsers(context, this.userSource)) {
            Habbo habbo = room.getHabbo(unit);
            if (habbo != null && habbo.getHabboInfo() != null) {
                viewers.put(habbo.getHabboInfo().getId(), habbo);
            }
        }
        return viewers;
    }

    private static List<HabboItem> currentTargets(Room room, Collection<HabboItem> candidates) {
        if (room == null || candidates == null) {
            return List.of();
        }
        return candidates.stream()
                .filter(item ->
                        item != null && item.getRoomId() == room.getId() && room.getHabboItem(item.getId()) == item)
                .distinct()
                .sorted(Comparator.comparingInt(HabboItem::getId))
                .limit(WiredManager.MAXIMUM_FURNI_SELECTION)
                .toList();
    }

    private void reset() {
        this.items.clear();
        this.audience = AUDIENCE_SOURCE_USERS;
        this.highlight = defaultHighlight();
        this.targetUsers = false;
        this.furniSource = WiredSourceUtil.SOURCE_SELECTED;
        this.userSource = WiredSourceUtil.SOURCE_TRIGGER;
        this.targetUserSource = WiredSourceUtil.SOURCE_TRIGGER;
        this.setDelay(0);
    }

    private static WiredHighlight defaultHighlight() {
        return new WiredHighlight(
                WiredHighlight.TYPE_OUTLINE, DEFAULT_STRENGTH, WiredHighlight.WHITE, WiredHighlight.ANIMATION_NONE, 0);
    }

    private static int normalizeAudience(int value) {
        return value == AUDIENCE_EVERYONE ? AUDIENCE_EVERYONE : AUDIENCE_SOURCE_USERS;
    }

    static class JsonData {
        int audience;
        int highlightType;
        int strength = DEFAULT_STRENGTH;
        int color = WiredHighlight.WHITE;
        int animation;
        int easing;
        boolean targetUsers;
        int furniSource = WiredSourceUtil.SOURCE_SELECTED;
        int userSource = WiredSourceUtil.SOURCE_TRIGGER;
        int targetUserSource = WiredSourceUtil.SOURCE_TRIGGER;
        int delay;
        List<Integer> itemIds;
    }
}
