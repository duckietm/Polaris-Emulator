package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.games.InteractionGameUpCounter;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class WiredEffectAdjustClock extends InteractionWiredEffect {
    private static final int OPERATOR_INCREASE = 0;
    private static final int OPERATOR_DECREASE = 1;
    private static final int OPERATOR_SET = 2;
    private static final int MAX_MINUTES = 99;
    private static final int MAX_HALF_SECOND_STEPS = 119;

    public static final WiredEffectType type = WiredEffectType.ADJUST_CLOCK;

    private final List<HabboItem> items = new ArrayList<>();
    private int operator = OPERATOR_SET;
    private int furniSource = WiredSourceUtil.SOURCE_TRIGGER;
    private int minutes = 0;
    private int halfSecondSteps = 0;

    public WiredEffectAdjustClock(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectAdjustClock(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void execute(WiredContext ctx) {
        Room room = ctx.room();

        if (room == null) {
            return;
        }

        List<HabboItem> effectiveItems = WiredSourceUtil.resolveItems(ctx, this.furniSource, this.items);

        if (this.furniSource == WiredSourceUtil.SOURCE_SELECTED) {
            this.items.removeIf(item -> item == null
                    || item.getRoomId() != this.getRoomId()
                    || room.getHabboItem(item.getId()) == null);
        }

        for (HabboItem item : effectiveItems) {
            if (!(item instanceof InteractionGameUpCounter)) {
                continue;
            }

            ((InteractionGameUpCounter) item).adjustCounter(room, this.operator, this.minutes, this.halfSecondSteps);
        }
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
                this.operator,
                this.furniSource,
                this.minutes,
                this.halfSecondSteps
        ));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.items.clear();
        String wiredData = set.getString("wired_data");

        JsonData jsonData = WiredUtilityPayloadGuard.fromJson(wiredData, JsonData.class);
        if (jsonData != null) {
            this.setDelay(WiredUtilityPayloadGuard.delay(jsonData.delay));
            this.operator = this.normalizeOperator(jsonData.operator);
            this.furniSource = WiredUtilityPayloadGuard.furniSource(jsonData.furniSource);
            this.minutes = this.normalizeMinutes(jsonData.minutes);
            this.halfSecondSteps = this.normalizeHalfSecondSteps(jsonData.halfSecondSteps);

            if (jsonData.itemIds != null) {
                for (Integer id : jsonData.itemIds) {
                    if (id == null) continue;
                    HabboItem item = room.getHabboItem(id);

                    if (item != null) {
                        this.items.add(item);
                    }
                }
            }

            return;
        }

        this.operator = OPERATOR_SET;
        this.furniSource = WiredSourceUtil.SOURCE_TRIGGER;
        this.minutes = 0;
        this.halfSecondSteps = 0;
        this.setDelay(0);
    }

    @Override
    public void onPickUp() {
        this.items.clear();
        this.operator = OPERATOR_SET;
        this.furniSource = WiredSourceUtil.SOURCE_TRIGGER;
        this.minutes = 0;
        this.halfSecondSteps = 0;
        this.setDelay(0);
    }

    @Override
    public WiredEffectType getType() {
        return type;
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        List<HabboItem> itemsSnapshot = new ArrayList<>(this.items);
        itemsSnapshot.removeIf(item -> item == null
                || item.getRoomId() != this.getRoomId()
                || room.getHabboItem(item.getId()) == null);

        this.items.clear();
        this.items.addAll(itemsSnapshot);

        message.appendBoolean(false);
        message.appendInt(WiredManager.MAXIMUM_FURNI_SELECTION);
        message.appendInt(itemsSnapshot.size());
        for (HabboItem item : itemsSnapshot) {
            message.appendInt(item.getId());
        }

        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString("");
        message.appendInt(4);
        message.appendInt(this.operator);
        message.appendInt(this.furniSource);
        message.appendInt(this.minutes);
        message.appendInt(this.halfSecondSteps);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(this.getDelay());
        message.appendInt(0);
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        int[] params = settings.getIntParams();

        if (params.length < 4) {
            throw new WiredSaveException("Invalid data");
        }

        this.operator = this.normalizeOperator(params[0]);
        this.furniSource = params[1];
        this.minutes = this.normalizeMinutes(params[2]);
        this.halfSecondSteps = this.normalizeHalfSecondSteps(params[3]);

        int delay = settings.getDelay();
        if (delay > Emulator.getConfig().getInt("hotel.wired.max_delay", 20)) {
            throw new WiredSaveException("Delay too long");
        }

        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());
        if (room == null) {
            throw new WiredSaveException("Room not found");
        }

        if (settings.getFurniIds().length > Emulator.getConfig().getInt("hotel.wired.furni.selection.count")) {
            throw new WiredSaveException("Too many furni selected");
        }

        List<HabboItem> newItems = new ArrayList<>();
        for (int itemId : settings.getFurniIds()) {
            HabboItem item = room.getHabboItem(itemId);

            if (item == null) {
                throw new WiredSaveException(String.format("Item %s not found", itemId));
            }

            if (!(item instanceof InteractionGameUpCounter)) {
                throw new WiredSaveException("wiredfurni.error.require_counter_furni");
            }

            newItems.add(item);
        }

        this.items.clear();
        this.items.addAll(newItems);
        this.setDelay(delay);

        return true;
    }

    private int normalizeOperator(int value) {
        if (value < OPERATOR_INCREASE || value > OPERATOR_SET) {
            return OPERATOR_SET;
        }

        return value;
    }

    private int normalizeMinutes(int value) {
        return Math.max(0, Math.min(MAX_MINUTES, value));
    }

    private int normalizeHalfSecondSteps(int value) {
        return Math.max(0, Math.min(MAX_HALF_SECOND_STEPS, value));
    }

    static class JsonData {
        int delay;
        List<Integer> itemIds;
        int operator;
        int furniSource;
        int minutes;
        int halfSecondSteps;

        public JsonData(int delay, List<Integer> itemIds, int operator, int furniSource, int minutes, int halfSecondSteps) {
            this.delay = delay;
            this.itemIds = itemIds;
            this.operator = operator;
            this.furniSource = furniSource;
            this.minutes = minutes;
            this.halfSecondSteps = halfSecondSteps;
        }
    }
}
