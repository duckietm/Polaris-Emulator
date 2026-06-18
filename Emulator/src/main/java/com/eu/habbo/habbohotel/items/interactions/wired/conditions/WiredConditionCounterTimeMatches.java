package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredCondition;
import com.eu.habbo.habbohotel.items.interactions.games.InteractionGameUpCounter;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredConditionType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import com.eu.habbo.messages.ServerMessage;
import gnu.trove.set.hash.THashSet;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.stream.Collectors;

public class WiredConditionCounterTimeMatches extends InteractionWiredCondition {
    private static final int COMPARISON_LESS = 0;
    private static final int COMPARISON_EQUAL = 1;
    private static final int COMPARISON_GREATER = 2;
    private static final int QUANTIFIER_ALL = 0;
    private static final int QUANTIFIER_ANY = 1;
    private static final int MAX_MINUTES = 99;
    private static final int MAX_HALF_SECOND_STEPS = 119;

    public static final WiredConditionType type = WiredConditionType.COUNTER_TIME_MATCHES;

    private final THashSet<HabboItem> items;
    private int comparison = COMPARISON_EQUAL;
    private int minutes = 0;
    private int halfSecondSteps = 0;
    private int furniSource = WiredSourceUtil.SOURCE_TRIGGER;
    private int quantifier = QUANTIFIER_ALL;

    public WiredConditionCounterTimeMatches(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
        this.items = new THashSet<>();
    }

    public WiredConditionCounterTimeMatches(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
        this.items = new THashSet<>();
    }

    @Override
    public boolean evaluate(WiredContext ctx) {
        Room room = ctx.room();
        if (room == null) {
            return false;
        }

        this.refresh(room);

        List<HabboItem> targets = WiredSourceUtil.resolveItems(ctx, this.furniSource, this.items);
        if (targets.isEmpty()) {
            return false;
        }

        int targetTimeInMs = this.getTargetTimeInMs();

        if (this.quantifier == QUANTIFIER_ANY) {
            for (HabboItem item : targets) {
                if (!(item instanceof InteractionGameUpCounter)) {
                    continue;
                }

                if (this.matchesCounter((InteractionGameUpCounter) item, targetTimeInMs)) {
                    return true;
                }
            }

            return false;
        }

        for (HabboItem item : targets) {
            if (!(item instanceof InteractionGameUpCounter)) {
                return false;
            }

            if (!this.matchesCounter((InteractionGameUpCounter) item, targetTimeInMs)) {
                return false;
            }
        }

        return true;
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(
                this.comparison,
                this.minutes,
                this.halfSecondSteps,
                this.furniSource,
                this.quantifier,
                this.items.stream().map(HabboItem::getId).collect(Collectors.toList())
        ));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.resetSettings();

        String wiredData = set.getString("wired_data");
        if (wiredData == null || wiredData.isEmpty() || !wiredData.startsWith("{")) {
            return;
        }

        JsonData data;
        try {
            data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
        } catch (RuntimeException exception) {
            this.resetSettings();
            return;
        }

        if (data == null) {
            return;
        }

        this.comparison = this.normalizeComparison(data.comparison);
        this.minutes = this.normalizeMinutes(data.minutes);
        this.halfSecondSteps = this.normalizeHalfSecondSteps(data.halfSecondSteps);
        this.furniSource = this.normalizeFurniSource(data.furniSource);
        this.quantifier = this.normalizeQuantifier(data.quantifier);

        if (data.itemIds == null) {
            return;
        }

