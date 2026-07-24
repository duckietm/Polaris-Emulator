package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredInternalVariableSupport;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;
import com.eu.habbo.messages.outgoing.rooms.items.WiredFurniOpacityComposer;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Applies a transient opacity and click-through state to furniture selected by
 * the current Wired source. The state belongs to the receiving client, just
 * like the original Wired opacity effect.
 */
public class WiredEffectFurniOpacity extends InteractionWiredEffect {
    public static final WiredEffectType type = WiredEffectType.FURNI_OPACITY;

    private static final int VISIBILITY_EVERYONE = 0;
    private static final int VISIBILITY_TRIGGERING_USER = 1;
    private static final int EASING_INSTANT = 0;
    private static final int EASING_MAX = 4;

    private final List<HabboItem> items = new ArrayList<>();
    private int opacity = 100;
    private int visibility = VISIBILITY_EVERYONE;
    private boolean clickThrough;
    private int easing = EASING_INSTANT;
    private int furniSource = WiredSourceUtil.SOURCE_TRIGGER;

    public WiredEffectFurniOpacity(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectFurniOpacity(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void execute(WiredContext ctx) {
        Room room = ctx.room();
        if (room == null) return;

        List<HabboItem> effectiveItems = WiredSourceUtil.resolveItems(ctx, this.furniSource, this.items)
                .stream()
                .filter(item -> item != null && item.getRoomId() == this.getRoomId())
                .collect(Collectors.toList());

        if (this.furniSource == WiredSourceUtil.SOURCE_SELECTED) {
            this.items.removeIf(item -> item == null || item.getRoomId() != this.getRoomId() || room.getHabboItem(item.getId()) == null);
        }

        if (effectiveItems.isEmpty()) return;

        if (this.visibility == VISIBILITY_TRIGGERING_USER) {
            WiredFurniOpacityComposer composer = new WiredFurniOpacityComposer(effectiveItems, this.opacity, this.clickThrough, this.easing);
            Habbo actor = ctx.actor().map(room::getHabbo).orElse(null);

            if (actor != null && actor.getClient() != null) {
                actor.getClient().sendResponse(composer.compose());
            }

            return;
        }

        for (HabboItem item : effectiveItems) {
            WiredInternalVariableSupport.setFurniOpacity(room, item, this.opacity);
        }

        room.sendComposer(new WiredFurniOpacityComposer(effectiveItems, this.opacity, this.clickThrough, this.easing).compose());
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(
                this.getDelay(),
                this.items.stream().map(HabboItem::getId).collect(Collectors.toList()),
                this.opacity,
                this.visibility,
                this.clickThrough,
                this.easing,
                this.furniSource
        ));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.items.clear();

        JsonData data = WiredUtilityPayloadGuard.fromJson(set.getString("wired_data"), JsonData.class);
        if (data == null) {
            this.onPickUp();
            return;
        }

        this.setDelay(WiredUtilityPayloadGuard.delay(data.delay));
        this.opacity = normalizeOpacity(data.opacity);
        this.visibility = normalizeVisibility(data.visibility);
        this.clickThrough = data.clickThrough;
        this.easing = normalizeEasing(data.easing);
        this.furniSource = WiredUtilityPayloadGuard.furniSource(data.furniSource);

        if (data.itemIds != null) {
            for (Integer itemId : data.itemIds) {
                if (itemId == null) continue;

                HabboItem item = room.getHabboItem(itemId);
                if (item != null) this.items.add(item);
            }
        }
    }

    @Override
    public void onPickUp() {
        this.items.clear();
        this.opacity = 100;
        this.visibility = VISIBILITY_EVERYONE;
        this.clickThrough = false;
        this.easing = EASING_INSTANT;
        this.furniSource = WiredSourceUtil.SOURCE_TRIGGER;
        this.setDelay(0);
    }

    @Override
    public WiredEffectType getType() {
        return type;
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        List<HabboItem> validItems = this.items.stream()
                .filter(item -> item != null && item.getRoomId() == this.getRoomId() && room.getHabboItem(item.getId()) != null)
                .collect(Collectors.toList());

        this.items.clear();
        this.items.addAll(validItems);

        message.appendBoolean(false);
        message.appendInt(WiredManager.MAXIMUM_FURNI_SELECTION);
        message.appendInt(validItems.size());
        for (HabboItem item : validItems) {
            message.appendInt(item.getId());
        }
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString("");
        message.appendInt(5);
        message.appendInt(this.opacity);
        message.appendInt(this.visibility);
        message.appendInt(this.clickThrough ? 1 : 0);
        message.appendInt(this.easing);
        message.appendInt(this.furniSource);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(this.getDelay());
        message.appendInt(0);
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        int[] params = settings.getIntParams();
        if (params.length < 5) throw new WiredSaveException("Invalid opacity Wired settings");

        int delay = settings.getDelay();
        if (delay > Emulator.getConfig().getInt("hotel.wired.max_delay", 20)) {
            throw new WiredSaveException("Delay too long");
        }

        if (settings.getFurniIds().length > Emulator.getConfig().getInt("hotel.wired.furni.selection.count")) {
            throw new WiredSaveException("Too many furni selected");
        }

        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());
        if (room == null) throw new WiredSaveException("Room not found");

        List<HabboItem> newItems = new ArrayList<>();
        for (int itemId : settings.getFurniIds()) {
            HabboItem item = room.getHabboItem(itemId);
            if (item == null) throw new WiredSaveException(String.format("Item %s not found", itemId));
            newItems.add(item);
        }

        this.opacity = normalizeOpacity(params[0]);
        this.visibility = normalizeVisibility(params[1]);
        this.clickThrough = params[2] == 1;
        this.easing = normalizeEasing(params[3]);
        this.furniSource = WiredUtilityPayloadGuard.furniSource(params[4]);
        if (!newItems.isEmpty() && this.furniSource == WiredSourceUtil.SOURCE_TRIGGER) {
            this.furniSource = WiredSourceUtil.SOURCE_SELECTED;
        }
        this.items.clear();
        if (this.furniSource == WiredSourceUtil.SOURCE_SELECTED) this.items.addAll(newItems);
        this.setDelay(delay);

        return true;
    }

    private int normalizeOpacity(int value) {
        return Math.max(0, Math.min(100, value));
    }

    private int normalizeVisibility(int value) {
        return value == VISIBILITY_TRIGGERING_USER ? VISIBILITY_TRIGGERING_USER : VISIBILITY_EVERYONE;
    }

    private int normalizeEasing(int value) {
        return Math.max(EASING_INSTANT, Math.min(EASING_MAX, value));
    }

    static class JsonData {
        int delay;
        List<Integer> itemIds;
        int opacity;
        int visibility;
        boolean clickThrough;
        int easing;
        int furniSource;

        JsonData(int delay, List<Integer> itemIds, int opacity, int visibility, boolean clickThrough, int easing, int furniSource) {
            this.delay = delay;
            this.itemIds = itemIds;
            this.opacity = opacity;
            this.visibility = visibility;
            this.clickThrough = clickThrough;
            this.easing = easing;
            this.furniSource = furniSource;
        }
    }
}
