package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

import com.eu.habbo.Emulator;
import com.eu.habbo.WiredCompatibilityDiagnostics;
import com.eu.habbo.WiredPlatform;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredCondition;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredConditionType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredQuantifier;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import com.eu.habbo.habbohotel.wired.protocol.WiredOfficialQuantifier;
import com.eu.habbo.habbohotel.wired.protocol.WiredOfficialSecondaryPicks;
import com.eu.habbo.habbohotel.wired.protocol.WiredOfficialSources;
import com.eu.habbo.messages.ServerMessage;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class WiredConditionFurniTypeMatch extends InteractionWiredCondition
        implements WiredOfficialQuantifier, WiredOfficialSources, WiredOfficialSecondaryPicks {
    protected static final int SOURCE_SECONDARY_SELECTED = 101;
    protected static final int QUANTIFIER_ALL = 0;
    protected static final int QUANTIFIER_ANY = 1;

    public static final WiredConditionType type = WiredConditionType.STUFF_IS;

    protected Set<HabboItem> items = new LinkedHashSet<>();
    protected Set<HabboItem> secondaryItems = new LinkedHashSet<>();
    protected int furniSource = WiredSourceUtil.SOURCE_TRIGGER;
    protected int compareFurniSource = WiredSourceUtil.SOURCE_TRIGGER;
    protected int quantifier = QUANTIFIER_ALL;

    public WiredConditionFurniTypeMatch(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredConditionFurniTypeMatch(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
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
            return WiredQuantifier.overEmpty(true);
        }

        Set<Integer> compareTypeIds = this.resolveCompareTypeIds(ctx);
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

        Set<Integer> compareTypeIds = this.resolveCompareTypeIds(ctx);
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

    protected Set<Integer> resolveCompareTypeIds(WiredContext ctx) {
        this.refresh();

        Set<Integer> compareTypeIds = new HashSet<>();

        for (HabboItem item : this.resolveConfiguredItems(ctx, this.compareFurniSource)) {
            if (item != null && item.getBaseItem() != null) {
                compareTypeIds.add(item.getBaseItem().getId());
            }
        }

        return compareTypeIds;
    }

    protected boolean matchesType(HabboItem item, Set<Integer> compareTypeIds) {
        return item != null
                && item.getBaseItem() != null
                && compareTypeIds.contains(item.getBaseItem().getId());
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
        return WiredManager.getGson()
                .toJson(new JsonData(
                        this.items.stream().map(HabboItem::getId).collect(Collectors.toList()),
                        this.secondaryItems.stream().map(HabboItem::getId).collect(Collectors.toList()),
                        this.furniSource,
                        this.compareFurniSource,
                        this.quantifier));
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
            List<Integer> compareIds = (data.secondaryItemIds != null)
                    ? data.secondaryItemIds
                    : ((data.itemIds != null) ? data.itemIds : new ArrayList<>());

            this.furniSource = this.normalizeFurniSource(
                    (data.furniSource != null) ? data.furniSource : WiredSourceUtil.SOURCE_TRIGGER);
            this.compareFurniSource = this.normalizeFurniSource(
                    (data.compareFurniSource != null)
                            ? data.compareFurniSource
                            : (compareIds.isEmpty() ? WiredSourceUtil.SOURCE_TRIGGER : SOURCE_SECONDARY_SELECTED));
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
                WiredCompatibilityDiagnostics.record(
                        WiredCompatibilityDiagnostics.FailurePoint.CONDITION_FURNI_TYPE_ID,
                        this.getRoomId(),
                        this.getId(),
                        ignored);
            }
        }

        this.loadItems(room, compareIds, this.secondaryItems);
        this.compareFurniSource =
                this.secondaryItems.isEmpty() ? WiredSourceUtil.SOURCE_TRIGGER : SOURCE_SECONDARY_SELECTED;
        this.quantifier = QUANTIFIER_ANY;
    }

    @Override
    public WiredConditionType getType() {
        return type;
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        this.refresh();

        // Official layout (StuffTypeMatches): no int params, the furni checked and the furni whose types they
        // must have in the two source slots (100 = the picks, stuffIds; 101 = the other picks, stuffIds2).
        message.appendBoolean(false);
        message.appendInt(WiredManager.MAXIMUM_FURNI_SELECTION);
        message.appendInt(this.items.size());

        for (HabboItem item : this.items) {
            message.appendInt(item.getId());
        }

        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString("");
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(0);
        message.appendInt(0);
    }

    @Override
    public int[] officialFurniSources() {
        return new int[] {this.furniSource, this.compareFurniSource};
    }

    @Override
    public int[] officialUserSources() {
        return new int[0];
    }

    @Override
    public int[] officialFurniIds2() {
        return this.secondaryItems.stream().mapToInt(HabboItem::getId).toArray();
    }

    @Override
    public boolean saveData(WiredSettings settings) {
        int count = settings.getFurniIds().length;
        int maxFurni = Emulator.getConfig().getInt("hotel.wired.furni.selection.count");
        if (count > maxFurni) return false;
        if (settings.getFurniIds2().length > maxFurni) return false;

        int[] params = settings.getIntParams();
        int[] sources = settings.getFurniSourceTypes();
        String stringParam =
                (settings.getStringParam() != null) ? settings.getStringParam().trim() : "";

        this.onPickUp();

        // The official save names both slots; an older client sent only the checked furni's source, the type
        // source as its int and the checked furni in the string.
        boolean official = sources.length >= 2;

        this.furniSource = sources.length > 0 ? this.normalizeFurniSource(sources[0]) : WiredSourceUtil.SOURCE_TRIGGER;
        if (official) {
            this.compareFurniSource = this.normalizeFurniSource(sources[1]);
        } else {
            this.compareFurniSource =
                    (params.length > 0) ? this.normalizeFurniSource(params[0]) : SOURCE_SECONDARY_SELECTED;
        }
        // A fresh box opens on "any": officially one match is enough.
        this.quantifier = this.normalizeQuantifier(settings.getQuantifierCode());

        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());
        if (room == null) {
            return false;
        }

        if (official) {
            if (this.usesSource(WiredSourceUtil.SOURCE_SELECTED)) {
                this.addItems(room, settings.getFurniIds(), this.items);
            }
            if (this.usesSource(SOURCE_SECONDARY_SELECTED)) {
                this.addItems(room, settings.getFurniIds2(), this.secondaryItems);
            }
            return true;
        }

        if (this.compareFurniSource == SOURCE_SECONDARY_SELECTED) {
            this.addItems(room, settings.getFurniIds(), this.secondaryItems);
        }

        if (this.furniSource == WiredSourceUtil.SOURCE_SELECTED) {
            for (Integer itemId : this.parseIds(stringParam)) {
                HabboItem item = room.getHabboItem(itemId);
                if (item != null) {
                    this.items.add(item);
                }
            }
        }

        return true;
    }

    private boolean usesSource(int source) {
        return this.furniSource == source || this.compareFurniSource == source;
    }

    private void addItems(Room room, int[] itemIds, Set<HabboItem> target) {
        for (int itemId : itemIds) {
            HabboItem item = room.getHabboItem(itemId);
            if (item != null) {
                target.add(item);
            }
        }
    }

    protected int getQuantifier() {
        return this.quantifier;
    }

    protected void refresh() {
        this.refreshSelection(this.items);
        this.refreshSelection(this.secondaryItems);
    }

    private void refreshSelection(Set<HabboItem> selection) {
        Set<HabboItem> remove = new HashSet<>();

        if (WiredPlatform.gameEnvironment() == null
                || WiredPlatform.gameEnvironment().getRoomManager() == null) {
            return;
        }

        Room room = WiredPlatform.gameEnvironment().getRoomManager().getRoom(this.getRoomId());
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

    void loadItems(Room room, List<Integer> itemIds, Set<HabboItem> target) {
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
                WiredCompatibilityDiagnostics.record(
                        WiredCompatibilityDiagnostics.FailurePoint.CONDITION_FURNI_TYPE_ID,
                        this.getRoomId(),
                        this.getId(),
                        ignored);
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

        public JsonData(
                List<Integer> primaryItemIds,
                List<Integer> secondaryItemIds,
                int furniSource,
                int compareFurniSource,
                int quantifier) {
            this.primaryItemIds = primaryItemIds;
            this.secondaryItemIds = secondaryItemIds;
            this.furniSource = furniSource;
            this.compareFurniSource = compareFurniSource;
            this.quantifier = quantifier;
        }
    }

    @Override
    public int officialQuantifierCode() {
        return this.getQuantifier();
    }
}
