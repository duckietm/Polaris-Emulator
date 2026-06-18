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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class WiredEffectSetAltitude extends InteractionWiredEffect {
    private static final Pattern ALTITUDE_PATTERN = Pattern.compile("^\\d+(\\.\\d{1,2})?$");

    private static final int OPERATOR_INCREASE = 0;
    private static final int OPERATOR_DECREASE = 1;
    private static final int OPERATOR_SET = 2;

    public static final WiredEffectType type = WiredEffectType.SET_ALTITUDE;

    private final List<HabboItem> items = new ArrayList<>();
    private int operator = OPERATOR_SET;
    private double altitude = 0.0D;
    private int furniSource = WiredSourceUtil.SOURCE_TRIGGER;

    public WiredEffectSetAltitude(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectSetAltitude(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
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
            if (item == null || item.getRoomId() != this.getRoomId()) {
                continue;
            }

            RoomTile tile = room.getLayout().getTile(item.getX(), item.getY());
            if (tile == null) {
                continue;
            }

            double nextAltitude = this.computeAltitude(item.getZ());
            WiredMoveCarryHelper.moveFurni(room, this, item, tile, item.getRotation(), nextAltitude, null, true, ctx);
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
                this.formatAltitude(this.altitude),
                this.furniSource
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
            this.altitude = this.parseAltitudeOrDefault(jsonData.altitude);
            this.furniSource = WiredUtilityPayloadGuard.furniSource(jsonData.furniSource);

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
        this.altitude = 0.0D;
        this.furniSource = WiredSourceUtil.SOURCE_TRIGGER;
        this.setDelay(0);
    }

    @Override
    public void onPickUp() {
        this.items.clear();
        this.operator = OPERATOR_SET;
        this.altitude = 0.0D;
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
        message.appendString(this.formatAltitude(this.altitude));
        message.appendInt(2);
        message.appendInt(this.operator);
        message.appendInt(this.furniSource);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(this.getDelay());
        message.appendInt(0);
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        int[] params = settings.getIntParams();
        this.operator = (params.length > 0) ? this.normalizeOperator(params[0]) : OPERATOR_SET;
        this.furniSource = (params.length > 1) ? params[1] : WiredSourceUtil.SOURCE_TRIGGER;

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

        this.altitude = this.parseAltitude(settings.getStringParam());
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

    private double computeAltitude(double currentAltitude) {
        double nextAltitude;

        switch (this.operator) {
            case OPERATOR_INCREASE:
                nextAltitude = currentAltitude + this.altitude;
                break;
            case OPERATOR_DECREASE:
                nextAltitude = currentAltitude - this.altitude;
                break;
            case OPERATOR_SET:
            default:
                nextAltitude = this.altitude;
                break;
        }

        return this.normalizeAltitude(nextAltitude);
    }

    private double parseAltitude(String value) throws WiredSaveException {
        String normalized = (value != null) ? value.trim() : "";

        if (normalized.isEmpty()) {
            return 0.0D;
        }

        if (!ALTITUDE_PATTERN.matcher(normalized).matches()) {
            throw new WiredSaveException("Invalid altitude value");
        }

        try {
            return this.normalizeAltitude(new BigDecimal(normalized).doubleValue());
        } catch (NumberFormatException exception) {
            throw new WiredSaveException("Invalid altitude value");
        }
    }

    private double parseAltitudeOrDefault(String value) {
        try {
            return this.parseAltitude(value);
        } catch (WiredSaveException exception) {
            return 0.0D;
        }
    }

    private double normalizeAltitude(double value) {
        double clampedValue = Math.max(0.0D, Math.min(Room.MAXIMUM_FURNI_HEIGHT, value));
        return BigDecimal.valueOf(clampedValue).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private String formatAltitude(double value) {
        BigDecimal decimal = BigDecimal.valueOf(this.normalizeAltitude(value)).stripTrailingZeros();
        return (decimal.scale() < 0 ? decimal.setScale(0, RoundingMode.DOWN) : decimal).toPlainString();
    }

    static class JsonData {
        int delay;
        List<Integer> itemIds;
        int operator;
        String altitude;
        int furniSource;

        public JsonData(int delay, List<Integer> itemIds, int operator, String altitude, int furniSource) {
            this.delay = delay;
            this.itemIds = itemIds;
            this.operator = operator;
            this.altitude = altitude;
            this.furniSource = furniSource;
        }
    }
}
