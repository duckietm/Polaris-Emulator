package com.eu.habbo.habbohotel.items.interactions.wired.selector;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.bots.Bot;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.games.Game;
import com.eu.habbo.habbohotel.games.GamePlayer;
import com.eu.habbo.habbohotel.games.GameTeam;
import com.eu.habbo.habbohotel.games.GameTeamColors;
import com.eu.habbo.habbohotel.games.battlebanzai.BattleBanzaiGame;
import com.eu.habbo.habbohotel.games.freeze.FreezeGame;
import com.eu.habbo.habbohotel.games.wired.WiredGame;
import com.eu.habbo.habbohotel.items.FurnitureType;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWired;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.pets.Pet;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomRightLevels;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.rooms.RoomUnitStatus;
import com.eu.habbo.habbohotel.rooms.WiredVariableDefinitionInfo;
import com.eu.habbo.habbohotel.users.DanceType;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredContextVariableSupport;
import com.eu.habbo.habbohotel.wired.core.WiredFreezeUtil;
import com.eu.habbo.habbohotel.wired.core.WiredInternalVariableSupport;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;
import com.eu.habbo.util.HotelDateTimeUtil;
import gnu.trove.set.hash.THashSet;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.ZonedDateTime;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

public abstract class WiredEffectVariableSelectorBase extends InteractionWiredEffect {
    protected static final int TARGET_USER = 0;
    protected static final int TARGET_FURNI = 1;
    protected static final int TARGET_CONTEXT = 2;
    protected static final int TARGET_ROOM = 3;

    protected static final int REFERENCE_CONSTANT = 0;
    protected static final int REFERENCE_VARIABLE = 1;

    protected static final int SOURCE_SECONDARY_SELECTED = 101;

    protected static final int COMPARISON_GREATER_THAN = 0;
    protected static final int COMPARISON_GREATER_THAN_OR_EQUAL = 1;
    protected static final int COMPARISON_EQUAL = 2;
    protected static final int COMPARISON_LESS_THAN_OR_EQUAL = 3;
    protected static final int COMPARISON_LESS_THAN = 4;
    protected static final int COMPARISON_NOT_EQUAL = 5;

    private static final String CUSTOM_TOKEN_PREFIX = "custom:";
    private static final String INTERNAL_TOKEN_PREFIX = "internal:";
    private static final String DELIM = "\t";

    protected boolean selectByValue = false;
    protected int comparison = COMPARISON_EQUAL;
    protected int referenceMode = REFERENCE_CONSTANT;
    protected int referenceConstantValue = 0;
    protected int referenceTargetType = TARGET_USER;
    protected int referenceUserSource = WiredSourceUtil.SOURCE_TRIGGER;
    protected int referenceFurniSource = WiredSourceUtil.SOURCE_TRIGGER;
    protected boolean filterExisting = false;
    protected boolean invert = false;
    protected String variableToken = "";
    protected int variableItemId = 0;
    protected String referenceVariableToken = "";
    protected int referenceVariableItemId = 0;
    protected final THashSet<HabboItem> referenceSelectedItems = new THashSet<>();

