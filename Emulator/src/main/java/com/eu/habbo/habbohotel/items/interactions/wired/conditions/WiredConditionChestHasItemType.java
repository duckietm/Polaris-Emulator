package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

import com.eu.habbo.WiredPlatform;
import com.eu.habbo.core.ConfigurationManager;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredCondition;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredComparison;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSaveRefusedException;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.ChestStorage;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.InteractionWiredChest;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredConditionType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import com.eu.habbo.habbohotel.wired.core.WiredValueVariable;
import com.eu.habbo.habbohotel.wired.core.WiredVariableOperand;
import com.eu.habbo.habbohotel.wired.protocol.WiredOfficialQuantifier;
import com.eu.habbo.habbohotel.wired.protocol.WiredOfficialSecondaryPicks;
import com.eu.habbo.habbohotel.wired.protocol.WiredOfficialSources;
import com.eu.habbo.habbohotel.wired.protocol.WiredOfficialVariableIds;
import com.eu.habbo.messages.ServerMessage;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Chest Has Item Types (furni classname {@code wf_cnd_chest_has_item_type}, AIR {@code class_4118} on
 * ChestHasAmount). Passes when the furni the selected {@link InteractionWiredChest}(s) hold of the picked
 * furni types compare to an amount.
 *
 * <p>As AIR: furni slot 0 picks the item types (any furni in the room stands for its type, first picks),
 * slot 1 the chests (second picks), and the ints are Chest Has Items' {@code [value, valueIsVariable,
 * variable target, comparison]} with the variable as the first variable id.
 *
 * <p>A box saved before held a typed base item id, an amount of at least 1 and a comparison; it loads with
 * that type kept (counted alongside any picked types until the box is saved with types picked), its amount
 * as a typed value and its chests as the chest picks.
 */
