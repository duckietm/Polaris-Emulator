package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.bots.Bot;
import com.eu.habbo.habbohotel.games.Game;
import com.eu.habbo.habbohotel.games.GamePlayer;
import com.eu.habbo.habbohotel.games.GameTeam;
import com.eu.habbo.habbohotel.games.GameTeamColors;
import com.eu.habbo.habbohotel.games.battlebanzai.BattleBanzaiGame;
import com.eu.habbo.habbohotel.games.freeze.FreezeGame;
import com.eu.habbo.habbohotel.games.wired.WiredGame;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.pets.Pet;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.rooms.WiredVariableDefinitionInfo;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredConditionType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredContextVariableSupport;
import com.eu.habbo.habbohotel.wired.core.WiredInternalVariableSupport;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.util.HotelDateTimeUtil;
import gnu.trove.set.hash.THashSet;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.ZonedDateTime;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

public class WiredConditionVariableValueMatch extends WiredConditionHasVariable {
    public static final WiredConditionType type = WiredConditionType.VAR_VAL_MATCH;

    private static final int TARGET_CONTEXT = 2;
    private static final int SOURCE_SECONDARY_SELECTED = 101;
    private static final int REFERENCE_CONSTANT = 0;
    private static final int REFERENCE_VARIABLE = 1;
    private static final int COMPARISON_GREATER_THAN = 0;
    private static final int COMPARISON_GREATER_THAN_OR_EQUAL = 1;
    private static final int COMPARISON_EQUAL = 2;
    private static final int COMPARISON_LESS_THAN_OR_EQUAL = 3;
    private static final int COMPARISON_LESS_THAN = 4;
    private static final int COMPARISON_NOT_EQUAL = 5;
    private static final String DELIM = "\t";
    private static final String FURNI_DELIM = ";";
    static final int MAX_ABS_REFERENCE_CONSTANT = 1_000_000_000;

    protected int comparison = COMPARISON_EQUAL;
    protected int referenceMode = REFERENCE_CONSTANT;
    protected int referenceConstantValue = 0;
    protected int referenceTargetType = TARGET_USER;
    protected int referenceUserSource = WiredSourceUtil.SOURCE_TRIGGER;
    protected int referenceFurniSource = WiredSourceUtil.SOURCE_TRIGGER;
    protected String referenceVariableToken = "";
    protected int referenceVariableItemId = 0;
    protected final THashSet<HabboItem> referenceSelectedItems = new THashSet<>();

