package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredCondition;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredComparison;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.ChestStorage;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.InteractionWiredChest;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.rooms.WiredVariableDefinitionInfo;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredConditionType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import com.eu.habbo.messages.ServerMessage;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Chest Has X Items (furni classname {@code wf_cnd_chest_has_items}). Passes when the total contents
 * (currency + furni) of the selected {@link InteractionWiredChest}(s) compare to an amount.
 *
 * <p>That amount can be a number you type or the current value of a variable, which is what the
 * official calls a value-or-variable section - a threshold that moves with the game instead of being
 * fixed when the box was saved. This reads a <b>room</b> or <b>user</b> variable; the official's furni
 * and context targets are not offered, since a chest threshold has no natural per-furni reading.
 *
 * <p>A box saved before this existed has two ints and no token, so it reads as a constant and behaves
 * exactly as it did. When the mode is variable and the variable cannot be read, the condition fails
 * rather than falling back to a number nobody chose.
 */
public class WiredConditionChestHasItems extends InteractionWiredCondition {
    public static final WiredConditionType type = WiredConditionType.CHEST_HAS_ITEMS;

    static final int AMOUNT_CONSTANT = 0;
    static final int AMOUNT_VARIABLE = 1;
    static final int TARGET_USER = 0;
    static final int TARGET_ROOM = 3;

    private static final String CUSTOM_TOKEN_PREFIX = "custom:";

    private final List<Integer> chestIds = new ArrayList<>();
    private int amount = 1;
    private int comparison = WiredComparison.GREATER_EQUAL;
    private int amountMode = AMOUNT_CONSTANT;
    private int amountTarget = TARGET_ROOM;
    private String amountVariableToken = "";

    public WiredConditionChestHasItems(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredConditionChestHasItems(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean evaluate(WiredContext ctx) {
        Room room = ctx.room();
        if (room == null) return false;

        int total = 0;
        for (Integer id : this.chestIds) {
            HabboItem item = room.getHabboItem(id);
            // Wired only reaches a chest whose owner upgraded it to answer wired.
            if (item instanceof InteractionWiredChest chest && chest.answersWired()) {
                total += chest.getContents().total(ChestStorage.KIND_CURRENCY)
                        + chest.getContents().total(ChestStorage.KIND_FURNI);
            }
        }
        Integer threshold = this.resolveAmount(ctx, room);
        return threshold != null && WiredComparison.compare(total, threshold, this.comparison);
    }

    /** The typed number, or the current value of the chosen variable; null when it cannot be read. */
    private Integer resolveAmount(WiredContext ctx, Room room) {
        if (this.amountMode != AMOUNT_VARIABLE) {
            return this.amount;
        }

        int variableItemId = customItemId(this.amountVariableToken);
        if (variableItemId <= 0) {
            return null;
        }

        if (this.amountTarget == TARGET_ROOM) {
            WiredVariableDefinitionInfo definition =
                    room.getRoomVariableManager().getDefinitionInfo(variableItemId);
            return (definition != null && definition.hasValue())
                    ? room.getRoomVariableManager().getCurrentValue(variableItemId)
                    : null;
        }

        WiredVariableDefinitionInfo definition = room.getUserVariableManager().getDefinitionInfo(variableItemId);
        if (definition == null || !definition.hasValue()) {
            return null;
        }

        for (RoomUnit roomUnit : WiredSourceUtil.resolveUsers(ctx, WiredSourceUtil.SOURCE_TRIGGER)) {
            Habbo habbo = (roomUnit != null) ? room.getHabbo(roomUnit) : null;
            if (habbo != null) {
                Integer value = room.getUserVariableManager()
                        .getCurrentValue(habbo.getHabboInfo().getId(), variableItemId);
                if (value != null) {
                    return value;
                }
            }
        }

        return null;
    }

    /** Tokens are {@code custom:<itemId>}; anything else names no variable this box can read. */
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

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public boolean saveData(WiredSettings settings) {
        int[] params = settings.getIntParams();
        this.amount = (params.length > 0) ? Math.max(0, params[0]) : 1;
        this.comparison = (params.length > 1) ? WiredComparison.normalize(params[1]) : WiredComparison.GREATER_EQUAL;
        this.amountMode = ((params.length > 2) && params[2] == AMOUNT_VARIABLE) ? AMOUNT_VARIABLE : AMOUNT_CONSTANT;
        this.amountTarget = ((params.length > 3) && params[3] == TARGET_USER) ? TARGET_USER : TARGET_ROOM;
        this.amountVariableToken = normalizeToken(settings.getStringParam());

        // A variable amount that names no variable would silently behave as a constant, so it is
        // refused at the door instead of being saved as something the dialog did not say.
        if (this.amountMode == AMOUNT_VARIABLE && customItemId(this.amountVariableToken) <= 0) {
            return false;
        }

        this.chestIds.clear();
        if (settings.getFurniIds() != null) {
            for (int id : settings.getFurniIds()) {
                this.chestIds.add(id);
            }
        }
        return true;
    }

    @Override
    public WiredConditionType getType() {
        return type;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson()
                .toJson(new JsonData(
                        this.amount,
                        this.comparison,
                        this.chestIds,
                        this.amountMode,
                        this.amountTarget,
                        this.amountVariableToken));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.onPickUp();

        String wiredData = set.getString("wired_data");
        if (wiredData == null || !wiredData.startsWith("{")) return;

        JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
        if (data == null) return;

        this.amount = Math.max(0, data.amount);
        this.comparison = WiredComparison.normalize(data.comparison);
        this.amountMode = (data.amountMode == AMOUNT_VARIABLE) ? AMOUNT_VARIABLE : AMOUNT_CONSTANT;
        this.amountTarget = (data.amountTarget == TARGET_USER) ? TARGET_USER : TARGET_ROOM;
        this.amountVariableToken = normalizeToken(data.amountVariableToken);
        if (data.chestIds != null) {
            this.chestIds.addAll(data.chestIds);
        }
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(WiredManager.MAXIMUM_FURNI_SELECTION);
        message.appendInt(this.chestIds.size());
        for (Integer id : this.chestIds) {
            message.appendInt(id);
        }
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString(this.amountVariableToken);
        message.appendInt(4);
        message.appendInt(this.amount);
        message.appendInt(this.comparison);
        message.appendInt(this.amountMode);
        message.appendInt(this.amountTarget);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(0);
        message.appendInt(0);
    }

    @Override
    public void onPickUp() {
        this.chestIds.clear();
        this.amount = 1;
        this.comparison = WiredComparison.GREATER_EQUAL;
        this.amountMode = AMOUNT_CONSTANT;
        this.amountTarget = TARGET_ROOM;
        this.amountVariableToken = "";
    }

    /** Normalises the token the dialog sends; a blank one simply names no variable. */
    private static String normalizeToken(String token) {
        return (token == null) ? "" : token.trim();
    }

    static class JsonData {
        int amount;
        int comparison;
        List<Integer> chestIds;
        int amountMode;
        int amountTarget;
        String amountVariableToken;

        public JsonData(
                int amount,
                int comparison,
                List<Integer> chestIds,
                int amountMode,
                int amountTarget,
                String amountVariableToken) {
            this.amount = amount;
            this.comparison = comparison;
            this.chestIds = chestIds;
            this.amountMode = amountMode;
            this.amountTarget = amountTarget;
            this.amountVariableToken = amountVariableToken;
        }
    }
}
