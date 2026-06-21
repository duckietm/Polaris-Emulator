package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredCondition;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredConditionOperator;
import com.eu.habbo.habbohotel.wired.WiredConditionType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import com.eu.habbo.messages.ServerMessage;
import gnu.trove.set.hash.THashSet;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class WiredConditionTriggerOnFurni extends InteractionWiredCondition {
    protected static final int QUANTIFIER_ALL = 0;
    protected static final int QUANTIFIER_ANY = 1;

    public static final WiredConditionType type = WiredConditionType.TRIGGER_ON_FURNI;

    protected THashSet<HabboItem> items = new THashSet<>();
    protected int furniSource = WiredSourceUtil.SOURCE_TRIGGER;
    protected int userSource = WiredSourceUtil.SOURCE_TRIGGER;
    protected int quantifier = QUANTIFIER_ALL;

    public WiredConditionTriggerOnFurni(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredConditionTriggerOnFurni(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean evaluate(WiredContext ctx) {
        if (ctx == null || ctx.room() == null) {
            return false;
        }

        this.refresh();

        List<RoomUnit> userTargets = WiredSourceUtil.resolveUsers(ctx, this.userSource);
        if (userTargets.isEmpty())
            return false;

        List<HabboItem> itemTargets = WiredSourceUtil.resolveItems(ctx, this.furniSource, this.items);
        if (itemTargets.isEmpty())
            return false;

        if (this.quantifier == QUANTIFIER_ANY) {
            return this.isAnyUserOnFurni(userTargets, itemTargets, ctx.room());
        }

        return this.areAllUsersOnFurni(userTargets, itemTargets, ctx.room());
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    protected boolean isAnyUserOnFurni(Collection<RoomUnit> users, Collection<HabboItem> items, Room room) {
        for (RoomUnit roomUnit : users) {
            if (roomUnit == null) continue;
            THashSet<HabboItem> itemsAtUser = room.getItemsAt(roomUnit.getCurrentLocation());
            if (items.stream().anyMatch(itemsAtUser::contains)) {
                return true;
            }
        }
        return false;
    }

    protected boolean areAllUsersOnFurni(Collection<RoomUnit> users, Collection<HabboItem> items, Room room) {
        for (RoomUnit roomUnit : users) {
            if (roomUnit == null) {
                return false;
            }

            THashSet<HabboItem> itemsAtUser = room.getItemsAt(roomUnit.getCurrentLocation());
            if (itemsAtUser == null || items.stream().noneMatch(itemsAtUser::contains)) {
                return false;
            }
        }

        return true;
    }

    @Override
    public String getWiredData() {
        this.refresh();
        return WiredManager.getGson().toJson(new JsonData(
                this.items.stream().map(HabboItem::getId).collect(Collectors.toList()),
                this.furniSource,
                this.userSource,
                this.quantifier
        ));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.onPickUp();
        String wiredData = set.getString("wired_data");
        if (wiredData == null || wiredData.isEmpty()) {
            return;
        }

        if (wiredData.startsWith("{")) {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            this.furniSource = WiredFurniConditionInputGuard.normalizeFurniSource(data.furniSource);
            this.userSource = WiredFurniConditionInputGuard.normalizeUserSource(data.userSource);
            this.quantifier = this.normalizeQuantifier(data.quantifier);

            for(int id : WiredFurniConditionInputGuard.sanitizeItemIds(data.itemIds, WiredManager.MAXIMUM_FURNI_SELECTION)) {
                HabboItem item = room.getHabboItem(id);

                if (item != null) {
                    this.items.add(item);
                }
            }
        } else {
            for (int id : WiredFurniConditionInputGuard.parseLegacyItemIds(wiredData, WiredManager.MAXIMUM_FURNI_SELECTION)) {
                HabboItem item = room.getHabboItem(id);

                if (item != null) {
                    this.items.add(item);
                }
            }
            this.furniSource = this.items.isEmpty() ? WiredSourceUtil.SOURCE_TRIGGER : WiredSourceUtil.SOURCE_SELECTED;
            this.userSource = WiredSourceUtil.SOURCE_TRIGGER;
            this.quantifier = QUANTIFIER_ALL;
        }
        this.furniSource = WiredFurniConditionInputGuard.selectedOrNormalizedFurniSource(this.furniSource, !this.items.isEmpty());
    }

    @Override
    public void onPickUp() {
        this.items.clear();
        this.furniSource = WiredSourceUtil.SOURCE_TRIGGER;
        this.userSource = WiredSourceUtil.SOURCE_TRIGGER;
        this.quantifier = QUANTIFIER_ALL;
    }

    @Override
    public WiredConditionType getType() {
        return type;
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        this.refresh();

        message.appendBoolean(false);
        message.appendInt(WiredManager.MAXIMUM_FURNI_SELECTION);
        message.appendInt(this.items.size());

        for (HabboItem item : this.items)
            message.appendInt(item.getId());

        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString("");
        message.appendInt(3);
        message.appendInt(this.furniSource);
        message.appendInt(this.userSource);
        message.appendInt(this.quantifier);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(0);
        message.appendInt(0);
    }

    @Override
    public boolean saveData(WiredSettings settings) {
        int count = settings.getFurniIds().length;
        if (count > Emulator.getConfig().getInt("hotel.wired.furni.selection.count")) return false;

        int[] params = settings.getIntParams();
        this.furniSource = (params.length > 0) ? WiredFurniConditionInputGuard.normalizeFurniSource(params[0]) : WiredSourceUtil.SOURCE_TRIGGER;
        this.userSource = (params.length > 1) ? WiredFurniConditionInputGuard.normalizeUserSource(params[1]) : WiredSourceUtil.SOURCE_TRIGGER;
        this.quantifier = (params.length > 2) ? this.normalizeQuantifier(params[2]) : QUANTIFIER_ALL;

        this.furniSource = WiredFurniConditionInputGuard.selectedOrNormalizedFurniSource(this.furniSource, count > 0);

        this.items.clear();

        if (this.furniSource == WiredSourceUtil.SOURCE_SELECTED) {
            Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());

            if (room != null) {
                for (int i = 0; i < count; i++) {
                    HabboItem item = room.getHabboItem(settings.getFurniIds()[i]);

                    if (item != null) {
                        this.items.add(item);
                    }
                }
            }
        }

        return true;
    }

    protected void refresh() {
        THashSet<HabboItem> items = new THashSet<>();

        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());
        if (room == null) {
            items.addAll(this.items);
        } else {
            for (HabboItem item : this.items) {
                if (item.getRoomId() != room.getId())
                    items.add(item);
            }
        }

        this.items.removeAll(items);
    }

    protected int getQuantifier() {
        return this.quantifier;
    }

    protected int normalizeQuantifier(int value) {
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

    int normalizeUserSource(int value) {
        return WiredSourceUtil.isDefaultUserSource(value) ? value : WiredSourceUtil.SOURCE_TRIGGER;
    }

    @Override
    public WiredConditionOperator operator() {
        return WiredConditionOperator.AND;
    }

    static class JsonData {
        List<Integer> itemIds;
        int furniSource;
        int userSource;
        int quantifier;

        public JsonData(List<Integer> itemIds, int furniSource, int userSource, int quantifier) {
            this.itemIds = itemIds;
            this.furniSource = furniSource;
            this.userSource = userSource;
            this.quantifier = quantifier;
        }
    }
}
