package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

import com.eu.habbo.Emulator;
import java.util.HashSet;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredCondition;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredConditionType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import com.eu.habbo.messages.ServerMessage;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * Negation of {@link WiredConditionSameHeight}: passes when the selected furni do NOT all share the same
 * stack height (Z) — i.e. at least one differs. Same furni-picker resolution (via {@code this.items}) and
 * dialog reuse; needs at least 2 furni to be meaningful.
 */
public class WiredConditionNotSameHeight extends InteractionWiredCondition {
    private static final int QUANTIFIER_ALL = 0;
    private static final int QUANTIFIER_ANY = 1;

    public static final WiredConditionType type = WiredConditionType.HAS_ALTITUDE;

    private final HashSet<HabboItem> items;
    private int furniSource = WiredSourceUtil.SOURCE_TRIGGER;
    private int quantifier = QUANTIFIER_ALL;

    public WiredConditionNotSameHeight(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
        this.items = new HashSet<>();
    }

    public WiredConditionNotSameHeight(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
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

        List<HabboItem> targets = WiredSourceUtil.resolveItems(ctx, this.furniSource, this.items);
        if (targets.size() < 2) {
            return false;
        }

        BigDecimal reference = null;
        for (HabboItem item : targets) {
            if (item == null) {
                continue;
            }

            BigDecimal z = BigDecimal.valueOf(item.getZ());
            if (reference == null) {
                reference = z;
            } else if (reference.compareTo(z) != 0) {
                return true;
            }
        }

        return false;
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(
                this.furniSource,
                this.quantifier,
                this.items.stream().map(HabboItem::getId).toList()
        ));
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

            HabboItem item = room.getHabboItem(id);
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

        for (HabboItem item : this.items) {
            message.appendInt(item.getId());
        }

        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString("");
        message.appendInt(2);
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
        this.furniSource = (params.length > 0) ? this.normalizeFurniSource(params[0]) : WiredSourceUtil.SOURCE_TRIGGER;
        this.quantifier = (params.length > 1) ? this.normalizeQuantifier(params[1]) : QUANTIFIER_ALL;

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
                HabboItem item = room.getHabboItem(itemId);
                if (item != null) {
                    this.items.add(item);
                }
            }
        }

        return true;
    }

    private void refresh(Room room) {
        var remove = new HashSet<HabboItem>();

        for (HabboItem item : this.items) {
            if (room.getHabboItem(item.getId()) == null) {
                remove.add(item);
            }
        }

        for (HabboItem item : remove) {
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
}