        for (Integer id : data.itemIds) {
            if (id == null) {
                continue;
            }

            HabboItem item = room.getHabboItem(id);
            if (item instanceof InteractionGameUpCounter) {
                this.items.add(item);
            }
        }
    }

    @Override
    public void onPickUp() {
        this.items.clear();
        this.comparison = COMPARISON_EQUAL;
        this.minutes = 0;
        this.halfSecondSteps = 0;
        this.furniSource = WiredSourceUtil.SOURCE_TRIGGER;
        this.quantifier = QUANTIFIER_ALL;
    }

    @Override
    public WiredConditionType getType() {
        return type;
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        this.refresh(room);

        message.appendBoolean(false);
        message.appendInt(WiredManager.MAXIMUM_FURNI_SELECTION);
        message.appendInt(this.items.size());

        for (HabboItem item : this.items) {
            message.appendInt(item.getId());
        }

        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString("");
        message.appendInt(5);
        message.appendInt(this.comparison);
        message.appendInt(this.minutes);
        message.appendInt(this.halfSecondSteps);
        message.appendInt(this.furniSource);
        message.appendInt(this.quantifier);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(0);
        message.appendInt(0);
    }

    @Override
    public boolean saveData(WiredSettings settings) {
        int[] params = settings.getIntParams();

        this.comparison = (params.length > 0) ? this.normalizeComparison(params[0]) : COMPARISON_EQUAL;
        this.minutes = (params.length > 1) ? this.normalizeMinutes(params[1]) : 0;
        this.halfSecondSteps = (params.length > 2) ? this.normalizeHalfSecondSteps(params[2]) : 0;
        this.furniSource = (params.length > 3) ? this.normalizeFurniSource(params[3]) : WiredSourceUtil.SOURCE_TRIGGER;
        this.quantifier = (params.length > 4) ? this.normalizeQuantifier(params[4]) : QUANTIFIER_ALL;

        this.items.clear();

        if (this.furniSource != WiredSourceUtil.SOURCE_SELECTED) {
            return true;
        }

        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());
        if (room == null) {
            return false;
        }

        int count = settings.getFurniIds().length;
        if (count > Emulator.getConfig().getInt("hotel.wired.furni.selection.count")) {
            return false;
        }

        for (int itemId : settings.getFurniIds()) {
            HabboItem item = room.getHabboItem(itemId);
            if (item instanceof InteractionGameUpCounter) {
                this.items.add(item);
            }
        }

        return true;
    }

    private void resetSettings() {
        this.items.clear();
        this.comparison = COMPARISON_EQUAL;
        this.minutes = 0;
        this.halfSecondSteps = 0;
        this.furniSource = WiredSourceUtil.SOURCE_TRIGGER;
        this.quantifier = QUANTIFIER_ALL;
    }

    private void refresh(Room room) {
        THashSet<HabboItem> remove = new THashSet<>();

        for (HabboItem item : this.items) {
            HabboItem roomItem = room.getHabboItem(item.getId());
            if (!(roomItem instanceof InteractionGameUpCounter)) {
                remove.add(item);
            }
        }

        for (HabboItem item : remove) {
            this.items.remove(item);
        }
    }

    private int getTargetTimeInMs() {
        return (this.minutes * 60_000) + (this.halfSecondSteps * 500);
    }

    private boolean matchesCounter(InteractionGameUpCounter counter, int targetTimeInMs) {
        int currentTimeInMs = counter.getCurrentTimeInMs();

        switch (this.comparison) {
            case COMPARISON_LESS:
                return currentTimeInMs < targetTimeInMs;
            case COMPARISON_GREATER:
                return currentTimeInMs > targetTimeInMs;
            default:
                return currentTimeInMs == targetTimeInMs;
        }
    }

    int normalizeComparison(int value) {
        if (value < COMPARISON_LESS || value > COMPARISON_GREATER) {
            return COMPARISON_EQUAL;
        }

        return value;
    }

    int normalizeMinutes(int value) {
        return Math.max(0, Math.min(MAX_MINUTES, value));
    }

    int normalizeHalfSecondSteps(int value) {
        return Math.max(0, Math.min(MAX_HALF_SECOND_STEPS, value));
    }

    int normalizeQuantifier(int value) {
        return (value == QUANTIFIER_ANY) ? QUANTIFIER_ANY : QUANTIFIER_ALL;
    }

    int normalizeFurniSource(int value) {
        switch (value) {
            case WiredSourceUtil.SOURCE_SELECTED:
            case WiredSourceUtil.SOURCE_SELECTOR:
            case WiredSourceUtil.SOURCE_SIGNAL:
            case WiredSourceUtil.SOURCE_TRIGGER:
                return value;
            default:
                return WiredSourceUtil.SOURCE_TRIGGER;
        }
    }

    static class JsonData {
        int comparison;
        int minutes;
        int halfSecondSteps;
        int furniSource;
        int quantifier;
        List<Integer> itemIds;

        public JsonData(int comparison, int minutes, int halfSecondSteps, int furniSource, int quantifier, List<Integer> itemIds) {
            this.comparison = comparison;
            this.minutes = minutes;
            this.halfSecondSteps = halfSecondSteps;
            this.furniSource = furniSource;
            this.quantifier = quantifier;
            this.itemIds = itemIds;
        }
    }
}
