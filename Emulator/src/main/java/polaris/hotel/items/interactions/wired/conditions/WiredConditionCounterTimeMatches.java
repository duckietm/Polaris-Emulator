package polaris.hotel.items.interactions.wired.conditions;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import polaris.Emulator;
import polaris.hotel.items.Item;
import polaris.hotel.items.interactions.InteractionWiredCondition;
import polaris.hotel.items.interactions.games.InteractionGameUpCounter;
import polaris.hotel.items.interactions.wired.WiredSaveRefusedException;
import polaris.hotel.items.interactions.wired.WiredSettings;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.users.UserItem;
import polaris.hotel.wired.WiredConditionType;
import polaris.hotel.wired.core.WiredContext;
import polaris.hotel.wired.core.WiredManager;
import polaris.hotel.wired.core.WiredQuantifier;
import polaris.hotel.wired.core.WiredSourceUtil;
import polaris.hotel.wired.protocol.WiredOfficialQuantifier;
import polaris.messages.ServerMessage;

public class WiredConditionCounterTimeMatches extends InteractionWiredCondition implements WiredOfficialQuantifier {
    private static final int COMPARISON_LESS = 0;
    private static final int COMPARISON_EQUAL = 1;
    private static final int COMPARISON_GREATER = 2;
    private static final int QUANTIFIER_ALL = 0;
    private static final int QUANTIFIER_ANY = 1;
    private static final int MAX_MINUTES = 99;
    private static final int MAX_HALF_SECOND_STEPS = 119;

    public static final WiredConditionType type = WiredConditionType.COUNTER_TIME_MATCHES;

    private final Set<UserItem> items;
    private int comparison = COMPARISON_EQUAL;
    private int minutes = 0;
    private int halfSecondSteps = 0;
    private int furniSource = WiredSourceUtil.SOURCE_TRIGGER;
    private int quantifier = QUANTIFIER_ALL;

    public WiredConditionCounterTimeMatches(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
        this.items = new LinkedHashSet<>();
    }

    public WiredConditionCounterTimeMatches(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
        this.items = new LinkedHashSet<>();
    }

    @Override
    public boolean evaluate(WiredContext ctx) {
        Room room = ctx.room();
        if (room == null) {
            return false;
        }

        this.refresh(room);

        List<UserItem> targets = WiredSourceUtil.resolveItems(ctx, this.furniSource, this.items);
        if (targets.isEmpty()) {
            return WiredQuantifier.overEmpty(this.quantifier != QUANTIFIER_ANY);
        }

        int targetTimeInMs = this.getTargetTimeInMs();

        if (this.quantifier == QUANTIFIER_ANY) {
            for (UserItem item : targets) {
                if (!(item instanceof InteractionGameUpCounter)) {
                    continue;
                }

                if (this.matchesCounter((InteractionGameUpCounter) item, targetTimeInMs)) {
                    return true;
                }
            }

            return false;
        }

        for (UserItem item : targets) {
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
        return WiredManager.getGson()
                .toJson(new JsonData(
                        this.comparison,
                        this.minutes,
                        this.halfSecondSteps,
                        this.furniSource,
                        this.quantifier,
                        this.items.stream().map(UserItem::getId).collect(Collectors.toList())));
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

            UserItem item = room.getUserItem(id);
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

        for (UserItem item : this.items) {
            message.appendInt(item.getId());
        }

        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString("");
        // Official layout: [seconds (0-59), minutes (0-99), half second (0-1), comparison (0 less, 1 equal,
        // 2 more)], the counters in the official source slot, the quantifier (0 all, 1 any) in its slot.
        message.appendInt(4);
        message.appendInt(this.halfSecondSteps / 2);
        message.appendInt(this.minutes);
        message.appendInt(this.halfSecondSteps % 2);
        message.appendInt(this.comparison);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(0);
        message.appendInt(0);
    }

    @Override
    public boolean saveData(WiredSettings settings) {
        int[] params = settings.getIntParams();
        refuseNonCounterPicks(settings);

        int seconds = (params.length > 0) ? Math.clamp(params[0], 0, 59) : 0;
        this.minutes = (params.length > 1) ? this.normalizeMinutes(params[1]) : 0;
        int half = (params.length > 2) ? Math.clamp(params[2], 0, 1) : 0;
        this.halfSecondSteps = this.normalizeHalfSecondSteps(seconds * 2 + half);
        this.comparison = (params.length > 3) ? this.normalizeComparison(params[3]) : COMPARISON_EQUAL;
        // A fresh box opens on "any": officially one counter is enough.
        this.quantifier = this.normalizeQuantifier(settings.getQuantifierCode());
        this.furniSource = settings.getFurniSourceTypes().length > 0
                ? this.normalizeFurniSource(settings.getFurniSourceTypes()[0])
                : WiredSourceUtil.SOURCE_TRIGGER;

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
            UserItem item = room.getUserItem(itemId);
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
        Set<UserItem> remove = new HashSet<>();

        for (UserItem item : this.items) {
            UserItem roomItem = room.getUserItem(item.getId());
            if (!(roomItem instanceof InteractionGameUpCounter)) {
                remove.add(item);
            }
        }

        for (UserItem item : remove) {
            this.items.remove(item);
        }
    }

    private int getTargetTimeInMs() {
        return (this.minutes * 60_000) + (this.halfSecondSteps * 500);
    }

    /** Official: picking anything but a counter refuses the save with the counter text. */
    private void refuseNonCounterPicks(WiredSettings settings) {
        int[] sources = settings.getFurniSourceTypes();
        if (sources.length == 0 || this.normalizeFurniSource(sources[0]) != WiredSourceUtil.SOURCE_SELECTED) return;

        Room room = settings.getRoom();
        if (room == null) return;

        for (int itemId : settings.getFurniIds()) {
            if (!(room.getUserItem(itemId) instanceof InteractionGameUpCounter)) {
                throw new WiredSaveRefusedException("wiredfurni.error.require_counter_furni");
            }
        }
    }

    private boolean matchesCounter(InteractionGameUpCounter counter, int targetTimeInMs) {
        long currentTimeInMs = counter.getCurrentTimeMs();

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

        public JsonData(
                int comparison,
                int minutes,
                int halfSecondSteps,
                int furniSource,
                int quantifier,
                List<Integer> itemIds) {
            this.comparison = comparison;
            this.minutes = minutes;
            this.halfSecondSteps = halfSecondSteps;
            this.furniSource = furniSource;
            this.quantifier = quantifier;
            this.itemIds = itemIds;
        }
    }

    @Override
    public int officialQuantifierCode() {
        return this.quantifier;
    }
}
