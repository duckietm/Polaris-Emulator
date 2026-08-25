package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredMoveCarryHelper;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class WiredEffectRelativeMove extends InteractionWiredEffect {
    private static final int HORIZONTAL_NEGATIVE = 0;
    private static final int HORIZONTAL_POSITIVE = 1;
    private static final int VERTICAL_NEGATIVE = 0;
    private static final int VERTICAL_POSITIVE = 1;
    private static final int MAX_DISTANCE = 20;

    public static final WiredEffectType type = WiredEffectType.RELATIVE_MOVE;

    private final List<HabboItem> items = new ArrayList<>();
    private int horizontalDirection = HORIZONTAL_POSITIVE;
    private int horizontalDistance = 0;
    private int verticalDirection = VERTICAL_POSITIVE;
    private int verticalDistance = 0;
    private int furniSource = WiredSourceUtil.SOURCE_TRIGGER;

    public WiredEffectRelativeMove(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectRelativeMove(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void execute(WiredContext ctx) {
        Room room = ctx.room();
        if (room == null || room.getLayout() == null) {
            return;
        }

        List<HabboItem> effectiveItems = WiredSourceUtil.resolveItems(ctx, this.furniSource, this.items);

        if (this.furniSource == WiredSourceUtil.SOURCE_SELECTED) {
            this.items.removeIf(item -> item == null
                    || item.getRoomId() != this.getRoomId()
                    || room.getHabboItem(item.getId()) == null);
        }

        int deltaX = this.getHorizontalOffset();
        int deltaY = this.getVerticalOffset();

        if (deltaX == 0 && deltaY == 0) {
            return;
        }

        for (HabboItem item : effectiveItems) {
            if (item == null || item.getRoomId() != this.getRoomId()) {
                continue;
            }

            short targetX = (short) (item.getX() + deltaX);
            short targetY = (short) (item.getY() + deltaY);

            RoomTile targetTile = room.getLayout().getTile(targetX, targetY);
            if (targetTile == null) {
                continue;
            }

            WiredMoveCarryHelper.moveFurni(room, this, item, targetTile, item.getRotation(), null, true, ctx);
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
                this.horizontalDirection,
                this.horizontalDistance,
                this.verticalDirection,
                this.verticalDistance,
                this.furniSource
        ));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.items.clear();
        String wiredData = set.getString("wired_data");

        if (wiredData != null && wiredData.startsWith("{")) {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            this.setDelay(data.delay);
            this.horizontalDirection = this.normalizeBinary(data.horizontalDirection, HORIZONTAL_POSITIVE);
            this.horizontalDistance = this.normalizeDistance(data.horizontalDistance);
            this.verticalDirection = this.normalizeBinary(data.verticalDirection, VERTICAL_POSITIVE);
            this.verticalDistance = this.normalizeDistance(data.verticalDistance);
            this.furniSource = data.furniSource;

            if (data.itemIds != null) {
                for (Integer id : data.itemIds) {
                    HabboItem item = room.getHabboItem(id);

                    if (item != null) {
                        this.items.add(item);
                    }
                }
            }

            return;
        }

        this.horizontalDirection = HORIZONTAL_POSITIVE;
        this.horizontalDistance = 0;
        this.verticalDirection = VERTICAL_POSITIVE;
        this.verticalDistance = 0;
        this.furniSource = WiredSourceUtil.SOURCE_TRIGGER;
        this.setDelay(0);
    }

    @Override
    public void onPickUp() {
        this.items.clear();
        this.horizontalDirection = HORIZONTAL_POSITIVE;
        this.horizontalDistance = 0;
        this.verticalDirection = VERTICAL_POSITIVE;
        this.verticalDistance = 0;
        this.furniSource = WiredSourceUtil.SOURCE_TRIGGER;
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
        message.appendInt(5);
        message.appendInt(this.horizontalDirection);
        message.appendInt(this.horizontalDistance);
        message.appendInt(this.verticalDirection);
        message.appendInt(this.verticalDistance);
        message.appendInt(this.furniSource);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(this.getDelay());
        message.appendInt(0);
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        int[] params = settings.getIntParams();

        if (params.length < 5) {
            throw new WiredSaveException("Invalid data");
        }

        this.horizontalDirection = this.normalizeBinary(params[0], HORIZONTAL_POSITIVE);
        this.horizontalDistance = this.normalizeDistance(params[1]);
        this.verticalDirection = this.normalizeBinary(params[2], VERTICAL_POSITIVE);
        this.verticalDistance = this.normalizeDistance(params[3]);
        this.furniSource = params[4];

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

            newItems.add(item);
        }

        this.items.clear();
        this.items.addAll(newItems);
        this.setDelay(delay);

        return true;
    }

    private int getHorizontalOffset() {
        if (this.horizontalDistance <= 0) {
            return 0;
        }

        return (this.horizontalDirection == HORIZONTAL_NEGATIVE) ? -this.horizontalDistance : this.horizontalDistance;
    }

    private int getVerticalOffset() {
        if (this.verticalDistance <= 0) {
            return 0;
        }

        return (this.verticalDirection == VERTICAL_NEGATIVE) ? -this.verticalDistance : this.verticalDistance;
    }

    private int normalizeBinary(int value, int fallback) {
        if (value == 0 || value == 1) {
            return value;
        }

        return fallback;
    }

    private int normalizeDistance(int value) {
        return Math.max(0, Math.min(MAX_DISTANCE, value));
    }

    static class JsonData {
        int delay;
        List<Integer> itemIds;
        int horizontalDirection;
        int horizontalDistance;
        int verticalDirection;
        int verticalDistance;
        int furniSource;

        public JsonData(int delay, List<Integer> itemIds, int horizontalDirection, int horizontalDistance, int verticalDirection, int verticalDistance, int furniSource) {
            this.delay = delay;
            this.itemIds = itemIds;
            this.horizontalDirection = horizontalDirection;
            this.horizontalDistance = horizontalDistance;
            this.verticalDirection = verticalDirection;
            this.verticalDistance = verticalDistance;
            this.furniSource = furniSource;
        }
    }
}