    public WiredConditionVariableValueMatch(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredConditionVariableValueMatch(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public WiredConditionType getType() {
        return type;
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        this.refresh();
        this.refreshReferenceItems();

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
        message.appendString(this.serializeStringData());
        message.appendInt(10);
        message.appendInt(this.targetType);
        message.appendInt(this.comparison);
        message.appendInt(this.referenceMode);
        message.appendInt(this.referenceConstantValue);
        message.appendInt(this.referenceTargetType);
        message.appendInt(this.userSource);
        message.appendInt(this.furniSource);
        message.appendInt(this.referenceUserSource);
        message.appendInt(this.referenceFurniSource);
        message.appendInt(this.quantifier);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(0);
        message.appendInt(0);
    }

    @Override
    public boolean saveData(WiredSettings settings) {
        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());
        if (room == null) return false;

        int[] params = settings.getIntParams();
        String[] stringParts = this.parseStringData(settings.getStringParam());
        int nextTargetType = normalizeTargetTypeExtended(param(params, 0, TARGET_USER));
        int nextComparison = normalizeComparison(param(params, 1, COMPARISON_EQUAL));
        int nextReferenceMode = normalizeReferenceMode(param(params, 2, REFERENCE_CONSTANT));
        int nextReferenceConstantValue = normalizeReferenceConstantValue(param(params, 3, 0));
        int nextReferenceTargetType = normalizeTargetTypeExtended(param(params, 4, TARGET_USER));
        int nextUserSource = normalizeUserSource(param(params, 5, WiredSourceUtil.SOURCE_TRIGGER));
        int nextFurniSource = normalizeFurniSource(param(params, 6, WiredSourceUtil.SOURCE_TRIGGER));
        int nextReferenceUserSource = normalizeUserSource(param(params, 7, WiredSourceUtil.SOURCE_TRIGGER));
        int nextReferenceFurniSource = normalizeReferenceFurniSource(param(params, 8, WiredSourceUtil.SOURCE_TRIGGER));
        int nextQuantifier = normalizeQuantifier(param(params, 9, QUANTIFIER_ALL));
        String nextVariableToken = normalizeVariableToken((stringParts.length > 0) ? stringParts[0] : settings.getStringParam());
        String nextReferenceVariableToken = normalizeVariableToken((stringParts.length > 1) ? stringParts[1] : "");

        if (!this.isValidSource(room, nextTargetType, nextVariableToken)) return false;
        if (nextReferenceMode == REFERENCE_VARIABLE && !this.isValidReference(room, nextReferenceTargetType, nextReferenceVariableToken)) return false;

        int selectionLimit = Emulator.getConfig().getInt("hotel.wired.furni.selection.count");
        List<HabboItem> nextSelectedItems = (nextTargetType == TARGET_FURNI && nextFurniSource == WiredSourceUtil.SOURCE_SELECTED)
            ? this.parseItems(settings.getFurniIds(), room)
            : new ArrayList<>();
        List<HabboItem> nextReferenceItems = (nextReferenceMode == REFERENCE_VARIABLE && nextReferenceTargetType == TARGET_FURNI && nextReferenceFurniSource == SOURCE_SECONDARY_SELECTED)
            ? this.parseItems((stringParts.length > 2) ? stringParts[2] : "", room)
            : new ArrayList<>();

        if (nextSelectedItems.size() > selectionLimit || nextReferenceItems.size() > selectionLimit) return false;

        this.selectedItems.clear();
        this.selectedItems.addAll(nextSelectedItems);
        this.referenceSelectedItems.clear();
        this.referenceSelectedItems.addAll(nextReferenceItems);
        this.targetType = nextTargetType;
        this.comparison = nextComparison;
        this.referenceMode = nextReferenceMode;
        this.referenceConstantValue = nextReferenceConstantValue;
        this.referenceTargetType = nextReferenceTargetType;
        this.userSource = nextUserSource;
        this.furniSource = nextFurniSource;
        this.referenceUserSource = nextReferenceUserSource;
        this.referenceFurniSource = nextReferenceFurniSource;
        this.quantifier = nextQuantifier;
        this.setVariableToken(nextVariableToken);
        this.setReferenceVariableToken(nextReferenceVariableToken);

        return true;
    }

    @Override
    public boolean evaluate(WiredContext ctx) {
        if (ctx == null) {
            return false;
        }

        Room room = ctx.room();

        if (room == null || this.variableToken == null || this.variableToken.isEmpty()) {
            return false;
        }

        return switch (this.targetType) {
            case TARGET_FURNI -> this.evaluateFurniTargets(ctx, room);
            case TARGET_ROOM -> this.evaluateRoomTarget(ctx, room);
            case TARGET_CONTEXT -> this.evaluateContextTarget(ctx, room);
            default -> this.evaluateUserTargets(ctx, room);
        };
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public String getWiredData() {
        this.refresh();
        this.refreshReferenceItems();

        return WiredManager.getGson().toJson(new JsonData(
            this.targetType,
            this.variableToken,
            this.variableItemId,
            this.comparison,
            this.referenceMode,
            this.referenceConstantValue,
            this.referenceTargetType,
            this.referenceVariableToken,
            this.referenceVariableItemId,
            this.userSource,
            this.furniSource,
            this.referenceUserSource,
            this.referenceFurniSource,
            this.quantifier,
            this.toIds(this.selectedItems),
            this.toIds(this.referenceSelectedItems)
        ));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.onPickUp();

        String wiredData = set.getString("wired_data");
        if (wiredData == null || wiredData.isEmpty() || !wiredData.startsWith("{")) return;

        JsonData data;
        try {
            data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
        } catch (RuntimeException exception) {
            this.onPickUp();
            return;
        }

        if (data == null) return;

        this.targetType = normalizeTargetTypeExtended(data.targetType);
        this.setVariableToken(normalizeVariableToken((data.variableToken != null) ? data.variableToken : ((data.variableItemId > 0) ? String.valueOf(data.variableItemId) : "")));
        this.comparison = normalizeComparison(data.comparison);
        this.referenceMode = normalizeReferenceMode(data.referenceMode);
        this.referenceConstantValue = normalizeReferenceConstantValue(data.referenceConstantValue);
        this.referenceTargetType = normalizeTargetTypeExtended(data.referenceTargetType);
        this.setReferenceVariableToken(normalizeVariableToken((data.referenceVariableToken != null) ? data.referenceVariableToken : ((data.referenceVariableItemId > 0) ? String.valueOf(data.referenceVariableItemId) : "")));
        this.userSource = normalizeUserSource(data.userSource);
        this.furniSource = normalizeFurniSource(data.furniSource);
        this.referenceUserSource = normalizeUserSource(data.referenceUserSource);
        this.referenceFurniSource = normalizeReferenceFurniSource(data.referenceFurniSource);
        this.quantifier = normalizeQuantifier(data.quantifier);

        if (room == null) return;

        this.selectedItems.addAll(this.parseItems(data.selectedItemIds, room));
        this.referenceSelectedItems.addAll(this.parseItems(data.referenceSelectedItemIds, room));
    }

    @Override
    public void onPickUp() {
        super.onPickUp();
        this.comparison = COMPARISON_EQUAL;
        this.referenceMode = REFERENCE_CONSTANT;
        this.referenceConstantValue = 0;
        this.referenceTargetType = TARGET_USER;
        this.referenceUserSource = WiredSourceUtil.SOURCE_TRIGGER;
        this.referenceFurniSource = WiredSourceUtil.SOURCE_TRIGGER;
        this.referenceSelectedItems.clear();
        this.setReferenceVariableToken("");
    }

    public boolean requiresTriggeringUser() {
        return (this.targetType == TARGET_USER && this.userSource == WiredSourceUtil.SOURCE_TRIGGER)
            || (this.referenceMode == REFERENCE_VARIABLE && this.referenceTargetType == TARGET_USER && this.referenceUserSource == WiredSourceUtil.SOURCE_TRIGGER);
    }

    private boolean evaluateUserTargets(WiredContext ctx, Room room) {
        List<RoomUnit> targets = WiredSourceUtil.resolveUsers(ctx, this.userSource);
        if (targets.isEmpty()) return false;

        ReferenceSnapshot references = this.resolveReferences(ctx, room);

        if (this.quantifier == QUANTIFIER_ANY) {
            int index = 0;
            for (RoomUnit roomUnit : targets) {
                Integer currentValue = this.readUserValue(room, roomUnit);
                Integer referenceValue = this.referenceFor(references, roomUnit != null ? roomUnit.getId() : 0, TARGET_USER, index++);

                if (this.matchesComparison(currentValue, referenceValue)) return true;
            }

            return false;
        }

        int index = 0;
        for (RoomUnit roomUnit : targets) {
            Integer currentValue = this.readUserValue(room, roomUnit);
            Integer referenceValue = this.referenceFor(references, roomUnit != null ? roomUnit.getId() : 0, TARGET_USER, index++);

            if (!this.matchesComparison(currentValue, referenceValue)) return false;
        }

        return true;
    }

    private boolean evaluateFurniTargets(WiredContext ctx, Room room) {
        this.refresh();

        List<HabboItem> targets = WiredSourceUtil.resolveItems(ctx, this.furniSource, this.selectedItems);
        if (targets.isEmpty()) return false;

        ReferenceSnapshot references = this.resolveReferences(ctx, room);

        if (this.quantifier == QUANTIFIER_ANY) {
            int index = 0;
            for (HabboItem item : targets) {
                Integer currentValue = this.readFurniValue(room, item);
                Integer referenceValue = this.referenceFor(references, item != null ? item.getId() : 0, TARGET_FURNI, index++);

                if (this.matchesComparison(currentValue, referenceValue)) return true;
            }

            return false;
        }

        int index = 0;
        for (HabboItem item : targets) {
            Integer currentValue = this.readFurniValue(room, item);
            Integer referenceValue = this.referenceFor(references, item != null ? item.getId() : 0, TARGET_FURNI, index++);

            if (!this.matchesComparison(currentValue, referenceValue)) return false;
        }

        return true;
    }

    private boolean evaluateRoomTarget(WiredContext ctx, Room room) {
        Integer currentValue = this.readRoomValue(room);
        Integer referenceValue = this.referenceFor(this.resolveReferences(ctx, room), room.getId(), TARGET_ROOM, 0);

        return this.matchesComparison(currentValue, referenceValue);
    }

    private boolean evaluateContextTarget(WiredContext ctx, Room room) {
        Integer currentValue = this.readContextTargetValue(ctx, room);
        Integer referenceValue = this.referenceFor(this.resolveReferences(ctx, room), this.variableItemId, TARGET_CONTEXT, 0);

        return this.matchesComparison(currentValue, referenceValue);
    }

    private ReferenceSnapshot resolveReferences(WiredContext ctx, Room room) {
        if (this.referenceMode != REFERENCE_VARIABLE) return null;

        return switch (this.referenceTargetType) {
            case TARGET_USER -> this.userReferences(ctx, room);
            case TARGET_FURNI -> this.furniReferences(ctx, room);
            case TARGET_CONTEXT -> this.contextReferences(ctx, room);
            case TARGET_ROOM -> this.roomReferences(room);
            default -> null;
        };
    }

    private ReferenceSnapshot userReferences(WiredContext ctx, Room room) {
        ReferenceSnapshot snapshot = new ReferenceSnapshot(TARGET_USER);

        if (isInternalVariableToken(this.referenceVariableToken)) {
            String key = getInternalVariableKey(this.referenceVariableToken);
            if (!canUseUserInternalReference(key)) return null;

            for (RoomUnit roomUnit : WiredSourceUtil.resolveUsers(ctx, this.referenceUserSource)) {
                Integer value = this.readUserInternalValue(room, roomUnit, key);
                if (value != null && roomUnit != null) snapshot.add(roomUnit.getId(), value);
            }

            return snapshot.isEmpty() ? null : snapshot;
        }

        WiredVariableDefinitionInfo definition = room.getUserVariableManager().getDefinitionInfo(this.referenceVariableItemId);
        if (definition == null || !definition.hasValue()) return null;

        for (RoomUnit roomUnit : WiredSourceUtil.resolveUsers(ctx, this.referenceUserSource)) {
            if (roomUnit == null) continue;

            Habbo habbo = room.getHabbo(roomUnit);
            if (habbo != null) snapshot.add(roomUnit.getId(), room.getUserVariableManager().getCurrentValue(habbo.getHabboInfo().getId(), this.referenceVariableItemId));
        }

        return snapshot.isEmpty() ? null : snapshot;
    }

    private ReferenceSnapshot furniReferences(WiredContext ctx, Room room) {
        int source = (this.referenceFurniSource == SOURCE_SECONDARY_SELECTED) ? WiredSourceUtil.SOURCE_SELECTED : this.referenceFurniSource;
        if (source == WiredSourceUtil.SOURCE_SELECTED) this.refreshReferenceItems();

        ReferenceSnapshot snapshot = new ReferenceSnapshot(TARGET_FURNI);

        if (isInternalVariableToken(this.referenceVariableToken)) {
            String key = getInternalVariableKey(this.referenceVariableToken);
            if (!canUseFurniInternalReference(key)) return null;

            for (HabboItem item : WiredSourceUtil.resolveItems(ctx, source, this.referenceSelectedItems)) {
                Integer value = this.readFurniInternalValue(room, item, key);
                if (value != null && item != null) snapshot.add(item.getId(), value);
            }

            return snapshot.isEmpty() ? null : snapshot;
        }

        WiredVariableDefinitionInfo definition = room.getFurniVariableManager().getDefinitionInfo(this.referenceVariableItemId);
        if (definition == null || !definition.hasValue()) return null;

        for (HabboItem item : WiredSourceUtil.resolveItems(ctx, source, this.referenceSelectedItems)) {
            if (item != null) snapshot.add(item.getId(), room.getFurniVariableManager().getCurrentValue(item.getId(), this.referenceVariableItemId));
        }

        return snapshot.isEmpty() ? null : snapshot;
    }

    private ReferenceSnapshot roomReferences(Room room) {
        ReferenceSnapshot snapshot = new ReferenceSnapshot(TARGET_ROOM);

        if (isInternalVariableToken(this.referenceVariableToken)) {
            String key = getInternalVariableKey(this.referenceVariableToken);
            if (!canUseRoomInternalReference(key)) return null;

            Integer value = this.readRoomInternalValue(room, key);
            if (value == null) return null;

            snapshot.add(room.getId(), value);
            return snapshot;
        }

        WiredVariableDefinitionInfo definition = room.getRoomVariableManager().getDefinitionInfo(this.referenceVariableItemId);
        if (definition == null || !definition.hasValue()) return null;

        snapshot.add(room.getId(), room.getRoomVariableManager().getCurrentValue(this.referenceVariableItemId));
        return snapshot;
    }

    private ReferenceSnapshot contextReferences(WiredContext ctx, Room room) {
        ReferenceSnapshot snapshot = new ReferenceSnapshot(TARGET_CONTEXT);

        if (isInternalVariableToken(this.referenceVariableToken)) {
            String key = getInternalVariableKey(this.referenceVariableToken);
            if (!canUseContextInternalReference(key)) return null;

            Integer value = WiredInternalVariableSupport.readContextValue(ctx, key);
            if (value == null) return null;

            snapshot.add(this.referenceVariableItemId > 0 ? this.referenceVariableItemId : (room != null ? room.getId() : 0), value);
            return snapshot;
        }

        WiredVariableDefinitionInfo definition = WiredContextVariableSupport.getDefinitionInfo(room, this.referenceVariableItemId);
        if (definition == null || !definition.hasValue() || !WiredContextVariableSupport.hasVariable(ctx, this.referenceVariableItemId)) return null;

        Integer value = WiredContextVariableSupport.getCurrentValue(ctx, this.referenceVariableItemId);
        if (value == null) return null;

        snapshot.add(this.referenceVariableItemId, value);
        return snapshot;
    }

    private Integer readUserValue(Room room, RoomUnit roomUnit) {
        if (room == null || roomUnit == null) return null;

        if (isInternalVariableToken(this.variableToken)) {
            String key = getInternalVariableKey(this.variableToken);
            return canUseUserInternalReference(key) ? this.readUserInternalValue(room, roomUnit, key) : null;
        }

        WiredVariableDefinitionInfo definition = room.getUserVariableManager().getDefinitionInfo(this.variableItemId);
        if (definition == null || !definition.hasValue()) return null;

        Habbo habbo = room.getHabbo(roomUnit);
        return (habbo != null) ? room.getUserVariableManager().getCurrentValue(habbo.getHabboInfo().getId(), this.variableItemId) : null;
    }

    private Integer readFurniValue(Room room, HabboItem item) {
        if (room == null || item == null) return null;

        if (isInternalVariableToken(this.variableToken)) {
            String key = getInternalVariableKey(this.variableToken);
            return canUseFurniInternalReference(key) ? this.readFurniInternalValue(room, item, key) : null;
        }

        WiredVariableDefinitionInfo definition = room.getFurniVariableManager().getDefinitionInfo(this.variableItemId);
        return (definition != null && definition.hasValue()) ? room.getFurniVariableManager().getCurrentValue(item.getId(), this.variableItemId) : null;
    }

    private Integer readRoomValue(Room room) {
        if (room == null) return null;

        if (isInternalVariableToken(this.variableToken)) {
            String key = getInternalVariableKey(this.variableToken);
            return canUseRoomInternalReference(key) ? this.readRoomInternalValue(room, key) : null;
        }

        WiredVariableDefinitionInfo definition = room.getRoomVariableManager().getDefinitionInfo(this.variableItemId);
        return (definition != null && definition.hasValue()) ? room.getRoomVariableManager().getCurrentValue(this.variableItemId) : null;
    }

    private Integer readContextTargetValue(WiredContext ctx, Room room) {
        if (ctx == null || room == null) return null;

        if (isInternalVariableToken(this.variableToken)) {
            String key = getInternalVariableKey(this.variableToken);
            return canUseContextInternalReference(key) ? WiredInternalVariableSupport.readContextValue(ctx, key) : null;
        }

        WiredVariableDefinitionInfo definition = WiredContextVariableSupport.getDefinitionInfo(room, this.variableItemId);
        if (definition == null || !definition.hasValue() || !WiredContextVariableSupport.hasVariable(ctx, this.variableItemId)) return null;

        return WiredContextVariableSupport.getCurrentValue(ctx, this.variableItemId);
    }

    private Integer referenceFor(ReferenceSnapshot snapshot, int destinationEntityId, int destinationTarget, int destinationIndex) {
        if (this.referenceMode != REFERENCE_VARIABLE) return this.referenceConstantValue;
        if (snapshot == null || snapshot.isEmpty()) return null;
        if (snapshot.targetType == destinationTarget && snapshot.values.containsKey(destinationEntityId)) return snapshot.values.get(destinationEntityId);
        if (destinationIndex >= 0 && destinationIndex < snapshot.values.size()) return new ArrayList<>(snapshot.values.values()).get(destinationIndex);
        return new ArrayList<>(snapshot.values.values()).get(0);
    }

    private boolean matchesComparison(Integer currentValue, Integer referenceValue) {
        if (currentValue == null || referenceValue == null) return false;

        return switch (this.comparison) {
            case COMPARISON_GREATER_THAN -> currentValue > referenceValue;
            case COMPARISON_GREATER_THAN_OR_EQUAL -> currentValue >= referenceValue;
            case COMPARISON_LESS_THAN_OR_EQUAL -> currentValue <= referenceValue;
            case COMPARISON_LESS_THAN -> currentValue < referenceValue;
            case COMPARISON_NOT_EQUAL -> !currentValue.equals(referenceValue);
            default -> currentValue.equals(referenceValue);
        };
    }

    private boolean isValidSource(Room room, int targetType, String variableToken) {
        if (variableToken == null || variableToken.isEmpty()) return false;

        return switch (targetType) {
            case TARGET_USER -> isInternalVariableToken(variableToken)
                ? canUseUserInternalReference(getInternalVariableKey(variableToken))
                : this.isValidUserCustomValue(room, getCustomItemId(variableToken));
            case TARGET_FURNI -> isInternalVariableToken(variableToken)
                ? canUseFurniInternalReference(getInternalVariableKey(variableToken))
                : this.isValidFurniCustomValue(room, getCustomItemId(variableToken));
            case TARGET_CONTEXT -> isInternalVariableToken(variableToken)
                ? canUseContextInternalReference(getInternalVariableKey(variableToken))
                : this.isValidContextCustomValue(room, getCustomItemId(variableToken));
            case TARGET_ROOM -> isInternalVariableToken(variableToken)
                ? canUseRoomInternalReference(getInternalVariableKey(variableToken))
                : this.isValidRoomCustomValue(room, getCustomItemId(variableToken));
            default -> false;
        };
    }

    private boolean isValidReference(Room room, int targetType, String variableToken) {
        return this.isValidSource(room, targetType, variableToken);
    }

    private boolean isValidUserCustomValue(Room room, int variableItemId) {
        WiredVariableDefinitionInfo definition = (room != null) ? room.getUserVariableManager().getDefinitionInfo(variableItemId) : null;
        return definition != null && definition.hasValue();
    }

    private boolean isValidFurniCustomValue(Room room, int variableItemId) {
        WiredVariableDefinitionInfo definition = (room != null) ? room.getFurniVariableManager().getDefinitionInfo(variableItemId) : null;
        return definition != null && definition.hasValue();
    }

    private boolean isValidRoomCustomValue(Room room, int variableItemId) {
        WiredVariableDefinitionInfo definition = (room != null) ? room.getRoomVariableManager().getDefinitionInfo(variableItemId) : null;
        return definition != null && definition.hasValue();
    }

    private boolean isValidContextCustomValue(Room room, int variableItemId) {
        WiredVariableDefinitionInfo definition = WiredContextVariableSupport.getDefinitionInfo(room, variableItemId);
        return definition != null && definition.hasValue();
    }

    private Integer readUserInternalValue(Room room, RoomUnit roomUnit, String key) {
        return WiredInternalVariableSupport.readUserValue(room, roomUnit, key);
    }

    private Integer readFurniInternalValue(Room room, HabboItem item, String key) {
        return WiredInternalVariableSupport.readFurniValue(room, item, key);
    }

    private Integer readRoomInternalValue(Room room, String key) {
        return WiredInternalVariableSupport.readRoomValue(room, key);
    }

    private Integer getUserTeamScore(Room room, Habbo habbo) {
        if (room == null || habbo == null || habbo.getHabboInfo().getGamePlayer() == null) return null;

        Game game = this.resolveTeamGame(room, habbo);
        GamePlayer gamePlayer = habbo.getHabboInfo().getGamePlayer();

        if (game == null || gamePlayer.getTeamColor() == null) return gamePlayer.getScore();

        GameTeam team = game.getTeam(gamePlayer.getTeamColor());
        return (team != null) ? team.getTotalScore() : gamePlayer.getScore();
    }

    private Integer getTeamColorId(int effectId) {
        TeamEffectData data = this.getTeamEffectData(effectId);
        return data == null ? null : data.colorId;
    }

    private Integer getTeamTypeId(int effectId) {
        TeamEffectData data = this.getTeamEffectData(effectId);
        return data == null ? null : data.typeId;
    }

    private int getTeamMetric(Room room, GameTeamColors color, boolean score) {
        Game game = this.resolveTeamGame(room, null);
        if (game == null || color == null) return 0;

        GameTeam team = game.getTeam(color);
        if (team == null) return 0;

        return score ? team.getTotalScore() : team.getMembers().size();
    }

    private Game resolveTeamGame(Room room, Habbo habbo) {
        if (room == null) return null;

        if (habbo != null && habbo.getHabboInfo() != null && habbo.getHabboInfo().getCurrentGame() != null) {
            Game game = room.getGame(habbo.getHabboInfo().getCurrentGame());
            if (game != null) return game;
        }

        Game wiredGame = room.getGame(WiredGame.class);
        if (wiredGame != null) return wiredGame;

        Game freezeGame = room.getGame(FreezeGame.class);
        if (freezeGame != null) return freezeGame;

        return room.getGame(BattleBanzaiGame.class);
    }

    private List<HabboItem> parseItems(int[] ids, Room room) {
        List<HabboItem> items = new ArrayList<>();
        if (ids == null || room == null) return items;

        for (int id : ids) {
            HabboItem item = room.getHabboItem(id);
            if (item != null) items.add(item);
        }

        return items;
    }

    private List<HabboItem> parseItems(List<Integer> ids, Room room) {
        List<HabboItem> items = new ArrayList<>();
        if (ids == null || room == null) return items;

        for (Integer id : ids) {
            if (id == null || id <= 0) continue;

            HabboItem item = room.getHabboItem(id);
            if (item != null) items.add(item);
        }

        return items;
    }

    private List<HabboItem> parseItems(String ids, Room room) {
        List<HabboItem> items = new ArrayList<>();
        if (ids == null || ids.trim().isEmpty() || room == null) return items;

        for (String part : ids.split("[;,\\t]")) {
            int id = parseInteger(part);
            if (id <= 0) continue;

            HabboItem item = room.getHabboItem(id);
            if (item != null) items.add(item);
        }

        return items;
    }

    private void refreshReferenceItems() {
        THashSet<HabboItem> staleItems = new THashSet<>();
        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());

        if (room == null) {
            staleItems.addAll(this.referenceSelectedItems);
        } else {
            for (HabboItem item : this.referenceSelectedItems) {
                if (item == null || item.getRoomId() != room.getId() || room.getHabboItem(item.getId()) == null) {
                    staleItems.add(item);
                }
            }
        }

        this.referenceSelectedItems.removeAll(staleItems);
    }

    private String serializeStringData() {
        return (this.variableToken == null ? "" : this.variableToken) + DELIM + (this.referenceVariableToken == null ? "" : this.referenceVariableToken) + DELIM + this.serializeIds(this.referenceSelectedItems);
    }

    private String[] parseStringData(String value) {
        return (value == null || value.isEmpty()) ? new String[0] : value.split("\\t", -1);
    }

    private List<Integer> toIds(THashSet<HabboItem> items) {
        List<Integer> ids = new ArrayList<>();
        for (HabboItem item : items) {
            if (item != null) ids.add(item.getId());
        }
        return ids;
    }

    private String serializeIds(THashSet<HabboItem> items) {
        StringBuilder builder = new StringBuilder();

        for (HabboItem item : items) {
            if (item == null) continue;
            if (builder.length() > 0) builder.append(FURNI_DELIM);
            builder.append(item.getId());
        }

        return builder.toString();
    }

    private void setReferenceVariableToken(String token) {
        this.referenceVariableToken = normalizeVariableToken(token);
        this.referenceVariableItemId = getCustomItemId(this.referenceVariableToken);
    }

    private static boolean canUseUserInternalReference(String key) {
        return WiredInternalVariableSupport.canUseUserReference(key);
    }

    private static boolean canUseFurniInternalReference(String key) {
        return WiredInternalVariableSupport.canUseFurniReference(key);
    }

    private static boolean canUseRoomInternalReference(String key) {
        return WiredInternalVariableSupport.canUseRoomReference(key);
    }

    private static boolean canUseContextInternalReference(String key) {
        return WiredInternalVariableSupport.canUseContextReference(key);
    }

    private static int param(int[] params, int index, int fallback) {
        return (params.length > index) ? params[index] : fallback;
    }

    static int normalizeTargetTypeExtended(int value) {
        return switch (value) {
            case TARGET_FURNI, TARGET_CONTEXT, TARGET_ROOM -> value;
            default -> TARGET_USER;
        };
    }

    static int normalizeReferenceMode(int value) {
        return (value == REFERENCE_VARIABLE) ? REFERENCE_VARIABLE : REFERENCE_CONSTANT;
    }

    static int normalizeReferenceFurniSource(int value) {
        return switch (value) {
            case SOURCE_SECONDARY_SELECTED, WiredSourceUtil.SOURCE_SELECTOR, WiredSourceUtil.SOURCE_SIGNAL -> value;
            default -> WiredSourceUtil.SOURCE_TRIGGER;
        };
    }

    static int normalizeComparison(int value) {
        return switch (value) {
            case COMPARISON_GREATER_THAN, COMPARISON_GREATER_THAN_OR_EQUAL, COMPARISON_LESS_THAN_OR_EQUAL, COMPARISON_LESS_THAN, COMPARISON_NOT_EQUAL -> value;
            default -> COMPARISON_EQUAL;
        };
    }

    static int normalizeReferenceConstantValue(int value) {
        return Math.max(-MAX_ABS_REFERENCE_CONSTANT, Math.min(MAX_ABS_REFERENCE_CONSTANT, value));
    }

    static int parseInteger(String value) {
        try {
            return (value == null || value.trim().isEmpty()) ? 0 : Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    static class JsonData {
        int targetType, variableItemId, comparison, referenceMode, referenceConstantValue, referenceTargetType, referenceVariableItemId, userSource, furniSource, referenceUserSource, referenceFurniSource, quantifier;
        String variableToken, referenceVariableToken;
        List<Integer> selectedItemIds, referenceSelectedItemIds;

        JsonData(int targetType, String variableToken, int variableItemId, int comparison, int referenceMode, int referenceConstantValue, int referenceTargetType, String referenceVariableToken, int referenceVariableItemId, int userSource, int furniSource, int referenceUserSource, int referenceFurniSource, int quantifier, List<Integer> selectedItemIds, List<Integer> referenceSelectedItemIds) {
            this.targetType = targetType;
            this.variableToken = variableToken;
            this.variableItemId = variableItemId;
            this.comparison = comparison;
            this.referenceMode = referenceMode;
            this.referenceConstantValue = referenceConstantValue;
            this.referenceTargetType = referenceTargetType;
            this.referenceVariableToken = referenceVariableToken;
            this.referenceVariableItemId = referenceVariableItemId;
            this.userSource = userSource;
            this.furniSource = furniSource;
            this.referenceUserSource = referenceUserSource;
            this.referenceFurniSource = referenceFurniSource;
            this.quantifier = quantifier;
            this.selectedItemIds = selectedItemIds;
            this.referenceSelectedItemIds = referenceSelectedItemIds;
        }
    }

    private static class ReferenceSnapshot {
        final int targetType;
        final LinkedHashMap<Integer, Integer> values = new LinkedHashMap<>();

        ReferenceSnapshot(int targetType) {
            this.targetType = targetType;
        }

        void add(int entityId, int value) {
            this.values.put(entityId, value);
        }

        boolean isEmpty() {
            return this.values.isEmpty();
        }
    }
}
