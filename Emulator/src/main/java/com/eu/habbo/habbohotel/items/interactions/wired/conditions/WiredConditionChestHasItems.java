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
import com.eu.habbo.habbohotel.wired.protocol.WiredOfficialVariableIds;
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
 * fixed when the box was saved. As AIR's ChestHasAmount the variable is a furni, user, global or
 * context one (official targets 0, 1, -10, -20), read on the triggering furni or user.
 *
 * <p>Official layout: {@code [value, valueIsVariable, variable target, comparison]}, the variable as the
 * first variable id. A box saved before this existed has two ints and no token, so it reads as a
 * constant and behaves exactly as it did; one saved with the user / room choice keeps it (user 1,
 * global -10). When the mode is variable and the variable cannot be read, the condition fails rather
 * than falling back to a number nobody chose.
 */
public class WiredConditionChestHasItems extends InteractionWiredCondition implements WiredOfficialVariableIds {
    public static final WiredConditionType type = WiredConditionType.CHEST_HAS_ITEMS;

    static final int AMOUNT_CONSTANT = 0;
    static final int AMOUNT_VARIABLE = 1;
    static final int TARGET_USER = 0;
    static final int TARGET_ROOM = 3;

    private static final String CUSTOM_TOKEN_PREFIX = "custom:";
    static final String REQUIRE_CHESTS = "wiredfurni.error.require_chests";

    private final List<Integer> chestIds = new ArrayList<>();
    private int amount = 1;
    private int comparison = WiredComparison.GREATER_EQUAL;
    private int amountMode = AMOUNT_CONSTANT;
    private int amountTarget = TARGET_ROOM;
    private String amountVariableToken = "";
    /** The official target the variable is read on (0 furni, 1 user, -10 global, -20 context). */
    private int valueTarget = WiredValueVariable.TARGET_GLOBAL;

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
        boolean anyChest = false;
        for (Integer id : this.chestIds) {
            HabboItem item = room.getHabboItem(id);
            anyChest |= item instanceof InteractionWiredChest;
            // Wired only reaches a chest whose owner upgraded it to answer wired.
            if (item instanceof InteractionWiredChest chest && chest.answersWired()) {
                total += chest.getContents().total(ChestStorage.KIND_CURRENCY)
                        + chest.getContents().total(ChestStorage.KIND_FURNI);
            }
        }
        // With no chest to check Habbo passes, whatever the comparison.
        if (!anyChest) {
            return true;
        }
        Integer threshold = this.resolveAmount(ctx, room);
        return threshold != null && WiredComparison.compare(total, threshold, this.comparison);
    }

    /** The typed number, or the current value of the chosen variable; null when it cannot be read. */
    private Integer resolveAmount(WiredContext ctx, Room room) {
        if (this.amountMode != AMOUNT_VARIABLE) {
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

    /** The variable id the dialog sends: the first variable id, or (older clients) the token in the string. */
    static String variableTokenOf(WiredSettings settings) {
        String[] variableIds = settings.getVariableIds();
        String raw =
                (variableIds != null && variableIds.length > 0 && variableIds[0] != null && !variableIds[0].isBlank())
                        ? variableIds[0]
                        : settings.getStringParam();
        String token = normalizeToken(raw);
        if (token.isEmpty() || token.startsWith(CUSTOM_TOKEN_PREFIX)) {
            return token;
        }
        int id = WiredValueVariable.parseId(token);
        return id > 0 ? CUSTOM_TOKEN_PREFIX + id : "";
    }

    /** Our stored user / room choice as the official target. */
    static int officialTargetOf(int amountTarget) {
        return amountTarget == TARGET_USER ? WiredValueVariable.TARGET_USER : WiredValueVariable.TARGET_GLOBAL;
    }

    @Override
    public String[] officialVariableIds() {
        int id = customItemId(this.amountVariableToken);
        return id > 0 ? new String[] {String.valueOf(id)} : new String[0];
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
        this.amountMode = ((params.length > 1) && params[1] == AMOUNT_VARIABLE) ? AMOUNT_VARIABLE : AMOUNT_CONSTANT;
        this.valueTarget =
                WiredValueVariable.normalizeTarget((params.length > 2) ? params[2] : WiredValueVariable.TARGET_GLOBAL);
        this.amountTarget = this.valueTarget == WiredValueVariable.TARGET_USER ? TARGET_USER : TARGET_ROOM;
        this.comparison = (params.length > 3) ? WiredComparison.normalize(params[3]) : WiredComparison.GREATER_EQUAL;
        this.amountVariableToken = variableTokenOf(settings);

        // A variable amount that names no variable would silently behave as a constant, so it is
        // refused at the door instead of being saved as something the dialog did not say.
        if (this.amountMode == AMOUNT_VARIABLE && customItemId(this.amountVariableToken) <= 0) {
            return false;
        }

        return this.selectChests(settings);
    }

    /**
     * Keeps the selected chests, no more of them than the hotel lets a wired box select; a picked furni
     * that is not a chest refuses the save with Habbo's text.
     */
    private boolean selectChests(WiredSettings settings) {
        int[] furniIds = (settings.getFurniIds() != null) ? settings.getFurniIds() : new int[0];
        ConfigurationManager config = WiredPlatform.configuration();
        int cap = (config == null) ? Integer.MAX_VALUE : config.getInt("hotel.wired.furni.selection.count");
        if (furniIds.length > cap) {
            return false;
        }

        Room room = (WiredPlatform.gameEnvironment() == null)
                ? null
                : WiredPlatform.gameEnvironment().getRoomManager().getRoom(this.getRoomId());
        if (room == null) {
            return false;
        }

        // Habbo refuses a picked furni that is not a chest; an id the room no longer knows (picked up
        // meanwhile) is simply left out.
        List<Integer> chests = new ArrayList<>();
        for (int id : furniIds) {
            HabboItem item = room.getHabboItem(id);
            if (item instanceof InteractionWiredChest) {
                chests.add(id);
            } else if (item != null) {
                throw new WiredSaveRefusedException(REQUIRE_CHESTS);
            }
        }
        this.chestIds.clear();
        this.chestIds.addAll(chests);
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
                        this.amountVariableToken,
                        this.valueTarget));
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
        // Boxes saved with the user / room choice read their variable where they did.
        this.valueTarget = (data.valueTarget != null)
                ? WiredValueVariable.normalizeTarget(data.valueTarget)
                : officialTargetOf(this.amountTarget);
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
        message.appendString("");
        // Official layout: [value, valueIsVariable, variable target (0 furni, 1 user, -10 global, -20
        // context), comparison]; the chests are the picks, the variable the first variable id.
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
    public void onPickUp() {
        this.chestIds.clear();
        this.amount = 1;
        this.comparison = WiredComparison.GREATER_EQUAL;
        this.amountMode = AMOUNT_CONSTANT;
        this.amountTarget = TARGET_ROOM;
        this.amountVariableToken = "";
        this.valueTarget = WiredValueVariable.TARGET_GLOBAL;
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
        Integer valueTarget;

        public JsonData(
                int amount,
                int comparison,
                List<Integer> chestIds,
                int amountMode,
                int amountTarget,
                String amountVariableToken,
                Integer valueTarget) {
            this.amount = amount;
            this.comparison = comparison;
            this.chestIds = chestIds;
            this.amountMode = amountMode;
            this.amountTarget = amountTarget;
            this.amountVariableToken = amountVariableToken;
            this.valueTarget = valueTarget;
        }
    }
}
