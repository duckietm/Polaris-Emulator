package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

import com.eu.habbo.Emulator;
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
import gnu.trove.set.hash.THashSet;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class WiredConditionFurniTypeMatch extends InteractionWiredCondition {
    protected static final int SOURCE_SECONDARY_SELECTED = 101;
    protected static final int QUANTIFIER_ALL = 0;
    protected static final int QUANTIFIER_ANY = 1;

    public static final WiredConditionType type = WiredConditionType.STUFF_IS;

    protected THashSet<HabboItem> items = new THashSet<>();
    protected THashSet<HabboItem> secondaryItems = new THashSet<>();
    protected int furniSource = WiredSourceUtil.SOURCE_TRIGGER;
    protected int compareFurniSource = WiredSourceUtil.SOURCE_TRIGGER;
    protected int quantifier = QUANTIFIER_ALL;

    public WiredConditionFurniTypeMatch(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredConditionFurniTypeMatch(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void onPickUp() {
        this.items.clear();
        this.secondaryItems.clear();
        this.furniSource = WiredSourceUtil.SOURCE_TRIGGER;
        this.compareFurniSource = WiredSourceUtil.SOURCE_TRIGGER;
        this.quantifier = QUANTIFIER_ALL;
    }

    @Override
    public boolean evaluate(WiredContext ctx) {
        if (ctx == null) {
            return false;
        }

        if (this.quantifier == QUANTIFIER_ANY) {
            return this.evaluateAnyMatches(ctx);
        }

        return this.evaluateAllMatches(ctx);
    }

    protected boolean evaluateAllMatches(WiredContext ctx) {
        List<HabboItem> matchTargets = this.resolveMatchTargets(ctx);
        if (matchTargets.isEmpty()) {
            return false;
        }

        THashSet<Integer> compareTypeIds = this.resolveCompareTypeIds(ctx);
        if (compareTypeIds.isEmpty()) {
            return false;
        }

        for (HabboItem item : matchTargets) {
            if (!this.matchesType(item, compareTypeIds)) {
                return false;
            }
        }

        return true;
    }

    protected boolean evaluateAnyMatches(WiredContext ctx) {
        List<HabboItem> matchTargets = this.resolveMatchTargets(ctx);
        if (matchTargets.isEmpty()) {
            return false;
        }

        THashSet<Integer> compareTypeIds = this.resolveCompareTypeIds(ctx);
        if (compareTypeIds.isEmpty()) {
            return false;
        }

        for (HabboItem item : matchTargets) {
            if (this.matchesType(item, compareTypeIds)) {
                return true;
            }
        }

        return false;
    }

    protected List<HabboItem> resolveMatchTargets(WiredContext ctx) {
        this.refresh();
        return this.resolveConfiguredItems(ctx, this.furniSource);
    }

    protected THashSet<Integer> resolveCompareTypeIds(WiredContext ctx) {
        this.refresh();

        THashSet<Integer> compareTypeIds = new THashSet<>();

        for (HabboItem item : this.resolveConfiguredItems(ctx, this.compareFurniSource)) {
            if (item != null && item.getBaseItem() != null) {
                compareTypeIds.add(item.getBaseItem().getId());
            }
        }

        return compareTypeIds;
    }

    protected boolean matchesType(HabboItem item, THashSet<Integer> compareTypeIds) {
        return item != null && item.getBaseItem() != null && compareTypeIds.contains(item.getBaseItem().getId());
    }

    protected List<HabboItem> resolveConfiguredItems(WiredContext ctx, int sourceType) {
        if (sourceType == SOURCE_SECONDARY_SELECTED) {
            return new ArrayList<>(this.secondaryItems);
        }

        return WiredSourceUtil.resolveItems(ctx, sourceType, this.items);
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public String getWiredData() {
        this.refresh();
        return WiredManager.getGson().toJson(new JsonData(
                this.items.stream().map(HabboItem::getId).collect(Collectors.toList()),
                this.secondaryItems.stream().map(HabboItem::getId).collect(Collectors.toList()),
                this.furniSource,
                this.compareFurniSource,
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

            List<Integer> primaryIds = (data.primaryItemIds != null) ? data.primaryItemIds : new ArrayList<>();
            List<Integer> compareIds = (data.secondaryItemIds != null) ? data.secondaryItemIds : ((data.itemIds != null) ? data.itemIds : new ArrayList<>());

            this.furniSource = this.normalizeFurniSource((data.furniSource != null) ? data.furniSource : WiredSourceUtil.SOURCE_TRIGGER);
            this.compareFurniSource = this.normalizeFurniSource((data.compareFurniSource != null) ? data.compareFurniSource : (compareIds.isEmpty() ? WiredSourceUtil.SOURCE_TRIGGER : SOURCE_SECONDARY_SELECTED));
            this.quantifier = this.normalizeQuantifier((data.quantifier != null) ? data.quantifier : QUANTIFIER_ANY);

            this.loadItems(room, primaryIds, this.items);
            this.loadItems(room, compareIds, this.secondaryItems);
            return;
        }

        String[] data = wiredData.split(";");
        List<Integer> compareIds = new ArrayList<>();

        for (String value : data) {
            try {
                compareIds.add(Integer.parseInt(value));
            } catch (NumberFormatException ignored) {
            }
        }

        this.loadItems(room, compareIds, this.secondaryItems);
        this.compareFurniSource = this.secondaryItems.isEmpty() ? WiredSourceUtil.SOURCE_TRIGGER : SOURCE_SECONDARY_SELECTED;
        this.quantifier = QUANTIFIER_ANY;
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

        for (HabboItem item : this.items) {
            message.appendInt(item.getId());
        }

        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString(this.serializeIds(this.secondaryItems));
        message.appendInt(3);
        message.appendInt(this.furniSource);
        message.appendInt(this.compareFurniSource);
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
        String stringParam = (settings.getStringParam() != null) ? settings.getStringParam().trim() : "";
        boolean legacyData = (params.length <= 1) && stringParam.isEmpty();

        this.onPickUp();

        if (legacyData) {
            this.furniSource = (params.length > 0) ? this.normalizeFurniSource(params[0]) : WiredSourceUtil.SOURCE_TRIGGER;
            this.quantifier = QUANTIFIER_ANY;
        } else {
            this.furniSource = (params.length > 0) ? this.normalizeFurniSource(params[0]) : WiredSourceUtil.SOURCE_TRIGGER;
            this.compareFurniSource = (params.length > 1) ? this.normalizeFurniSource(params[1]) : WiredSourceUtil.SOURCE_TRIGGER;
            this.quantifier = (params.length > 2) ? this.normalizeQuantifier(params[2]) : QUANTIFIER_ALL;
        }

        if (count > 0 && this.furniSource == WiredSourceUtil.SOURCE_TRIGGER) {
            this.furniSource = WiredSourceUtil.SOURCE_SELECTED;
        }

        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());
        if (room == null) {
            return false;
        }

        if (legacyData) {
            for (int itemId : settings.getFurniIds()) {
                HabboItem item = room.getHabboItem(itemId);
                if (item != null) {
                    this.secondaryItems.add(item);
                }
            }

            this.compareFurniSource = this.secondaryItems.isEmpty() ? WiredSourceUtil.SOURCE_TRIGGER : SOURCE_SECONDARY_SELECTED;
            return true;
        }

        if (this.furniSource == WiredSourceUtil.SOURCE_SELECTED) {
            for (int itemId : settings.getFurniIds()) {
                HabboItem item = room.getHabboItem(itemId);
                if (item != null) {
                    this.items.add(item);
                }
            }
        }

        if (this.compareFurniSource == SOURCE_SECONDARY_SELECTED) {
            for (Integer itemId : this.parseIds(stringParam)) {
                HabboItem item = room.getHabboItem(itemId);
                if (item != null) {
                    this.secondaryItems.add(item);
                }
            }
        }

        return true;
    }

    protected int getQuantifier() {
        return this.quantifier;
    }

    protected void refresh() {
        this.refreshSelection(this.items);
        this.refreshSelection(this.secondaryItems);
    }

    private void refreshSelection(THashSet<HabboItem> selection) {
        THashSet<HabboItem> remove = new THashSet<>();

        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());
        if (room == null) {
            remove.addAll(selection);
        } else {
            for (HabboItem item : selection) {
                if (room.getHabboItem(item.getId()) == null) {
                    remove.add(item);
                }
            }
        }

        for (HabboItem item : remove) {
            selection.remove(item);
        }
    }

    void loadItems(Room room, List<Integer> itemIds, THashSet<HabboItem> target) {
        if (room == null || itemIds == null || target == null) {
            return;
        }

        for (Integer id : itemIds) {
            if (id == null) {
                continue;
            }

            HabboItem item = room.getHabboItem(id);
            if (item != null) {
                target.add(item);
            }
        }
    }

    private String serializeIds(THashSet<HabboItem> source) {
        return source.stream()
                .map(HabboItem::getId)
                .filter(id -> id > 0)
                .map(String::valueOf)
                .collect(Collectors.joining(";"));
    }

    List<Integer> parseIds(String value) {
        List<Integer> result = new ArrayList<>();
        if (value == null || value.isEmpty()) {
            return result;
        }

        for (String part : value.split("[;,\\t]")) {
            if (part == null || part.trim().isEmpty()) {
                continue;
            }

            try {
                result.add(Integer.parseInt(part.trim()));
            } catch (NumberFormatException ignored) {
            }
        }

        return result;
    }

    protected int normalizeFurniSource(int value) {
        switch (value) {
            case WiredSourceUtil.SOURCE_TRIGGER:
            case WiredSourceUtil.SOURCE_SELECTED:
            case SOURCE_SECONDARY_SELECTED:
            case WiredSourceUtil.SOURCE_SELECTOR:
            case WiredSourceUtil.SOURCE_SIGNAL:
                return value;
            default:
                return WiredSourceUtil.SOURCE_TRIGGER;
        }
    }

    protected int normalizeQuantifier(int value) {
        return (value == QUANTIFIER_ANY) ? QUANTIFIER_ANY : QUANTIFIER_ALL;
    }

    static class JsonData {
        List<Integer> primaryItemIds;
        List<Integer> secondaryItemIds;
        List<Integer> itemIds;
        Integer furniSource;
        Integer compareFurniSource;
        Integer quantifier;

        public JsonData(List<Integer> primaryItemIds, List<Integer> secondaryItemIds, int furniSource, int compareFurniSource, int quantifier) {
            this.primaryItemIds = primaryItemIds;
            this.secondaryItemIds = secondaryItemIds;
            this.furniSource = furniSource;
            this.compareFurniSource = compareFurniSource;
            this.quantifier = quantifier;
        }
    }
}