public class WiredConditionChestHasItemType extends InteractionWiredCondition
        implements WiredOfficialSources,
                WiredOfficialSecondaryPicks,
                WiredOfficialVariableIds,
                WiredOfficialQuantifier {
    public static final WiredConditionType type = WiredConditionType.CHEST_HAS_ITEM_TYPE;

    static final int DEFAULT_TYPE_SOURCE = WiredSourceUtil.SOURCE_SELECTED;
    static final int DEFAULT_CHEST_SOURCE = WiredVariableOperand.SOURCE_SECONDARY_SELECTED;
    private int quantifier = 0;
    private static final String CUSTOM_TOKEN_PREFIX = "custom:";

    private final List<Integer> typeItemIds = new ArrayList<>();
    private final List<Integer> chestIds = new ArrayList<>();
    private int typeSource = DEFAULT_TYPE_SOURCE;
    private int chestSource = DEFAULT_CHEST_SOURCE;
    /** A typed base item id of a box saved before the item type picks; 0 for none. */
    private int baseItemId = 0;

    private int amount = 1;
    private int comparison = WiredComparison.GREATER_EQUAL;
    private int amountMode = WiredConditionChestHasItems.AMOUNT_CONSTANT;
    private int valueTarget = WiredValueVariable.TARGET_GLOBAL;
    private String amountVariableToken = "";

    public WiredConditionChestHasItemType(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredConditionChestHasItemType(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean evaluate(WiredContext ctx) {
        Room room = ctx.room();
        if (room == null) return false;

        Set<Integer> types = new LinkedHashSet<>();
        if (this.baseItemId > 0) types.add(this.baseItemId);
        for (HabboItem item : this.resolve(ctx, room, this.typeSource, this.typeItemIds)) {
            if (item.getBaseItem() != null) types.add(item.getBaseItem().getId());
        }

        int total = 0;
        List<InteractionWiredChest> seen = new ArrayList<>();
        for (HabboItem item : this.resolve(ctx, room, this.chestSource, this.chestIds)) {
            if (!(item instanceof InteractionWiredChest chest) || seen.contains(chest)) continue;
            seen.add(chest);
            // Wired only reaches a chest whose owner upgraded it to answer wired.
            if (!chest.answersWired()) continue;
            for (int baseId : types) {
                total += chest.getContents().count(ChestStorage.KIND_FURNI, baseId);
            }
        }
        // With no chest AIR counts 0 and still compares ("equal to 0" passes).
        Integer threshold = this.resolveAmount(ctx, room);
        return threshold != null && WiredComparison.compare(total, threshold, this.comparison);
    }

    private Integer resolveAmount(WiredContext ctx, Room room) {
        if (this.amountMode != WiredConditionChestHasItems.AMOUNT_VARIABLE) {
            return this.amount;
        }
        return WiredValueVariable.read(
                ctx,
                room,
                this.valueTarget,
                customItemId(this.amountVariableToken),
                WiredSourceUtil.SOURCE_TRIGGER,
                WiredSourceUtil.SOURCE_TRIGGER);
    }

    private List<HabboItem> resolve(WiredContext ctx, Room room, int source, List<Integer> picks) {
        if (source == WiredSourceUtil.SOURCE_SELECTED || source == WiredVariableOperand.SOURCE_SECONDARY_SELECTED) {
            List<HabboItem> picked = new ArrayList<>();
            for (Integer id : picks) {
                HabboItem item = room.getHabboItem(id);
                if (item != null) picked.add(item);
            }
            return WiredSourceUtil.resolveItems(ctx, WiredSourceUtil.SOURCE_SELECTED, picked);
        }
        return WiredSourceUtil.resolveItems(ctx, source, new ArrayList<>());
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public boolean saveData(WiredSettings settings) {
        int[] params = settings.getIntParams();
        this.quantifier = WiredConditionChestHasItems.normalizeChestQuantifier(settings.getQuantifierCode());
        this.amount = (params.length > 0) ? Math.max(0, params[0]) : 1;
        this.amountMode = ((params.length > 1) && params[1] == WiredConditionChestHasItems.AMOUNT_VARIABLE)
                ? WiredConditionChestHasItems.AMOUNT_VARIABLE
                : WiredConditionChestHasItems.AMOUNT_CONSTANT;
        this.valueTarget =
                WiredValueVariable.normalizeTarget((params.length > 2) ? params[2] : WiredValueVariable.TARGET_GLOBAL);
        this.comparison = (params.length > 3) ? WiredComparison.normalize(params[3]) : WiredComparison.GREATER_EQUAL;
        this.amountVariableToken = WiredConditionChestHasItems.variableTokenOf(settings);
        this.typeSource = normalizeSource(settings.furniSource(0, DEFAULT_TYPE_SOURCE), DEFAULT_TYPE_SOURCE);
        this.chestSource = normalizeSource(settings.furniSource(1, DEFAULT_CHEST_SOURCE), DEFAULT_CHEST_SOURCE);

        if (this.amountMode == WiredConditionChestHasItems.AMOUNT_VARIABLE
                && customItemId(this.amountVariableToken) <= 0) {
            return false;
        }

        return this.selectPicks(settings);
    }

    /**
     * Keeps the type picks and the chest picks, no more of each than the hotel lets a wired box select; a chest pick
     * that is not a chest refuses the save with Habbo's text.
     */
    private boolean selectPicks(WiredSettings settings) {
        int[] typePicks = (settings.getFurniIds() != null) ? settings.getFurniIds() : new int[0];
        int[] chestPicks = (settings.getFurniIds2() != null) ? settings.getFurniIds2() : new int[0];
        ConfigurationManager config = WiredPlatform.configuration();
        int cap = (config == null) ? Integer.MAX_VALUE : config.getInt("hotel.wired.furni.selection.count");
        if (typePicks.length > cap || chestPicks.length > cap) {
            return false;
        }

        Room room = (WiredPlatform.gameEnvironment() == null)
                ? null
                : WiredPlatform.gameEnvironment().getRoomManager().getRoom(this.getRoomId());
        if (room == null) {
            return false;
        }

        List<Integer> chests = new ArrayList<>();
        for (int id : chestPicks) {
            HabboItem item = room.getHabboItem(id);
            if (item instanceof InteractionWiredChest) {
                chests.add(id);
            } else if (item != null) {
                throw new WiredSaveRefusedException(WiredConditionChestHasItems.REQUIRE_CHESTS);
            }
        }
        List<Integer> types = new ArrayList<>();
        for (int id : typePicks) {
            if (room.getHabboItem(id) != null && !types.contains(id)) types.add(id);
        }

        this.typeItemIds.clear();
        this.typeItemIds.addAll(types);
        this.chestIds.clear();
        this.chestIds.addAll(chests);
        // A typed type of an older box stays until types are picked.
        if (!this.typeItemIds.isEmpty() || this.typeSource != WiredSourceUtil.SOURCE_SELECTED) {
            this.baseItemId = 0;
        }
        return true;
    }

    @Override
    public WiredConditionType getType() {
        return type;
    }

    @Override
    public String getWiredData() {
        JsonData data = new JsonData();
        data.baseItemId = this.baseItemId;
        data.amount = this.amount;
        data.comparison = this.comparison;
        data.chestIds = new ArrayList<>(this.chestIds);
        data.typeItemIds = new ArrayList<>(this.typeItemIds);
        data.furniSource = this.typeSource;
        data.furniSource2 = this.chestSource;
        data.amountMode = this.amountMode;
        data.valueTarget = this.valueTarget;
        data.amountVariableToken = this.amountVariableToken;
        data.quantifier = this.quantifier;
        return WiredManager.getGson().toJson(data);
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.onPickUp();

        String wiredData = set.getString("wired_data");
        if (wiredData == null || !wiredData.startsWith("{")) return;

        JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
        if (data == null) return;

        this.baseItemId = Math.max(0, data.baseItemId);
        this.comparison = WiredComparison.normalize(data.comparison);
        this.quantifier =
                (data.quantifier != null) ? WiredConditionChestHasItems.normalizeChestQuantifier(data.quantifier) : 0;
        if (data.chestIds != null) this.chestIds.addAll(data.chestIds);
        if (data.typeItemIds != null) this.typeItemIds.addAll(data.typeItemIds);
        if (data.furniSource != null) this.typeSource = normalizeSource(data.furniSource, DEFAULT_TYPE_SOURCE);
        if (data.furniSource2 != null) this.chestSource = normalizeSource(data.furniSource2, DEFAULT_CHEST_SOURCE);
        if (data.valueTarget != null) {
            this.amount = Math.max(0, data.amount);
            this.amountMode =
                    (data.amountMode != null && data.amountMode == WiredConditionChestHasItems.AMOUNT_VARIABLE)
                            ? WiredConditionChestHasItems.AMOUNT_VARIABLE
                            : WiredConditionChestHasItems.AMOUNT_CONSTANT;
            this.valueTarget = WiredValueVariable.normalizeTarget(data.valueTarget);
            this.amountVariableToken = (data.amountVariableToken == null) ? "" : data.amountVariableToken.trim();
        } else {
            // Saved before the value-or-variable amount: a typed amount of at least 1.
            this.amount = Math.max(1, data.amount);
        }
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(WiredManager.MAXIMUM_FURNI_SELECTION);
        message.appendInt(this.typeItemIds.size());
        for (Integer id : this.typeItemIds) {
            message.appendInt(id);
        }
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString("");
        // Official layout (Chest Has Items'): [value, valueIsVariable, variable target, comparison]; the item types
        // are the first picks, the chests the second, the variable the first variable id.
        message.appendInt(4);
        message.appendInt(this.amount);
        message.appendInt(this.amountMode);
        message.appendInt(this.valueTarget);
        message.appendInt(this.comparison);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(0);
        message.appendInt(0);
    }

    @Override
    public int[] officialFurniSources() {
        return new int[] {this.typeSource, this.chestSource};
    }

    @Override
    public int[] officialUserSources() {
        return new int[0];
    }

    @Override
    public int[] officialFurniIds2() {
        return this.chestIds.stream().mapToInt(Integer::intValue).toArray();
    }

    @Override
    public String[] officialVariableIds() {
        int id = customItemId(this.amountVariableToken);
        return id > 0 ? new String[] {String.valueOf(id)} : new String[0];
    }

    @Override
    public void onPickUp() {
        this.typeItemIds.clear();
        this.chestIds.clear();
        this.typeSource = DEFAULT_TYPE_SOURCE;
        this.chestSource = DEFAULT_CHEST_SOURCE;
        this.baseItemId = 0;
        this.amount = 1;
        this.comparison = WiredComparison.GREATER_EQUAL;
        this.amountMode = WiredConditionChestHasItems.AMOUNT_CONSTANT;
        this.valueTarget = WiredValueVariable.TARGET_GLOBAL;
        this.amountVariableToken = "";
        this.quantifier = 0;
    }

    @Override
    public int officialQuantifierCode() {
        return this.quantifier;
    }

    int getLegacyBaseItemId() {
        return this.baseItemId;
    }

    private static int normalizeSource(int value, int fallback) {
        return switch (value) {
            case WiredSourceUtil.SOURCE_TRIGGER,
                    WiredSourceUtil.SOURCE_SELECTED,
                    WiredVariableOperand.SOURCE_SECONDARY_SELECTED,
                    WiredSourceUtil.SOURCE_SELECTOR,
                    WiredSourceUtil.SOURCE_SIGNAL -> value;
            default -> fallback;
        };
    }

    private static int customItemId(String token) {
        if (token == null || !token.startsWith(CUSTOM_TOKEN_PREFIX)) {
            return 0;
        }
        try {
            return Integer.parseInt(token.substring(CUSTOM_TOKEN_PREFIX.length()));
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    static class JsonData {
        int baseItemId;
        int amount;
        int comparison;
        List<Integer> chestIds;
        List<Integer> typeItemIds;
        Integer furniSource;
        Integer furniSource2;
        Integer amountMode;
        Integer valueTarget;
        String amountVariableToken;
        Integer quantifier;
    }
}
