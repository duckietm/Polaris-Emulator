package com.eu.habbo.habbohotel.items.interactions.wired.extra;

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
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.WiredVariableDefinitionInfo;
import com.eu.habbo.habbohotel.pets.Pet;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.DanceType;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredContextVariableSupport;
import com.eu.habbo.habbohotel.wired.core.WiredFreezeUtil;
import com.eu.habbo.habbohotel.wired.core.WiredInternalVariableSupport;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;
import com.eu.habbo.util.HotelDateTimeUtil;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.ZonedDateTime;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public abstract class WiredExtraVariableFilterBase extends InteractionWiredExtra {
    protected static final int TARGET_USER = 0;
    protected static final int TARGET_FURNI = 1;
    protected static final int TARGET_CONTEXT = 2;
    protected static final int TARGET_ROOM = 3;

    protected static final int AMOUNT_CONSTANT = 0;
    protected static final int AMOUNT_VARIABLE = 1;
    protected static final int SOURCE_SECONDARY_SELECTED = 101;

    protected static final int SORT_VALUE_HIGHEST = 0;
    protected static final int SORT_VALUE_LOWEST = 1;
    protected static final int SORT_CREATION_OLDEST = 2;
    protected static final int SORT_CREATION_LATEST = 3;
    protected static final int SORT_UPDATE_OLDEST = 4;
    protected static final int SORT_UPDATE_LATEST = 5;

    private static final int MAX_FILTER_AMOUNT = 10000;
    private static final String CUSTOM_TOKEN_PREFIX = "custom:";
    private static final String INTERNAL_TOKEN_PREFIX = "internal:";
    private static final String DELIM = "\t";

    protected int sortBy = SORT_VALUE_HIGHEST;
    protected int amountMode = AMOUNT_CONSTANT;
    protected int amountConstantValue = 1;
    protected int referenceTargetType = TARGET_USER;
    protected int referenceUserSource = WiredSourceUtil.SOURCE_TRIGGER;
    protected int referenceFurniSource = WiredSourceUtil.SOURCE_TRIGGER;
    protected String variableToken = "";
    protected int variableItemId = 0;
    protected String referenceVariableToken = "";
    protected int referenceVariableItemId = 0;
    protected final List<HabboItem> referenceSelectedItems = new ArrayList<>();

    protected WiredExtraVariableFilterBase(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    protected WiredExtraVariableFilterBase(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    protected abstract int getVariableTargetType();

    protected abstract int getCode();

    public List<RoomUnit> filterUsers(Room room, WiredContext ctx, Iterable<RoomUnit> values) {
        if (room == null || ctx == null || this.getVariableTargetType() != TARGET_USER || this.variableToken.isEmpty()) {
            return toUserList(values);
        }

        int amount = this.resolveAmount(ctx, room);
        if (amount <= 0) return new ArrayList<>();

        List<SortableEntry<RoomUnit>> matches = new ArrayList<>();

        for (RoomUnit roomUnit : values) {
            if (roomUnit == null) continue;

            MetricSnapshot metric = this.resolveUserMetric(room, roomUnit);
            if (metric == null) continue;

            matches.add(new SortableEntry<>(roomUnit, metric));
        }

        matches.sort(this.metricComparator());
        return trimUsers(matches, amount);
    }

    public List<HabboItem> filterItems(Room room, WiredContext ctx, Iterable<HabboItem> values) {
        if (room == null || ctx == null || this.getVariableTargetType() != TARGET_FURNI || this.variableToken.isEmpty()) {
            return toItemList(values);
        }

        int amount = this.resolveAmount(ctx, room);
        if (amount <= 0) return new ArrayList<>();

        List<SortableEntry<HabboItem>> matches = new ArrayList<>();

        for (HabboItem item : values) {
            if (item == null) continue;

            MetricSnapshot metric = this.resolveFurniMetric(room, item);
            if (metric == null) continue;

            matches.add(new SortableEntry<>(item, metric));
        }

        matches.sort(this.metricComparator());
        return trimItems(matches, amount);
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());
        if (room == null) throw new WiredSaveException("Room not found");

        int[] params = settings.getIntParams();
        String[] stringParts = parseStringData(settings.getStringParam());
        String nextVariableToken = normalizeVariableToken((stringParts.length > 0) ? stringParts[0] : "");
        String nextReferenceVariableToken = normalizeVariableToken((stringParts.length > 1) ? stringParts[1] : "");
        int nextSortBy = normalizeSortBy(param(params, 0, SORT_VALUE_HIGHEST));
        int nextAmountMode = normalizeAmountMode(param(params, 1, AMOUNT_CONSTANT));
        int nextAmountConstantValue = normalizeAmount(param(params, 2, 1));
        int nextReferenceTargetType = normalizeReferenceTargetType(param(params, 3, TARGET_USER));
        int nextReferenceUserSource = normalizeUserSource(param(params, 4, WiredSourceUtil.SOURCE_TRIGGER));
        int nextReferenceFurniSource = normalizeReferenceFurniSource(param(params, 5, WiredSourceUtil.SOURCE_TRIGGER));

        if (!this.isValidMainVariable(room, nextVariableToken)) throw new WiredSaveException("wiredfurni.params.variables.validation.invalid_variable");
        if (nextAmountMode == AMOUNT_VARIABLE && !this.isValidReference(room, nextReferenceTargetType, nextReferenceVariableToken)) throw new WiredSaveException("wiredfurni.params.variables.validation.invalid_variable");

        List<HabboItem> nextReferenceItems = new ArrayList<>();
        if (nextAmountMode == AMOUNT_VARIABLE && nextReferenceTargetType == TARGET_FURNI && nextReferenceFurniSource == SOURCE_SECONDARY_SELECTED) {
            int selectionLimit = Emulator.getConfig().getInt("hotel.wired.furni.selection.count");
            if (settings.getFurniIds().length > selectionLimit) throw new WiredSaveException("Too many furni selected");

            for (int furniId : settings.getFurniIds()) {
                HabboItem item = room.getHabboItem(furniId);
                if (item != null) nextReferenceItems.add(item);
            }
        }

        this.sortBy = nextSortBy;
        this.amountMode = nextAmountMode;
        this.amountConstantValue = nextAmountConstantValue;
        this.referenceTargetType = nextReferenceTargetType;
        this.referenceUserSource = nextReferenceUserSource;
        this.referenceFurniSource = nextReferenceFurniSource;
        this.setVariableToken(nextVariableToken);
        this.setReferenceVariableToken(nextReferenceVariableToken);
        this.referenceSelectedItems.clear();
        this.referenceSelectedItems.addAll(nextReferenceItems);
        return true;
    }

    @Override
    public String getWiredData() {
        this.refreshReferenceItems();
        return WiredManager.getGson().toJson(new JsonData(this.sortBy, this.amountMode, this.amountConstantValue, this.referenceTargetType, this.referenceUserSource, this.referenceFurniSource, this.variableToken, this.variableItemId, this.referenceVariableToken, this.referenceVariableItemId, this.toIds(this.referenceSelectedItems)));
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        this.refreshReferenceItems();
        List<HabboItem> selectedItems = new ArrayList<>();
        if (this.amountMode == AMOUNT_VARIABLE && this.referenceTargetType == TARGET_FURNI && this.referenceFurniSource == SOURCE_SECONDARY_SELECTED) {
            selectedItems.addAll(this.referenceSelectedItems);
        }

        message.appendBoolean(false);
        message.appendInt(WiredManager.MAXIMUM_FURNI_SELECTION);
        message.appendInt(selectedItems.size());
        for (HabboItem item : selectedItems) message.appendInt(item.getId());
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString(this.serializeStringData());
        message.appendInt(6);
        message.appendInt(this.sortBy);
        message.appendInt(this.amountMode);
        message.appendInt(this.amountConstantValue);
        message.appendInt(this.referenceTargetType);
        message.appendInt(this.referenceUserSource);
        message.appendInt(this.referenceFurniSource);
        message.appendInt(0);
        message.appendInt(this.getCode());
        message.appendInt(0);
        message.appendInt(0);
    }
    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.onPickUp();

        String wiredData = set.getString("wired_data");
        if (wiredData == null || wiredData.isEmpty() || !wiredData.startsWith("{")) return;

        JsonData data = WiredExtraPayloadGuard.fromJson(wiredData, JsonData.class);
        if (data == null) return;

        this.sortBy = normalizeSortBy(data.sortBy);
        this.amountMode = normalizeAmountMode(data.amountMode);
        this.amountConstantValue = normalizeAmount(data.amountConstantValue);
        this.referenceTargetType = normalizeReferenceTargetType(data.referenceTargetType);
        this.referenceUserSource = normalizeUserSource(data.referenceUserSource);
        this.referenceFurniSource = normalizeReferenceFurniSource(data.referenceFurniSource);
        this.setVariableToken(normalizeVariableToken((data.variableToken != null) ? data.variableToken : ((data.variableItemId > 0) ? String.valueOf(data.variableItemId) : "")));
        this.setReferenceVariableToken(normalizeVariableToken((data.referenceVariableToken != null) ? data.referenceVariableToken : ((data.referenceVariableItemId > 0) ? String.valueOf(data.referenceVariableItemId) : "")));

        if (room == null || data.selectedItemIds == null) return;

        for (Integer itemId : data.selectedItemIds) {
            if (itemId == null || itemId <= 0) continue;

            HabboItem item = room.getHabboItem(itemId);
            if (item != null) this.referenceSelectedItems.add(item);
        }
    }

    @Override
    public void onPickUp() {
        this.sortBy = SORT_VALUE_HIGHEST;
        this.amountMode = AMOUNT_CONSTANT;
        this.amountConstantValue = 1;
        this.referenceTargetType = TARGET_USER;
        this.referenceUserSource = WiredSourceUtil.SOURCE_TRIGGER;
        this.referenceFurniSource = WiredSourceUtil.SOURCE_TRIGGER;
        this.referenceSelectedItems.clear();
        this.setVariableToken("");
        this.setReferenceVariableToken("");
    }

    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public void onWalk(RoomUnit roomUnit, Room room, Object[] objects) {
    }

    @Override
    public boolean hasConfiguration() {
        return true;
    }

    private int resolveAmount(WiredContext ctx, Room room) {
        if (this.amountMode != AMOUNT_VARIABLE) return normalizeAmount(this.amountConstantValue);

        Integer value = this.resolveReferenceValue(ctx, room);
        return value == null ? 0 : normalizeAmount(value);
    }

    private Integer resolveReferenceValue(WiredContext ctx, Room room) {
        if (room == null) return null;

        if (this.referenceTargetType == TARGET_FURNI) {
            int source = (this.referenceFurniSource == SOURCE_SECONDARY_SELECTED) ? WiredSourceUtil.SOURCE_SELECTED : this.referenceFurniSource;
            if (source == WiredSourceUtil.SOURCE_SELECTED) this.refreshReferenceItems();

            for (HabboItem item : WiredSourceUtil.resolveItems(ctx, source, this.referenceSelectedItems)) {
                Integer value = this.readFurniReferenceValue(room, item);
                if (value != null) return value;
            }

            return null;
        }

        if (this.referenceTargetType == TARGET_CONTEXT) {
            return this.readContextReferenceValue(ctx, room);
        }

        if (this.referenceTargetType == TARGET_ROOM) {
            return this.readRoomReferenceValue(room);
        }

        for (RoomUnit roomUnit : WiredSourceUtil.resolveUsers(ctx, this.referenceUserSource)) {
            Integer value = this.readUserReferenceValue(room, roomUnit);
            if (value != null) return value;
        }

        return null;
    }

    private Integer readUserReferenceValue(Room room, RoomUnit roomUnit) {
        if (room == null || roomUnit == null) return null;

        if (isInternalVariableToken(this.referenceVariableToken)) {
            String key = getInternalVariableKey(this.referenceVariableToken);
            return canUseUserInternalReference(key) ? this.readUserInternalValue(room, roomUnit, key) : null;
        }

        WiredVariableDefinitionInfo definition = room.getUserVariableManager().getDefinitionInfo(this.referenceVariableItemId);
        if (definition == null || !definition.hasValue()) return null;

        Habbo habbo = room.getHabbo(roomUnit);
        return (habbo != null) ? room.getUserVariableManager().getCurrentValue(habbo.getHabboInfo().getId(), this.referenceVariableItemId) : null;
    }

    private Integer readFurniReferenceValue(Room room, HabboItem item) {
        if (room == null || item == null) return null;

        if (isInternalVariableToken(this.referenceVariableToken)) {
            String key = getInternalVariableKey(this.referenceVariableToken);
            return canUseFurniInternalReference(key) ? this.readFurniInternalValue(room, item, key) : null;
        }

        WiredVariableDefinitionInfo definition = room.getFurniVariableManager().getDefinitionInfo(this.referenceVariableItemId);
        return (definition != null && definition.hasValue()) ? room.getFurniVariableManager().getCurrentValue(item.getId(), this.referenceVariableItemId) : null;
    }

    private Integer readRoomReferenceValue(Room room) {
        if (room == null) return null;

        if (isInternalVariableToken(this.referenceVariableToken)) {
            String key = getInternalVariableKey(this.referenceVariableToken);
            return canUseRoomInternalReference(key) ? this.readRoomInternalValue(room, key) : null;
        }

        WiredVariableDefinitionInfo definition = room.getRoomVariableManager().getDefinitionInfo(this.referenceVariableItemId);
        return (definition != null && definition.hasValue()) ? room.getRoomVariableManager().getCurrentValue(this.referenceVariableItemId) : null;
    }

    private Integer readContextReferenceValue(WiredContext ctx, Room room) {
        if (ctx == null) return null;

        if (isInternalVariableToken(this.referenceVariableToken)) {
            String key = getInternalVariableKey(this.referenceVariableToken);
            return canUseContextInternalReference(key) ? WiredInternalVariableSupport.readContextValue(ctx, key) : null;
        }

        WiredVariableDefinitionInfo definition = WiredContextVariableSupport.getDefinitionInfo(room, this.referenceVariableItemId);
        if (definition == null || !definition.hasValue() || !WiredContextVariableSupport.hasVariable(ctx, this.referenceVariableItemId)) return null;

        return WiredContextVariableSupport.getCurrentValue(ctx, this.referenceVariableItemId);
    }

    private MetricSnapshot resolveUserMetric(Room room, RoomUnit roomUnit) {
        if (room == null || roomUnit == null) return null;

        if (isInternalVariableToken(this.variableToken)) {
            String key = getInternalVariableKey(this.variableToken);
            Integer value = canUseUserInternalReference(key) ? this.readUserInternalValue(room, roomUnit, key) : null;
            return (value != null) ? new MetricSnapshot(roomUnit.getId(), value, 0, 0) : null;
        }

        WiredVariableDefinitionInfo definition = room.getUserVariableManager().getDefinitionInfo(this.variableItemId);
        Habbo habbo = room.getHabbo(roomUnit);
        if (definition == null || habbo == null) return null;
        if (!room.getUserVariableManager().hasVariable(habbo.getHabboInfo().getId(), this.variableItemId)) return null;

        return new MetricSnapshot(
            roomUnit.getId(),
            definition.hasValue() ? room.getUserVariableManager().getCurrentValue(habbo.getHabboInfo().getId(), this.variableItemId) : 0,
            room.getUserVariableManager().getCreatedAt(habbo.getHabboInfo().getId(), this.variableItemId),
            room.getUserVariableManager().getUpdatedAt(habbo.getHabboInfo().getId(), this.variableItemId));
    }

    private MetricSnapshot resolveFurniMetric(Room room, HabboItem item) {
        if (room == null || item == null) return null;

        if (isInternalVariableToken(this.variableToken)) {
            String key = getInternalVariableKey(this.variableToken);
            Integer value = canUseFurniInternalReference(key) ? this.readFurniInternalValue(room, item, key) : null;
            return (value != null) ? new MetricSnapshot(item.getId(), value, 0, 0) : null;
        }

        WiredVariableDefinitionInfo definition = room.getFurniVariableManager().getDefinitionInfo(this.variableItemId);
        if (definition == null) return null;
        if (!room.getFurniVariableManager().hasVariable(item.getId(), this.variableItemId)) return null;

        return new MetricSnapshot(
            item.getId(),
            definition.hasValue() ? room.getFurniVariableManager().getCurrentValue(item.getId(), this.variableItemId) : 0,
            room.getFurniVariableManager().getCreatedAt(item.getId(), this.variableItemId),
            room.getFurniVariableManager().getUpdatedAt(item.getId(), this.variableItemId));
    }

    private Comparator<SortableEntry<?>> metricComparator() {
        return switch (this.sortBy) {
            case SORT_VALUE_LOWEST -> Comparator.comparingInt((SortableEntry<?> entry) -> entry.metric.value).thenComparingInt(entry -> entry.metric.entityId);
            case SORT_CREATION_OLDEST -> Comparator.comparingInt((SortableEntry<?> entry) -> entry.metric.createdAt).thenComparingInt(entry -> entry.metric.entityId);
            case SORT_CREATION_LATEST -> Comparator.<SortableEntry<?>, Integer>comparing(entry -> entry.metric.createdAt).reversed().thenComparingInt(entry -> entry.metric.entityId);
            case SORT_UPDATE_OLDEST -> Comparator.comparingInt((SortableEntry<?> entry) -> entry.metric.updatedAt).thenComparingInt(entry -> entry.metric.entityId);
            case SORT_UPDATE_LATEST -> Comparator.<SortableEntry<?>, Integer>comparing(entry -> entry.metric.updatedAt).reversed().thenComparingInt(entry -> entry.metric.entityId);
            default -> Comparator.<SortableEntry<?>, Integer>comparing(entry -> entry.metric.value).reversed().thenComparingInt(entry -> entry.metric.entityId);
        };
    }

    private boolean isValidMainVariable(Room room, String token) {
        if (token == null || token.isEmpty()) return false;

        if (isInternalVariableToken(token)) {
            String key = getInternalVariableKey(token);
            return this.getVariableTargetType() == TARGET_FURNI ? canUseFurniInternalReference(key) : canUseUserInternalReference(key);
        }

        if (this.getVariableTargetType() == TARGET_FURNI) {
            return room != null && room.getFurniVariableManager().getDefinitionInfo(getCustomItemId(token)) != null;
        }

        return room != null && room.getUserVariableManager().getDefinitionInfo(getCustomItemId(token)) != null;
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
    private static List<RoomUnit> trimUsers(List<SortableEntry<RoomUnit>> matches, int amount) {
        List<RoomUnit> result = new ArrayList<>();
        for (SortableEntry<RoomUnit> match : matches) {
            if (result.size() >= amount) break;
            result.add(match.entity);
        }
        return result;
    }

    private static List<HabboItem> trimItems(List<SortableEntry<HabboItem>> matches, int amount) {
        List<HabboItem> result = new ArrayList<>();
        for (SortableEntry<HabboItem> match : matches) {
            if (result.size() >= amount) break;
            result.add(match.entity);
        }
        return result;
    }

    private static List<RoomUnit> toUserList(Iterable<RoomUnit> values) {
        List<RoomUnit> result = new ArrayList<>();
        if (values == null) return result;
        for (RoomUnit value : values) if (value != null) result.add(value);
        return result;
    }

    private static List<HabboItem> toItemList(Iterable<HabboItem> values) {
        List<HabboItem> result = new ArrayList<>();
        if (values == null) return result;
        for (HabboItem value : values) if (value != null) result.add(value);
        return result;
    }

    private String serializeStringData() {
        return (this.variableToken == null ? "" : this.variableToken) + DELIM + (this.referenceVariableToken == null ? "" : this.referenceVariableToken);
    }

    private void refreshReferenceItems() {
        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());
        if (room == null) {
            this.referenceSelectedItems.clear();
            return;
        }

        this.referenceSelectedItems.removeIf(item -> item == null || item.getRoomId() != room.getId() || room.getHabboItem(item.getId()) == null);
    }

    private void setVariableToken(String token) {
        this.variableToken = normalizeVariableToken(token);
        this.variableItemId = getCustomItemId(this.variableToken);
    }

    private void setReferenceVariableToken(String token) {
        this.referenceVariableToken = normalizeVariableToken(token);
        this.referenceVariableItemId = getCustomItemId(this.referenceVariableToken);
    }

    private List<Integer> toIds(List<HabboItem> items) {
        List<Integer> ids = new ArrayList<>();
        for (HabboItem item : items) if (item != null) ids.add(item.getId());
        return ids;
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
    private int getUserTeamScore(Room room, Habbo habbo) {
        if (room == null || habbo == null || habbo.getHabboInfo() == null || habbo.getHabboInfo().getGamePlayer() == null) return 0;

        Game game = this.resolveTeamGame(room, habbo);
        if (game == null) return 0;

        GamePlayer player = habbo.getHabboInfo().getGamePlayer();
        return player.getScore();
    }

    private int getTeamColorId(int effectValue) {
        TeamEffectData effectData = this.getTeamEffectData(effectValue);
        return (effectData != null) ? effectData.colorId : 0;
    }

    private int getTeamTypeId(int effectValue) {
        TeamEffectData effectData = this.getTeamEffectData(effectValue);
        return (effectData != null) ? effectData.typeId : 0;
    }

    private int getTeamMetric(Room room, GameTeamColors color, boolean score) {
        Game game = room.getGame(WiredGame.class);
        if (game == null) game = room.getGame(FreezeGame.class);
        if (game == null) game = room.getGame(BattleBanzaiGame.class);
        if (game == null) return 0;

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

    private static int normalizeSortBy(int value) {
        return switch (value) {
            case SORT_VALUE_LOWEST, SORT_CREATION_OLDEST, SORT_CREATION_LATEST, SORT_UPDATE_OLDEST, SORT_UPDATE_LATEST -> value;
            default -> SORT_VALUE_HIGHEST;
        };
    }

    private static int normalizeAmountMode(int value) {
        return (value == AMOUNT_VARIABLE) ? AMOUNT_VARIABLE : AMOUNT_CONSTANT;
    }

    private static int normalizeReferenceTargetType(int value) {
        return switch (value) {
            case TARGET_FURNI, TARGET_CONTEXT, TARGET_ROOM -> value;
            default -> TARGET_USER;
        };
    }

    private static int normalizeUserSource(int value) {
        return WiredSourceUtil.isDefaultUserSource(value) ? value : WiredSourceUtil.SOURCE_TRIGGER;
    }

    private static int normalizeReferenceFurniSource(int value) {
        return switch (value) {
            case SOURCE_SECONDARY_SELECTED, WiredSourceUtil.SOURCE_SELECTOR, WiredSourceUtil.SOURCE_SIGNAL -> value;
            default -> WiredSourceUtil.SOURCE_TRIGGER;
        };
    }

    protected static String normalizeVariableToken(String token) {
        if (token == null) return "";

        String normalized = token.trim();
        if (normalized.isEmpty()) return "";
        if (normalized.startsWith(INTERNAL_TOKEN_PREFIX)) {
            return INTERNAL_TOKEN_PREFIX + WiredInternalVariableSupport.normalizeKey(normalized.substring(INTERNAL_TOKEN_PREFIX.length()));
        }
        if (isCustomVariableToken(normalized) || isInternalVariableToken(normalized)) return normalized;

        try {
            int parsed = Integer.parseInt(normalized);
            return (parsed > 0) ? (CUSTOM_TOKEN_PREFIX + parsed) : "";
        } catch (NumberFormatException e) {
            return "";
        }
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

    private static int normalizeAmount(int value) {
        return Math.max(0, Math.min(MAX_FILTER_AMOUNT, value));
    }

    protected static class JsonData {
        int sortBy;
        int amountMode;
        int amountConstantValue;
        int referenceTargetType;
        int referenceUserSource;
        int referenceFurniSource;
        String variableToken;
        int variableItemId;
        String referenceVariableToken;
        int referenceVariableItemId;
        List<Integer> selectedItemIds;

        JsonData(int sortBy, int amountMode, int amountConstantValue, int referenceTargetType, int referenceUserSource, int referenceFurniSource, String variableToken, int variableItemId, String referenceVariableToken, int referenceVariableItemId, List<Integer> selectedItemIds) {
            this.sortBy = sortBy;
            this.amountMode = amountMode;
            this.amountConstantValue = amountConstantValue;
            this.referenceTargetType = referenceTargetType;
            this.referenceUserSource = referenceUserSource;
            this.referenceFurniSource = referenceFurniSource;
            this.variableToken = variableToken;
            this.variableItemId = variableItemId;
            this.referenceVariableToken = referenceVariableToken;
            this.referenceVariableItemId = referenceVariableItemId;
            this.selectedItemIds = selectedItemIds;
        }
    }

    private static class SortableEntry<T> {
        final T entity;
        final MetricSnapshot metric;

        SortableEntry(T entity, MetricSnapshot metric) {
            this.entity = entity;
            this.metric = metric;
        }
    }

    private static class MetricSnapshot {
        final int entityId;
        final int value;
        final int createdAt;
        final int updatedAt;

        MetricSnapshot(int entityId, int value, int createdAt, int updatedAt) {
            this.entityId = entityId;
            this.value = value;
            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
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
