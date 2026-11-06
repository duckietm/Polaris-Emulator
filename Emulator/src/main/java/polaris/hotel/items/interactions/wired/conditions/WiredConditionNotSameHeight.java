package polaris.hotel.items.interactions.wired.conditions;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import polaris.Emulator;
import polaris.hotel.items.Item;
import polaris.hotel.items.interactions.InteractionWiredCondition;
import polaris.hotel.items.interactions.wired.WiredSettings;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.users.UserItem;
import polaris.hotel.wired.WiredConditionType;
import polaris.hotel.wired.core.WiredContext;
import polaris.hotel.wired.core.WiredManager;
import polaris.hotel.wired.core.WiredSourceUtil;
import polaris.hotel.wired.protocol.WiredOfficialQuantifier;
import polaris.messages.ServerMessage;

/**
 * Negation of {@link WiredConditionSameHeight}, under whichever quantifier the dialog is set to.
 * {@code all} - the historical and default behaviour - passes when the targets do not all share one
 * stack height, i.e. at least one differs; {@code any} passes when no two of them meet at all. Same
 * furni-picker resolution (via {@code this.items}) and dialog reuse; needs at least 2 furni to mean
 * anything.
 */
public class WiredConditionNotSameHeight extends InteractionWiredCondition implements WiredOfficialQuantifier {
    private static final int QUANTIFIER_ALL = 0;
    private static final int QUANTIFIER_ANY = 1;

    public static final WiredConditionType type = WiredConditionType.FURNI_PROPERTY;

    private final HashSet<UserItem> items;
    private int furniSource = WiredSourceUtil.SOURCE_TRIGGER;
    private int quantifier = QUANTIFIER_ALL;

    public WiredConditionNotSameHeight(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
        this.items = new HashSet<>();
    }

    public WiredConditionNotSameHeight(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
        this.items = new HashSet<>();
    }

    @Override
    public boolean evaluate(WiredContext ctx) {
        Room room = ctx.room();
        if (room == null) {
            return false;
        }

        this.refresh(room);

        List<UserItem> targets = WiredSourceUtil.resolveItems(ctx, this.furniSource, this.items);
        if (targets.size() < 2) {
            return false;
        }

        return this.quantifier == QUANTIFIER_ANY
                ? !WiredConditionSameHeight.anyPairShareHeight(targets)
                : !WiredConditionSameHeight.allShareHeight(targets);
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
                        this.furniSource,
                        this.quantifier,
                        this.items.stream().map(UserItem::getId).toList()));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.items.clear();
        this.furniSource = WiredSourceUtil.SOURCE_TRIGGER;
        this.quantifier = QUANTIFIER_ALL;

        String wiredData = set.getString("wired_data");
        if (wiredData == null || !wiredData.startsWith("{")) {
            return;
        }

        JsonData data;
        try {
            data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
        } catch (RuntimeException exception) {
            this.onPickUp();
            return;
        }

        if (data == null) {
            return;
        }

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
            if (item != null) {
                this.items.add(item);
            }
        }
    }

    @Override
    public void onPickUp() {
        this.items.clear();
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
        message.appendInt(1);
        message.appendInt(this.furniSource);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(0);
        message.appendInt(0);
    }

    @Override
    public boolean saveData(WiredSettings settings) {
        int[] params = settings.getIntParams();
        this.furniSource = (params.length > 0) ? this.normalizeFurniSource(params[0]) : WiredSourceUtil.SOURCE_TRIGGER;
        this.quantifier = this.normalizeQuantifier(settings.getQuantifierCode());

        int count = settings.getFurniIds().length;
        if (count > Emulator.getConfig().getInt("hotel.wired.furni.selection.count")) {
            return false;
        }

        if (count > 0 && this.furniSource == WiredSourceUtil.SOURCE_TRIGGER) {
            this.furniSource = WiredSourceUtil.SOURCE_SELECTED;
        }

        this.items.clear();

        if (this.furniSource == WiredSourceUtil.SOURCE_SELECTED) {
            Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());
            if (room == null) {
                return false;
            }

            for (int itemId : settings.getFurniIds()) {
                UserItem item = room.getUserItem(itemId);
                if (item != null) {
                    this.items.add(item);
                }
            }
        }

        return true;
    }

    private void refresh(Room room) {
        var remove = new HashSet<UserItem>();

        for (UserItem item : this.items) {
            if (room.getUserItem(item.getId()) == null) {
                remove.add(item);
            }
        }

        for (UserItem item : remove) {
            this.items.remove(item);
        }
    }

    int normalizeQuantifier(int value) {
        return (value == QUANTIFIER_ANY) ? QUANTIFIER_ANY : QUANTIFIER_ALL;
    }

    int normalizeFurniSource(int value) {
        return switch (value) {
            case WiredSourceUtil.SOURCE_SELECTED,
                    WiredSourceUtil.SOURCE_SELECTOR,
                    WiredSourceUtil.SOURCE_SIGNAL,
                    WiredSourceUtil.SOURCE_TRIGGER -> value;
            default -> WiredSourceUtil.SOURCE_TRIGGER;
        };
    }

    static class JsonData {
        int furniSource;
        int quantifier;
        List<Integer> itemIds;

        public JsonData(int furniSource, int quantifier, List<Integer> itemIds) {
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
