package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.games.InteractionGameTimer;
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

public class WiredEffectControlClock extends InteractionWiredEffect {
    private static final int ACTION_START = 0;
    private static final int ACTION_STOP = 1;
    private static final int ACTION_RESET = 2;
    private static final int ACTION_PAUSE = 3;
    private static final int ACTION_RESUME = 4;

    public static final WiredEffectType type = WiredEffectType.CONTROL_CLOCK;

    private final List<HabboItem> items = new ArrayList<>();
    private int action = ACTION_START;
    private int furniSource = WiredSourceUtil.SOURCE_TRIGGER;

    public WiredEffectControlClock(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectControlClock(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
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
            if (!(item instanceof InteractionGameTimer)) {
                continue;
            }

            if (item instanceof InteractionGameUpCounter) {
                this.controlUpCounter((InteractionGameUpCounter) item, room);
                continue;
            }

            this.controlGameTimer((InteractionGameTimer) item, room);
        }
    }

    private void controlUpCounter(InteractionGameUpCounter counter, Room room) {
        switch (this.action) {
            case ACTION_START:
                counter.restartFromZero(room);
                break;
            case ACTION_STOP:
                counter.stopCounter(room);
                break;
            case ACTION_RESET:
                counter.resetCounter(room);
                break;
            case ACTION_PAUSE:
                counter.pauseCounter(room);
                break;
            case ACTION_RESUME:
                counter.resumeCounter(room);
                break;
        }
    }

    private void controlGameTimer(InteractionGameTimer timer, Room room) {
        switch (this.action) {
            case ACTION_START:
                timer.startTimer(room);
                break;
            case ACTION_STOP:
                this.stopGameTimer(timer, room, false);
                break;
            case ACTION_RESET:
                this.stopGameTimer(timer, room, true);
                break;
            case ACTION_PAUSE:
                timer.pauseTimer(room);
                break;
            case ACTION_RESUME:
                timer.resumeTimer(room);
                break;
        }
    }

    private void stopGameTimer(InteractionGameTimer timer, Room room, boolean resetTime) {
        boolean wasActive = timer.isRunning() || timer.isPaused();

        timer.endGame(room);

        if (resetTime) {
            timer.setTimeNow(timer.getBaseTime());
            timer.setExtradata(timer.getTimeNow() + "\t" + timer.getBaseTime());
        }

        room.updateItem(timer);
        timer.needsUpdate(true);

        if (wasActive) {
            WiredManager.triggerGameEnds(room);
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
                this.action,
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
            this.action = this.normalizeAction(data.action);
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

        this.action = ACTION_START;
        this.furniSource = WiredSourceUtil.SOURCE_TRIGGER;
        this.setDelay(0);
    }

    @Override
    public void onPickUp() {
        this.items.clear();
        this.action = ACTION_START;
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
        message.appendInt(2);
        message.appendInt(this.action);
        message.appendInt(this.furniSource);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(this.getDelay());
        message.appendInt(0);
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        int[] params = settings.getIntParams();

        if (params.length < 2) {
            throw new WiredSaveException("Invalid data");
        }

        this.action = this.normalizeAction(params[0]);
        this.furniSource = params[1];

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

            if (!(item instanceof InteractionGameTimer)) {
                throw new WiredSaveException("wiredfurni.error.require_counter_furni");
            }

            newItems.add(item);
        }

        this.items.clear();
        this.items.addAll(newItems);
        this.setDelay(delay);

        return true;
    }

    private int normalizeAction(int value) {
        if (value < ACTION_START || value > ACTION_RESUME) {
            return ACTION_START;
        }

        return value;
    }

    static class JsonData {
        int delay;
        List<Integer> itemIds;
        int action;
        int furniSource;

        public JsonData(int delay, List<Integer> itemIds, int action, int furniSource) {
            this.delay = delay;
            this.itemIds = itemIds;
            this.action = action;
            this.furniSource = furniSource;
        }
    }
}
