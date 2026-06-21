package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.bots.Bot;
import com.eu.habbo.habbohotel.items.FurnitureType;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredCondition;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.pets.Pet;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomRightLevels;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.rooms.RoomUnitStatus;
import com.eu.habbo.habbohotel.users.DanceType;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredConditionType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredContextVariableSupport;
import com.eu.habbo.habbohotel.wired.core.WiredFreezeUtil;
import com.eu.habbo.habbohotel.wired.core.WiredInternalVariableSupport;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import com.eu.habbo.messages.ServerMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class WiredConditionHasVariable extends InteractionWiredCondition {
    private static final Logger LOGGER = LoggerFactory.getLogger(WiredConditionHasVariable.class);

    protected static final int TARGET_USER = 0;
    protected static final int TARGET_FURNI = 1;
    protected static final int TARGET_CONTEXT = 2;
    protected static final int TARGET_ROOM = 3;
    protected static final int QUANTIFIER_ALL = 0;
    protected static final int QUANTIFIER_ANY = 1;
    private static final String CUSTOM_TOKEN_PREFIX = "custom:";
    private static final String INTERNAL_TOKEN_PREFIX = "internal:";

    public static final WiredConditionType type = WiredConditionType.HAS_VAR;

    protected final Set<HabboItem> selectedItems = new LinkedHashSet<>();
    protected int targetType = TARGET_USER;
    protected int userSource = WiredSourceUtil.SOURCE_TRIGGER;
    protected int furniSource = WiredSourceUtil.SOURCE_TRIGGER;
    protected int quantifier = QUANTIFIER_ALL;
    protected String variableToken = "";
    protected int variableItemId = 0;

    public WiredConditionHasVariable(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredConditionHasVariable(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public WiredConditionType getType() {
        return type;
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        this.refresh();

        List<HabboItem> serializedItems = new ArrayList<>();
        if (this.targetType == TARGET_FURNI && this.furniSource == WiredSourceUtil.SOURCE_SELECTED) {
            serializedItems.addAll(this.selectedItems);
        }

        message.appendBoolean(false);
        message.appendInt(WiredManager.MAXIMUM_FURNI_SELECTION);
        message.appendInt(serializedItems.size());

        for (HabboItem item : serializedItems) {
            message.appendInt(item.getId());
        }

        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString(this.variableToken == null ? "" : this.variableToken);
        message.appendInt(4);
        message.appendInt(this.targetType);
        message.appendInt(this.userSource);
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
        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());

        this.targetType = (params.length > 0) ? normalizeTargetType(params[0]) : TARGET_USER;
        this.userSource = (params.length > 1) ? normalizeUserSource(params[1]) : WiredSourceUtil.SOURCE_TRIGGER;
        this.furniSource = (params.length > 2) ? normalizeFurniSource(params[2]) : WiredSourceUtil.SOURCE_TRIGGER;
        this.quantifier = (params.length > 3) ? normalizeQuantifier(params[3]) : QUANTIFIER_ALL;
        this.setVariableToken(normalizeVariableToken(settings.getStringParam()));

        if (this.variableToken.isEmpty()) {
            return false;
        }

        this.selectedItems.clear();

        if (this.targetType == TARGET_FURNI && this.furniSource == WiredSourceUtil.SOURCE_SELECTED && room != null) {
            int[] furniIds = settings.getFurniIds();
            if (furniIds.length > Emulator.getConfig().getInt("hotel.wired.furni.selection.count")) {
                return false;
            }

            for (int furniId : furniIds) {
                HabboItem item = room.getHabboItem(furniId);

                if (item != null) {
                    this.selectedItems.add(item);
                }
            }
        }

        return true;
    }

    @Override
    public boolean evaluate(WiredContext ctx) {
        return this.evaluateWithNegation(ctx, false);
    }

    protected boolean evaluateWithNegation(WiredContext ctx, boolean negative) {
        Room room = ctx.room();

        if (room == null || this.variableToken == null || this.variableToken.isEmpty()) {
            return false;
        }

        return switch (this.targetType) {
            case TARGET_FURNI -> this.evaluateFurniTargets(ctx, room, negative);
            case TARGET_CONTEXT -> {
                boolean contextMatch = this.matchesContext(ctx, room);
                yield negative ? !contextMatch : contextMatch;
            }
            case TARGET_ROOM -> {
                boolean roomMatch = this.matchesRoom(room);
                yield negative ? !roomMatch : roomMatch;
            }
            default -> this.evaluateUserTargets(ctx, room, negative);
        };
    }

    private boolean evaluateUserTargets(WiredContext ctx, Room room, boolean negative) {
        List<RoomUnit> targets = WiredSourceUtil.resolveUsers(ctx, this.userSource);
        if (targets.isEmpty()) return false;

        boolean match = (this.quantifier == QUANTIFIER_ANY)
            ? this.matchesAnyUser(room, targets)
            : this.matchesAllUsers(room, targets);

        return negative ? !match : match;
    }

    private boolean evaluateFurniTargets(WiredContext ctx, Room room, boolean negative) {
        this.refresh();

        List<HabboItem> targets = WiredSourceUtil.resolveItems(ctx, this.furniSource, this.selectedItems);
        if (targets.isEmpty()) return false;

        boolean match = (this.quantifier == QUANTIFIER_ANY)
            ? this.matchesAnyFurni(room, targets)
            : this.matchesAllFurni(room, targets);

        return negative ? !match : match;
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public String getWiredData() {
        this.refresh();

        List<Integer> itemIds = new ArrayList<>();
        for (HabboItem item : this.selectedItems) {
            if (item != null) itemIds.add(item.getId());
        }

        return WiredManager.getGson().toJson(new JsonData(
            itemIds,
            this.targetType,
            this.variableToken,
            this.variableItemId,
            this.userSource,
            this.furniSource,
            this.quantifier
        ));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.onPickUp();

        String wiredData = set.getString("wired_data");
        if (wiredData == null || wiredData.isEmpty()) return;

        try {
            if (wiredData.startsWith("{")) {
                JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);

                if (data == null) return;

                this.targetType = normalizeTargetType(data.targetType);
                this.userSource = normalizeUserSource(data.userSource);
                this.furniSource = normalizeFurniSource(data.furniSource);
                this.quantifier = normalizeQuantifier(data.quantifier);
                this.setVariableToken(normalizeVariableToken((data.variableToken != null) ? data.variableToken : ((data.variableItemId > 0) ? String.valueOf(data.variableItemId) : "")));

                if (room != null && data.itemIds != null) {
                    for (Integer itemId : data.itemIds) {
                        if (itemId == null || itemId <= 0) continue;

                        HabboItem item = room.getHabboItem(itemId);
                        if (item != null) this.selectedItems.add(item);
                    }
                }

                return;
            }

            this.setVariableToken(normalizeVariableToken(wiredData));
        } catch (Exception e) {
            LOGGER.error("Failed to load wired variable condition data for item {}", this.getId(), e);
            this.onPickUp();
        }
    }

    @Override
    public void onPickUp() {
        this.targetType = TARGET_USER;
        this.userSource = WiredSourceUtil.SOURCE_TRIGGER;
        this.furniSource = WiredSourceUtil.SOURCE_TRIGGER;
        this.quantifier = QUANTIFIER_ALL;
        this.selectedItems.clear();
        this.setVariableToken("");
    }

    protected boolean matchesAnyUser(Room room, List<RoomUnit> targets) {
        for (RoomUnit roomUnit : targets) {
            if (this.matchesUser(room, roomUnit)) {
                return true;
            }
        }

        return false;
    }

    protected boolean matchesAllUsers(Room room, List<RoomUnit> targets) {
        for (RoomUnit roomUnit : targets) {
            if (!this.matchesUser(room, roomUnit)) {
                return false;
            }
        }

        return true;
    }

    protected boolean matchesAnyFurni(Room room, List<HabboItem> targets) {
        for (HabboItem item : targets) {
            if (this.matchesFurni(room, item)) {
                return true;
            }
        }

        return false;
    }

    protected boolean matchesAllFurni(Room room, List<HabboItem> targets) {
        for (HabboItem item : targets) {
            if (!this.matchesFurni(room, item)) {
                return false;
            }
        }

        return true;
    }

    protected boolean matchesUser(Room room, RoomUnit roomUnit) {
        if (room == null || roomUnit == null) {
            return false;
        }

        if (isCustomVariableToken(this.variableToken)) {
            Habbo habbo = room.getHabbo(roomUnit);

            return habbo != null && room.getUserVariableManager().hasVariable(habbo.getHabboInfo().getId(), this.variableItemId);
        }

        if (isInternalVariableToken(this.variableToken)) {
            return this.hasUserInternalVariable(room, roomUnit, getInternalVariableKey(this.variableToken));
        }

        return false;
    }

    protected boolean matchesFurni(Room room, HabboItem item) {
        if (room == null || item == null) {
            return false;
        }

        if (isCustomVariableToken(this.variableToken)) {
            return room.getFurniVariableManager().hasVariable(item.getId(), this.variableItemId);
        }

        if (isInternalVariableToken(this.variableToken)) {
            return this.hasFurniInternalVariable(item, getInternalVariableKey(this.variableToken));
        }

        return false;
    }

    protected boolean matchesContext(WiredContext ctx, Room room) {
        if (ctx == null || room == null) {
            return false;
        }

        if (isCustomVariableToken(this.variableToken)) {
            return WiredContextVariableSupport.hasVariable(ctx, this.variableItemId);
        }

        if (isInternalVariableToken(this.variableToken)) {
            return WiredInternalVariableSupport.readContextValue(ctx, getInternalVariableKey(this.variableToken)) != null;
        }

        return false;
    }

    protected boolean matchesRoom(Room room) {
        if (room == null) {
            return false;
        }

        if (isCustomVariableToken(this.variableToken)) {
            return room.getRoomVariableManager().hasVariable(this.variableItemId);
        }

        if (isInternalVariableToken(this.variableToken)) {
            return this.hasRoomInternalVariable(getInternalVariableKey(this.variableToken));
        }

        return false;
    }

    protected boolean hasUserInternalVariable(Room room, RoomUnit roomUnit, String key) {
        return WiredInternalVariableSupport.hasUserValue(room, roomUnit, key);
    }

    protected boolean hasFurniInternalVariable(HabboItem item, String key) {
        return WiredInternalVariableSupport.hasFurniValue(item, key);
    }

    protected boolean hasRoomInternalVariable(String key) {
        return WiredInternalVariableSupport.hasRoomValue(Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId()), key);
    }

    protected void refresh() {
        Set<HabboItem> staleItems = new HashSet<>();
        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());

        if (room == null) {
            staleItems.addAll(this.selectedItems);
        } else {
            for (HabboItem item : this.selectedItems) {
                if (item == null || item.getRoomId() != room.getId()) {
                    staleItems.add(item);
                }
            }
        }

        this.selectedItems.removeAll(staleItems);
    }

    protected void setVariableToken(String token) {
        this.variableToken = normalizeVariableToken(token);
        this.variableItemId = getCustomItemId(this.variableToken);
    }

    protected boolean hasRoomEntryMethod(Habbo habbo) {
        if (habbo == null) return false;

        String roomEntryMethod = habbo.getHabboInfo().getRoomEntryMethod();

        return roomEntryMethod != null && !roomEntryMethod.trim().isEmpty() && !"unknown".equalsIgnoreCase(roomEntryMethod);
    }

    protected TeamEffectData getTeamEffectData(int effectValue) {
        if (effectValue <= 0) return null;

        if (effectValue >= 223 && effectValue <= 226) return new TeamEffectData(effectValue - 222, 0);
        if (effectValue >= 33 && effectValue <= 36) return new TeamEffectData(effectValue - 32, 1);
        if (effectValue >= 40 && effectValue <= 43) return new TeamEffectData(effectValue - 39, 2);

        return null;
    }

    protected static int normalizeTargetType(int value) {
        return switch (value) {
            case TARGET_FURNI, TARGET_CONTEXT, TARGET_ROOM -> value;
            default -> TARGET_USER;
        };
    }

    protected static int normalizeQuantifier(int value) {
        return (value == QUANTIFIER_ANY) ? QUANTIFIER_ANY : QUANTIFIER_ALL;
    }

    protected static int normalizeUserSource(int value) {
        return WiredSourceUtil.isDefaultUserSource(value) ? value : WiredSourceUtil.SOURCE_TRIGGER;
    }

    protected static int normalizeFurniSource(int value) {
        return switch (value) {
            case WiredSourceUtil.SOURCE_SELECTED, WiredSourceUtil.SOURCE_SELECTOR, WiredSourceUtil.SOURCE_SIGNAL -> value;
            default -> WiredSourceUtil.SOURCE_TRIGGER;
        };
    }

    protected static boolean isCustomVariableToken(String token) {
        return token != null && token.startsWith(CUSTOM_TOKEN_PREFIX);
    }

    protected static boolean isInternalVariableToken(String token) {
        return token != null && token.startsWith(INTERNAL_TOKEN_PREFIX);
    }

    protected static int getCustomItemId(String token) {
        if (!isCustomVariableToken(token)) return 0;

        try {
            return Integer.parseInt(token.substring(CUSTOM_TOKEN_PREFIX.length()));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    protected static String getInternalVariableKey(String token) {
        return isInternalVariableToken(token) ? WiredInternalVariableSupport.normalizeKey(token.substring(INTERNAL_TOKEN_PREFIX.length())) : "";
    }

    protected static String normalizeVariableToken(String token) {
        if (token == null) return "";

        String normalized = token.trim();
        if (normalized.isEmpty()) return "";
        if (isCustomVariableToken(normalized)) return normalized;
        if (isInternalVariableToken(normalized)) return INTERNAL_TOKEN_PREFIX + WiredInternalVariableSupport.normalizeKey(normalized.substring(INTERNAL_TOKEN_PREFIX.length()));

        try {
            int parsed = Integer.parseInt(normalized);
            return (parsed > 0) ? (CUSTOM_TOKEN_PREFIX + parsed) : "";
        } catch (NumberFormatException e) {
            return "";
        }
    }

    protected static class JsonData {
        List<Integer> itemIds;
        int targetType;
        String variableToken;
        int variableItemId;
        int userSource;
        int furniSource;
        int quantifier;

        public JsonData(List<Integer> itemIds, int targetType, String variableToken, int variableItemId, int userSource, int furniSource, int quantifier) {
            this.itemIds = itemIds;
            this.targetType = targetType;
            this.variableToken = variableToken;
            this.variableItemId = variableItemId;
            this.userSource = userSource;
            this.furniSource = furniSource;
            this.quantifier = quantifier;
        }
    }

    protected static class TeamEffectData {
        final int colorId;
        final int typeId;

        protected TeamEffectData(int colorId, int typeId) {
            this.colorId = colorId;
            this.typeId = typeId;
        }
    }
}