    protected WiredEffectVariableSelectorBase(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    protected WiredEffectVariableSelectorBase(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    protected abstract int getVariableTargetType();

    @Override
    public void execute(WiredContext ctx) {
        Room room = ctx.room();

        if (room == null || this.variableToken == null || this.variableToken.isEmpty()) {
            return;
        }

        if (this.getVariableTargetType() == TARGET_FURNI) {
            LinkedHashSet<HabboItem> matchedItems = new LinkedHashSet<>();

            for (HabboItem item : this.getSelectableFloorItems(room, ctx)) {
                if (item == null) continue;
                if (!this.matchesFurni(room, item, ctx)) continue;

                matchedItems.add(item);
            }

            LinkedHashSet<HabboItem> result = this.applySelectorModifiers(matchedItems, this.getSelectableFloorItems(room, ctx), ctx.targets().items(), this.filterExisting, this.invert);
            ctx.targets().setItems(result);
            return;
        }

        LinkedHashSet<RoomUnit> matchedUsers = new LinkedHashSet<>();

        for (RoomUnit roomUnit : room.getRoomUnits()) {
            if (roomUnit == null) continue;
            if (!this.matchesUser(room, roomUnit, ctx)) continue;

            matchedUsers.add(roomUnit);
        }

        LinkedHashSet<RoomUnit> result = this.applySelectorModifiers(matchedUsers, room.getRoomUnits(), ctx.targets().users(), this.filterExisting, this.invert);
        ctx.targets().setUsers(result);
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        Room room = this.getRoom();
        if (room == null) return false;

        int[] params = settings.getIntParams();
        String[] stringParts = parseStringData(settings.getStringParam());

        boolean nextSelectByValue = param(params, 0, 0) == 1;
        int nextComparison = normalizeComparison(param(params, 1, COMPARISON_EQUAL));
        int nextReferenceMode = normalizeReferenceMode(param(params, 2, REFERENCE_CONSTANT));
        int nextReferenceConstantValue = param(params, 3, 0);
        int nextReferenceTargetType = normalizeReferenceTargetType(param(params, 4, TARGET_USER));
        int nextReferenceUserSource = normalizeUserSource(param(params, 5, WiredSourceUtil.SOURCE_TRIGGER));
        int nextReferenceFurniSource = normalizeReferenceFurniSource(param(params, 6, WiredSourceUtil.SOURCE_TRIGGER));
        boolean nextFilterExisting = param(params, 7, 0) == 1;
        boolean nextInvert = param(params, 8, 0) == 1;
        String nextVariableToken = normalizeVariableToken((stringParts.length > 0) ? stringParts[0] : settings.getStringParam());
        String nextReferenceVariableToken = normalizeVariableToken((stringParts.length > 1) ? stringParts[1] : "");

        if (!this.isValidMainVariable(room, nextVariableToken, nextSelectByValue)) return false;
        if (nextSelectByValue && nextReferenceMode == REFERENCE_VARIABLE && !this.isValidReference(room, nextReferenceTargetType, nextReferenceVariableToken)) return false;

        List<HabboItem> nextReferenceItems = new ArrayList<>();

        if (nextSelectByValue && nextReferenceMode == REFERENCE_VARIABLE && nextReferenceTargetType == TARGET_FURNI && nextReferenceFurniSource == SOURCE_SECONDARY_SELECTED) {
            int[] furniIds = settings.getFurniIds();
            if (furniIds.length > Emulator.getConfig().getInt("hotel.wired.furni.selection.count")) return false;

            for (int furniId : furniIds) {
                HabboItem item = room.getHabboItem(furniId);
                if (item != null) nextReferenceItems.add(item);
            }
        }

        this.referenceSelectedItems.clear();
        this.referenceSelectedItems.addAll(nextReferenceItems);
        this.selectByValue = nextSelectByValue;
        this.comparison = nextComparison;
        this.referenceMode = nextReferenceMode;
        this.referenceConstantValue = nextReferenceConstantValue;
        this.referenceTargetType = nextReferenceTargetType;
        this.referenceUserSource = nextReferenceUserSource;
        this.referenceFurniSource = nextReferenceFurniSource;
        this.filterExisting = nextFilterExisting;
        this.invert = nextInvert;
        this.setVariableToken(nextVariableToken);
        this.setReferenceVariableToken(nextReferenceVariableToken);
        this.setDelay(settings.getDelay());

        return true;
    }

    @Override
    public boolean isSelector() {
        return true;
    }

    @Override
    public boolean usesExistingSelectorTargets() {
        return this.filterExisting;
    }

    @Override
    public String getWiredData() {
        this.refreshReferenceItems();

        return WiredManager.getGson().toJson(new JsonData(
            this.selectByValue,
            this.comparison,
            this.referenceMode,
            this.referenceConstantValue,
            this.referenceTargetType,
            this.referenceUserSource,
            this.referenceFurniSource,
            this.filterExisting,
            this.invert,
            this.variableToken,
            this.variableItemId,
            this.referenceVariableToken,
            this.referenceVariableItemId,
            this.toIds(this.referenceSelectedItems),
            this.getDelay()
        ));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.onPickUp();

        String wiredData = set.getString("wired_data");
        if (wiredData == null || wiredData.isEmpty() || !wiredData.startsWith("{")) return;

        JsonData data = WiredSelectorPayloadGuard.fromJson(wiredData, JsonData.class);
        if (data == null) return;

        this.selectByValue = data.selectByValue;
        this.comparison = normalizeComparison(data.comparison);
        this.referenceMode = normalizeReferenceMode(data.referenceMode);
        this.referenceConstantValue = data.referenceConstantValue;
        this.referenceTargetType = normalizeReferenceTargetType(data.referenceTargetType);
        this.referenceUserSource = normalizeUserSource(data.referenceUserSource);
        this.referenceFurniSource = normalizeReferenceFurniSource(data.referenceFurniSource);
        this.filterExisting = data.filterExisting;
        this.invert = data.invert;
        this.setVariableToken(normalizeVariableToken((data.variableToken != null) ? data.variableToken : ((data.variableItemId > 0) ? String.valueOf(data.variableItemId) : "")));
        this.setReferenceVariableToken(normalizeVariableToken((data.referenceVariableToken != null) ? data.referenceVariableToken : ((data.referenceVariableItemId > 0) ? String.valueOf(data.referenceVariableItemId) : "")));
        this.setDelay(data.delay);

        if (room == null || data.selectedItemIds == null) return;

        for (Integer itemId : data.selectedItemIds) {
            if (itemId == null || itemId <= 0) continue;

            HabboItem item = room.getHabboItem(itemId);
            if (item != null) this.referenceSelectedItems.add(item);
        }
    }

    @Override
    public void onPickUp() {
        this.selectByValue = false;
        this.comparison = COMPARISON_EQUAL;
        this.referenceMode = REFERENCE_CONSTANT;
        this.referenceConstantValue = 0;
        this.referenceTargetType = TARGET_USER;
        this.referenceUserSource = WiredSourceUtil.SOURCE_TRIGGER;
        this.referenceFurniSource = WiredSourceUtil.SOURCE_TRIGGER;
        this.filterExisting = false;
        this.invert = false;
        this.referenceSelectedItems.clear();
        this.setVariableToken("");
        this.setReferenceVariableToken("");
        this.setDelay(0);
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        this.refreshReferenceItems();

        List<HabboItem> serializedItems = new ArrayList<>();
        if (this.selectByValue && this.referenceMode == REFERENCE_VARIABLE && this.referenceTargetType == TARGET_FURNI && this.referenceFurniSource == SOURCE_SECONDARY_SELECTED) {
            serializedItems.addAll(this.referenceSelectedItems);
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
        message.appendInt(9);
        message.appendInt(this.selectByValue ? 1 : 0);
        message.appendInt(this.comparison);
        message.appendInt(this.referenceMode);
        message.appendInt(this.referenceConstantValue);
        message.appendInt(this.referenceTargetType);
        message.appendInt(this.referenceUserSource);
        message.appendInt(this.referenceFurniSource);
        message.appendInt(this.filterExisting ? 1 : 0);
        message.appendInt(this.invert ? 1 : 0);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(this.getDelay());
        message.appendInt(0);
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public boolean requiresTriggeringUser() {
        return this.selectByValue && this.referenceMode == REFERENCE_VARIABLE && this.referenceTargetType == TARGET_USER && this.referenceUserSource == WiredSourceUtil.SOURCE_TRIGGER;
    }

    private boolean matchesUser(Room room, RoomUnit roomUnit, WiredContext ctx) {
        if (!this.selectByValue) return this.hasUserVariable(room, roomUnit);

        Integer currentValue = this.readUserValue(room, roomUnit);
        Integer referenceValue = this.resolveReferenceValue(ctx, room, roomUnit != null ? roomUnit.getId() : 0, TARGET_USER, -1);

        return this.matchesComparison(currentValue, referenceValue);
    }

    private boolean matchesFurni(Room room, HabboItem item, WiredContext ctx) {
        if (!this.selectByValue) return this.hasFurniVariable(room, item);

        Integer currentValue = this.readFurniValue(room, item);
        Integer referenceValue = this.resolveReferenceValue(ctx, room, item != null ? item.getId() : 0, TARGET_FURNI, -1);

        return this.matchesComparison(currentValue, referenceValue);
    }

    private boolean hasUserVariable(Room room, RoomUnit roomUnit) {
        if (room == null || roomUnit == null) return false;

        if (isCustomVariableToken(this.variableToken)) {
            Habbo habbo = room.getHabbo(roomUnit);
            return habbo != null && room.getUserVariableManager().hasVariable(habbo.getHabboInfo().getId(), this.variableItemId);
        }

        return isInternalVariableToken(this.variableToken) && this.hasUserInternalVariable(room, roomUnit, getInternalVariableKey(this.variableToken));
    }

    private boolean hasFurniVariable(Room room, HabboItem item) {
        if (room == null || item == null) return false;

        if (isCustomVariableToken(this.variableToken)) {
            return room.getFurniVariableManager().hasVariable(item.getId(), this.variableItemId);
        }

        return isInternalVariableToken(this.variableToken) && this.hasFurniInternalVariable(item, getInternalVariableKey(this.variableToken));
    }

    private Integer resolveReferenceValue(WiredContext ctx, Room room, int destinationEntityId, int destinationTargetType, int destinationIndex) {
        if (!this.selectByValue || this.referenceMode != REFERENCE_VARIABLE) return this.referenceConstantValue;

        ReferenceSnapshot snapshot = this.resolveReferences(ctx, room);
        if (snapshot == null || snapshot.isEmpty()) return null;
        if (snapshot.targetType == destinationTargetType && snapshot.values.containsKey(destinationEntityId)) return snapshot.values.get(destinationEntityId);
        if (destinationIndex >= 0 && destinationIndex < snapshot.values.size()) return new ArrayList<>(snapshot.values.values()).get(destinationIndex);

        return new ArrayList<>(snapshot.values.values()).get(0);
    }

    private ReferenceSnapshot resolveReferences(WiredContext ctx, Room room) {
        return switch (this.referenceTargetType) {
            case TARGET_FURNI -> this.furniReferences(ctx, room);
            case TARGET_CONTEXT -> this.contextReferences(ctx, room);
            case TARGET_ROOM -> this.roomReferences(room);
            default -> this.userReferences(ctx, room);
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

    private boolean isValidMainVariable(Room room, String token, boolean requireValue) {
        if (token == null || token.isEmpty()) return false;

        int targetType = this.getVariableTargetType();

        if (isInternalVariableToken(token)) {
            String key = getInternalVariableKey(token);
            return targetType == TARGET_FURNI
                ? (requireValue ? canUseFurniInternalReference(key) : this.hasFurniInternalKey(key))
                : (requireValue ? canUseUserInternalReference(key) : this.hasUserInternalKey(key));
        }

        if (targetType == TARGET_FURNI) {
            WiredVariableDefinitionInfo definition = (room != null) ? room.getFurniVariableManager().getDefinitionInfo(getCustomItemId(token)) : null;
            return definition != null && (!requireValue || definition.hasValue());
        }

        WiredVariableDefinitionInfo definition = (room != null) ? room.getUserVariableManager().getDefinitionInfo(getCustomItemId(token)) : null;
        return definition != null && (!requireValue || definition.hasValue());
    }

    private boolean isValidReference(Room room, int targetType, String token) {
        if (token == null || token.isEmpty()) return false;

        if (isInternalVariableToken(token)) {
            String key = getInternalVariableKey(token);
            return switch (targetType) {
                case TARGET_FURNI -> canUseFurniInternalReference(key);
                case TARGET_CONTEXT -> canUseContextInternalReference(key);
                case TARGET_ROOM -> canUseRoomInternalReference(key);
                default -> canUseUserInternalReference(key);
            };
        }

        return switch (targetType) {
            case TARGET_FURNI -> {
                WiredVariableDefinitionInfo definition = (room != null) ? room.getFurniVariableManager().getDefinitionInfo(getCustomItemId(token)) : null;
                yield definition != null && definition.hasValue();
            }
            case TARGET_CONTEXT -> this.isValidContextCustomReference(room, getCustomItemId(token));
            case TARGET_ROOM -> {
                WiredVariableDefinitionInfo definition = (room != null) ? room.getRoomVariableManager().getDefinitionInfo(getCustomItemId(token)) : null;
                yield definition != null && definition.hasValue();
            }
            default -> {
                WiredVariableDefinitionInfo definition = (room != null) ? room.getUserVariableManager().getDefinitionInfo(getCustomItemId(token)) : null;
                yield definition != null && definition.hasValue();
            }
        };
    }

    private boolean isValidContextCustomReference(Room room, int variableItemId) {
        WiredVariableDefinitionInfo definition = WiredContextVariableSupport.getDefinitionInfo(room, variableItemId);
        return definition != null && definition.hasValue();
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

    private boolean hasUserInternalVariable(Room room, RoomUnit roomUnit, String key) {
        return WiredInternalVariableSupport.hasUserValue(room, roomUnit, key);
    }

    private boolean hasFurniInternalVariable(HabboItem item, String key) {
        return WiredInternalVariableSupport.hasFurniValue(item, key);
    }

    private boolean hasUserInternalKey(String key) {
        return WiredInternalVariableSupport.canUseUserReference(key);
    }

    private boolean hasFurniInternalKey(String key) {
        return WiredInternalVariableSupport.canUseFurniReference(key) || "@wallitem_offset".equals(WiredInternalVariableSupport.normalizeKey(key));
    }

    private boolean hasRoomEntryMethod(Habbo habbo) {
        if (habbo == null) return false;

        String roomEntryMethod = habbo.getHabboInfo().getRoomEntryMethod();
        return roomEntryMethod != null && !roomEntryMethod.trim().isEmpty() && !"unknown".equalsIgnoreCase(roomEntryMethod);
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

    private TeamEffectData getTeamEffectData(int effectValue) {
        if (effectValue <= 0) return null;

        if (effectValue >= 223 && effectValue <= 226) return new TeamEffectData(effectValue - 222, 0);
        if (effectValue >= 33 && effectValue <= 36) return new TeamEffectData(effectValue - 32, 1);
        if (effectValue >= 40 && effectValue <= 43) return new TeamEffectData(effectValue - 39, 2);

        return null;
    }

    private void refreshReferenceItems() {
        THashSet<HabboItem> staleItems = new THashSet<>();
        Room room = this.getRoom();

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
        return (this.variableToken == null ? "" : this.variableToken) + DELIM + (this.referenceVariableToken == null ? "" : this.referenceVariableToken);
    }

    private void setVariableToken(String token) {
        this.variableToken = normalizeVariableToken(token);
        this.variableItemId = getCustomItemId(this.variableToken);
    }

    private void setReferenceVariableToken(String token) {
        this.referenceVariableToken = normalizeVariableToken(token);
        this.referenceVariableItemId = getCustomItemId(this.referenceVariableToken);
    }

    private List<Integer> toIds(THashSet<HabboItem> items) {
        List<Integer> ids = new ArrayList<>();

        for (HabboItem item : items) {
            if (item != null) ids.add(item.getId());
        }

        return ids;
    }

    private static int normalizeReferenceMode(int value) {
        return (value == REFERENCE_VARIABLE) ? REFERENCE_VARIABLE : REFERENCE_CONSTANT;
    }

    private static int normalizeReferenceTargetType(int value) {
        return switch (value) {
            case TARGET_FURNI, TARGET_CONTEXT, TARGET_ROOM -> value;
            default -> TARGET_USER;
        };
    }

    private static int normalizeReferenceFurniSource(int value) {
        return switch (value) {
            case SOURCE_SECONDARY_SELECTED, WiredSourceUtil.SOURCE_SELECTOR, WiredSourceUtil.SOURCE_SIGNAL -> value;
            default -> WiredSourceUtil.SOURCE_TRIGGER;
        };
    }

    private static int normalizeComparison(int value) {
        return switch (value) {
            case COMPARISON_GREATER_THAN, COMPARISON_GREATER_THAN_OR_EQUAL, COMPARISON_LESS_THAN_OR_EQUAL, COMPARISON_LESS_THAN, COMPARISON_NOT_EQUAL -> value;
            default -> COMPARISON_EQUAL;
        };
    }

    private static int normalizeUserSource(int value) {
        return WiredSourceUtil.isDefaultUserSource(value) ? value : WiredSourceUtil.SOURCE_TRIGGER;
    }

    protected static boolean isCustomVariableToken(String token) {
        return token != null && token.startsWith(CUSTOM_TOKEN_PREFIX);
    }

    protected static boolean isInternalVariableToken(String token) {
        return token != null && token.startsWith(INTERNAL_TOKEN_PREFIX);
    }

    private static int getCustomItemId(String token) {
        if (!isCustomVariableToken(token)) return 0;

        try {
            return Integer.parseInt(token.substring(CUSTOM_TOKEN_PREFIX.length()));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String getInternalVariableKey(String token) {
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
        return (params != null && params.length > index) ? params[index] : fallback;
    }

    private static String[] parseStringData(String value) {
        return (value == null || value.isEmpty()) ? new String[0] : value.split("\\t", -1);
    }

    private static int parseInteger(String value) {
        try {
            return (value == null || value.trim().isEmpty()) ? 0 : Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    protected static class JsonData {
        boolean selectByValue;
        int comparison;
        int referenceMode;
        int referenceConstantValue;
        int referenceTargetType;
        int referenceUserSource;
        int referenceFurniSource;
        boolean filterExisting;
        boolean invert;
        String variableToken;
        int variableItemId;
        String referenceVariableToken;
        int referenceVariableItemId;
        List<Integer> selectedItemIds;
        int delay;

        JsonData(boolean selectByValue, int comparison, int referenceMode, int referenceConstantValue, int referenceTargetType, int referenceUserSource, int referenceFurniSource, boolean filterExisting, boolean invert, String variableToken, int variableItemId, String referenceVariableToken, int referenceVariableItemId, List<Integer> selectedItemIds, int delay) {
            this.selectByValue = selectByValue;
            this.comparison = comparison;
            this.referenceMode = referenceMode;
            this.referenceConstantValue = referenceConstantValue;
            this.referenceTargetType = referenceTargetType;
            this.referenceUserSource = referenceUserSource;
            this.referenceFurniSource = referenceFurniSource;
            this.filterExisting = filterExisting;
            this.invert = invert;
            this.variableToken = variableToken;
            this.variableItemId = variableItemId;
            this.referenceVariableToken = referenceVariableToken;
            this.referenceVariableItemId = referenceVariableItemId;
            this.selectedItemIds = selectedItemIds;
            this.delay = delay;
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

    private static class TeamEffectData {
        final int colorId;
        final int typeId;

        TeamEffectData(int colorId, int typeId) {
            this.colorId = colorId;
            this.typeId = typeId;
        }
    }
}
