package polaris.hotel.items.interactions.wired.effects;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.items.Item;
import polaris.hotel.items.interactions.InteractionWiredEffect;
import polaris.hotel.items.interactions.games.InteractionGameUpCounter;
import polaris.hotel.items.interactions.wired.WiredInputGuard;
import polaris.hotel.items.interactions.wired.WiredSettings;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.users.UserItem;
import polaris.hotel.wired.WiredEffectType;
import polaris.hotel.wired.core.WiredContext;
import polaris.hotel.wired.core.WiredManager;
import polaris.hotel.wired.core.WiredSourceUtil;
import polaris.messages.ServerMessage;
import polaris.messages.incoming.wired.WiredSaveException;

public class WiredEffectAdjustClock extends InteractionWiredEffect {
    private static final int OPERATOR_INCREASE = 0;
    private static final int OPERATOR_DECREASE = 1;
    private static final int OPERATOR_SET = 2;
    private static final int MAX_MINUTES = 99;
    private static final int MAX_HALF_SECOND_STEPS = 119;

    public static final WiredEffectType type = WiredEffectType.ADJUST_CLOCK;

    private final List<UserItem> items = new ArrayList<>();
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

        List<UserItem> effectiveItems = WiredSourceUtil.resolveItems(ctx, this.furniSource, this.items);

        if (this.furniSource == WiredSourceUtil.SOURCE_SELECTED) {
            this.items.removeIf(item ->
                    item == null || item.getRoomId() != this.getRoomId() || room.getUserItem(item.getId()) == null);
        }

        for (UserItem item : effectiveItems) {
            if (!(item instanceof InteractionGameUpCounter)) {
                continue;
            }

            ((InteractionGameUpCounter) item).adjustCounter(room, this.operator, this.minutes, this.halfSecondSteps);
        }
    }

    /** Whether this box sets the clock, rather than increasing or decreasing it. */
    public boolean isSet() {
        return this.operator == OPERATOR_SET;
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson()
                .toJson(new JsonData(
                        this.getDelay(),
                        this.items.stream().map(UserItem::getId).collect(Collectors.toList()),
                        this.operator,
                        this.furniSource,
                        this.minutes,
                        this.halfSecondSteps));
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
                    UserItem item = room.getUserItem(id);

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
        List<UserItem> itemsSnapshot = new ArrayList<>(this.items);
        itemsSnapshot.removeIf(
                item -> item == null || item.getRoomId() != this.getRoomId() || room.getUserItem(item.getId()) == null);

        this.items.clear();
        this.items.addAll(itemsSnapshot);

        message.appendBoolean(false);
        message.appendInt(WiredManager.MAXIMUM_FURNI_SELECTION);
        message.appendInt(itemsSnapshot.size());
        for (UserItem item : itemsSnapshot) {
            message.appendInt(item.getId());
        }

        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString("");
        message.appendInt(4);
        message.appendInt(this.halfSecondSteps / 2);
        message.appendInt(this.minutes);
        message.appendInt(this.halfSecondSteps % 2);
        message.appendInt(this.operator);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(this.getDelay());
        message.appendInt(0);
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        int[] params = settings.getIntParams();

        // Official layout: [whole seconds, minutes, half a second (0/1), operator]; the furni source is
        // the official source slot.
        if (params.length < 4) {
            throw new WiredSaveException("Invalid data");
        }

        this.halfSecondSteps = this.normalizeHalfSecondSteps(Math.max(0, params[0]) * 2 + (params[2] == 1 ? 1 : 0));
        this.minutes = this.normalizeMinutes(params[1]);
        this.operator = this.normalizeOperator(params[3]);
        this.furniSource = settings.furniSource(0, WiredSourceUtil.SOURCE_TRIGGER);

        int delay = settings.getDelay();
        if (delay > Emulator.getConfig().getInt("hotel.wired.max_delay", WiredInputGuard.DEFAULT_MAX_DELAY)) {
            throw new WiredSaveException("Delay too long");
        }

        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());
        if (room == null) {
            throw new WiredSaveException("Room not found");
        }

        if (settings.getFurniIds().length > Emulator.getConfig().getInt("hotel.wired.furni.selection.count")) {
            throw new WiredSaveException("Too many furni selected");
        }

        List<UserItem> newItems = new ArrayList<>();
        for (int itemId : settings.getFurniIds()) {
            UserItem item = room.getUserItem(itemId);

            if (item == null) {
                throw new WiredSaveException(String.format("Item %s not found", itemId));
            }

            if (!(item instanceof InteractionGameUpCounter)) {
                throw new WiredSaveException("wiredfurni.error.require_counter_furni");
            }

            newItems.add(item);
        }

        // Picked clocks with the dropdown left on "triggering furni" resolved the trigger item at
        // run time, so the validated clocks were stored and never adjusted.
        if (!newItems.isEmpty() && this.furniSource == WiredSourceUtil.SOURCE_TRIGGER) {
            this.furniSource = WiredSourceUtil.SOURCE_SELECTED;
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

        public JsonData(
                int delay, List<Integer> itemIds, int operator, int furniSource, int minutes, int halfSecondSteps) {
            this.delay = delay;
            this.itemIds = itemIds;
            this.operator = operator;
            this.furniSource = furniSource;
            this.minutes = minutes;
            this.halfSecondSteps = halfSecondSteps;
        }
    }
}
