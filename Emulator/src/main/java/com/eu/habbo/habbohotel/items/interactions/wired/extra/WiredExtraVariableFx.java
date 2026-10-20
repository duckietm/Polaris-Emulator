package com.eu.habbo.habbohotel.items.interactions.wired.extra;

import com.eu.habbo.WiredPlatform;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.wired.core.WiredExecutionOrderUtil;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredPointerVariableSupport;
import com.eu.habbo.habbohotel.wired.protocol.WiredOfficialVariableIds;
import com.eu.habbo.habbohotel.wired.protocol.WiredVariableIds;
import com.eu.habbo.habbohotel.wired.protocol.WiredVariableTargets;
import com.eu.habbo.habbohotel.wired.variablefx.WiredVariableFxConfig;
import com.eu.habbo.habbohotel.wired.variablefx.WiredVariableFxStatus;
import com.eu.habbo.habbohotel.wired.variablefx.WiredVariableFxStyles;
import com.eu.habbo.habbohotel.wired.variablefx.WiredVariableFxSupport;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.IntUnaryOperator;

/**
 * A variable fx addon: it draws the variable box it shares a tile with over every avatar or furni
 * that holds the variable (a health bar, a level badge, a number...). The box holds no state and
 * sends nothing itself; {@link com.eu.habbo.habbohotel.wired.variablefx.WiredVariableFxService}
 * works out, per player, what to show and tells the client.
 *
 * <p>Int params, as the Octane editor writes them: source (0 user, 1 furni), visibility, show mode,
 * show duration (ms), style, colour, width, segments, default min, default max, override min on,
 * override max on, override min target (0 holder, 1 global), override max target, audience value,
 * category extra (the level badge's bar, the number's icon alignment). The string param carries
 * the three variable tokens and the icon, tab separated: override min, override max, audience,
 * icon. A token is {@code custom:<definition item id>}.
 */
public abstract class WiredExtraVariableFx extends InteractionWiredExtra implements WiredOfficialVariableIds {
    public static final int SOURCE_USER = 0;
    public static final int SOURCE_FURNI = 1;

    public static final int VISIBILITY_ONLY_USER = 0;
    public static final int VISIBILITY_GAME_TEAM = 1;
    public static final int VISIBILITY_EVERYONE = 2;
    public static final int VISIBILITY_HAS_VARIABLE = 3;
    public static final int VISIBILITY_HAS_VARIABLE_WITH_VALUE = 4;

    public static final int SHOW_ALWAYS = 0;
    public static final int SHOW_WHEN_CHANGES = 1;
    public static final int SHOW_NEVER = 2;

    public static final int OVERRIDE_TARGET_HOLDER = 0;
    public static final int OVERRIDE_TARGET_GLOBAL = 1;

    public static final int COLOR_NOT_APPLICABLE = -1;
    public static final int COLOR_DYNAMIC_LEVELLING = 1001;
    public static final int COLOR_DYNAMIC_TEAM = 1002;
    public static final int WIDTH_MEDIUM = 2;

    public static final int SHOW_DURATION_MIN_MS = 1500;
    public static final int SHOW_DURATION_MAX_MS = 20000;
    public static final int SHOW_DURATION_DEFAULT_MS = 3000;
    /** Show-when-changes refreshers, as AIR's client counts them: created, increased, decreased, unchanged. */
    static final int UPDATE_CREATED = 1;

    static final int UPDATE_INCREASED = 2;
    static final int UPDATE_DECREASED = 4;
    static final int UPDATE_UNCHANGED = 8;
    /** Any real change: what "show when changes" meant before boxes had a mask. */
    static final int UPDATE_MASK_DEFAULT = UPDATE_CREATED | UPDATE_INCREASED | UPDATE_DECREASED;

    public static final int SEGMENTS_MAX = 100;
    public static final int DEFAULT_MAX_VALUE = 100;
    public static final int PARAM_COUNT = 16;

    // The fx's place in its holder's stack; AIR's client reads it from the config extra.
    static final String EXTRA_STACK_ORDER = "stack_order";
    protected static final String STATUS_CURRENT_LEVEL = "current_level";
    protected static final String STATUS_MAX_LEVEL = "max_level";
    protected static final String STATUS_IS_MAXED = "is_maxed";
    protected static final String STATUS_DELEGATED_COLOR = "delegated_color";

    private static final String CUSTOM_TOKEN_PREFIX = "custom:";
    private static final String DELIM = "\t";
    private static final int ICON_MAX_LENGTH = 32;

    private int source = SOURCE_USER;
    private int visibility = VISIBILITY_EVERYONE;
    private int showMode = SHOW_ALWAYS;
    private int showDurationMs = SHOW_DURATION_DEFAULT_MS;
    private int styleId = 0;
    private int colorId = COLOR_NOT_APPLICABLE;
    private int widthId = WIDTH_MEDIUM;
    private int segments = 0;
    private int defaultMin = 0;
    private int defaultMax = DEFAULT_MAX_VALUE;
    private boolean overrideMinEnabled = false;
    private boolean overrideMaxEnabled = false;
    private int overrideMinTarget = OVERRIDE_TARGET_HOLDER;
    private int overrideMaxTarget = OVERRIDE_TARGET_HOLDER;
    private int audienceValue = 0;
    private int categoryExtra = 0;
    private int overrideMinItemId = 0;
    private int overrideMaxItemId = 0;
    private int audienceItemId = 0;
    private String icon = "";
    // Official only: refresh mask, show on hover, renderer, fx stack order.
    private int updateMask = UPDATE_MASK_DEFAULT;
    private boolean showOnHover = false;
    private int rendererParam = 0;
    private int stackOrder = 0;

    protected WiredExtraVariableFx(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    protected WiredExtraVariableFx(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    /** The client's category of this fx (see {@link WiredVariableFxStyles}). */
    public abstract int getCategory();

    /** The wired layout code the client picks the editor by. */
    public abstract int getCode();

    /** False for the categories whose editor has no min and max (levels, plain numbers). */
    protected boolean usesValueRange() {
        return true;
    }

    /** Config extras only this category has. */
    protected void addCategoryExtra(Map<String, String> extra, WiredVariableFxStyles.Style style, int rendererId) {}

    /** The renderer a segment count applies to; a level badge passes it to its bar. */
    protected int getSegmentRendererId(int rendererId) {
        return rendererId;
    }

    // ---- what the fx system reads ---------------------------------------------------------

    public boolean isUserFx() {
        return this.source == SOURCE_USER;
    }

    /**
     * Who is shown the fx. A furni has no owner to be "only" for and no team, so those two read
     * as everyone.
     */
    public int getVisibility() {
        if (!this.isUserFx() && (this.visibility == VISIBILITY_ONLY_USER || this.visibility == VISIBILITY_GAME_TEAM)) {
            return VISIBILITY_EVERYONE;
        }
        return this.visibility;
    }

    public int getAudienceValue() {
        return this.audienceValue;
    }

    public int getAudienceItemId() {
        return this.audienceItemId;
    }

    public int getShowMode() {
        return this.showMode;
    }

    public int getCategoryExtra() {
        return this.categoryExtra;
    }

    public String getIcon() {
        return this.icon;
    }

    /** The variable box on this tile that fits the source, null when there is none. */
    public InteractionWiredExtra getShownVariableBox(Room room) {
        if (room == null || room.getRoomSpecialTypes() == null) return null;

        Collection<InteractionWiredExtra> extras = room.getRoomSpecialTypes().getExtras(this.getX(), this.getY());
        if (extras == null || extras.isEmpty()) return null;

        for (InteractionWiredExtra extra : WiredExecutionOrderUtil.sort(extras)) {
            if (this.isUserFx() && extra instanceof WiredExtraUserVariable) return extra;
            if (!this.isUserFx() && extra instanceof WiredExtraFurniVariable) return extra;
        }

        return null;
    }

    /**
     * How the fx looks. Everything in it has been checked against what the client can draw: the
     * renderer is the picked one when the style offers it (status bars offer three), the style's
     * default otherwise. "Never" is AIR's "visible on cursor hover", so it always shows on hover; a
     * non-zero stack order rides in the extra, where AIR's client reads it.
     */
    public WiredVariableFxConfig buildConfig() {
        WiredVariableFxStyles.Style style = WiredVariableFxStyles.get(this.getCategory(), this.styleId);
        int rendererId = style.resolveRenderer(this.rendererParam);
        Map<String, String> extra = new LinkedHashMap<>(style.extra());

        this.addCategoryExtra(extra, style, rendererId);

        if (this.segments > 0 && WiredVariableFxStyles.supportsSegments(this.getSegmentRendererId(rendererId))) {
            extra.put(WiredVariableFxStyles.EXTRA_SEGMENTS, Integer.toString(this.segments));
        }

        if (this.stackOrder != 0) {
            extra.put(EXTRA_STACK_ORDER, Integer.toString(this.stackOrder));
        }

        long[] range = this.getDefaultRange();

        return new WiredVariableFxConfig(
                this.getId(),
                this.isUserFx(),
                this.showMode,
                this.updateMask,
                this.showOnHover || this.showMode == SHOW_NEVER,
                this.showDurationMs,
                this.getCategory(),
                style.styleId(),
                this.colorId,
                this.widthId,
                rendererId,
                range[0],
                range[1],
                extra);
    }

    /**
     * What one holder of the variable shows: its value, the range when it is not the config's
     * default one, and the extras the client's renderer reads per status.
     */
    public WiredVariableFxStatus resolveStatus(
            Room room,
            InteractionWiredExtra variableBox,
            WiredVariableFxStatus.Key key,
            int holderId,
            long value,
            String teamColor) {
        return this.resolveStatus(room, variableBox, key, holderId, value, teamColor, IntUnaryOperator.identity());
    }

    /**
     * The same, with the override variables' ids read through {@code variableIds}: an fx imported from
     * another room names that room's variables, which the importing room knows by its references' ids.
     */
    public WiredVariableFxStatus resolveStatus(
            Room room,
            InteractionWiredExtra variableBox,
            WiredVariableFxStatus.Key key,
            int holderId,
            long value,
            String teamColor,
            IntUnaryOperator variableIds) {
        Map<String, String> extra = new LinkedHashMap<>();

        this.addColorExtra(extra, teamColor);

        long[] range = this.resolveOverriddenRange(room, holderId, variableIds);

        return new WiredVariableFxStatus(
                key, false, value, (range != null) ? range[0] : null, (range != null) ? range[1] : null, extra);
    }

    /**
     * The extras the two per-status colours need. The levelling colour reads the level from every
     * status, so an fx that is not about levels still says "level one of one".
     */
    protected void addColorExtra(Map<String, String> extra, String teamColor) {
        if (this.colorId == COLOR_DYNAMIC_TEAM && teamColor != null) {
            extra.put(STATUS_DELEGATED_COLOR, teamColor);
        }

        if (this.colorId == COLOR_DYNAMIC_LEVELLING) {
            addLevelExtra(extra, 1, 1, false);
        }
    }

    protected static void addLevelExtra(Map<String, String> extra, int level, int maxLevel, boolean maxed) {
        extra.put(STATUS_CURRENT_LEVEL, Integer.toString(level));
        extra.put(STATUS_MAX_LEVEL, Integer.toString(maxLevel));
        extra.put(STATUS_IS_MAXED, maxed ? "true" : "false");
    }

    /** The config's range, repaired so a maximum that is not above the minimum never divides by zero. */
    protected long[] getDefaultRange() {
        if (!this.usesValueRange()) return new long[] {0, DEFAULT_MAX_VALUE};

        long min = this.defaultMin;
        long max = this.defaultMax;

        if (max <= min) max = (min < DEFAULT_MAX_VALUE) ? DEFAULT_MAX_VALUE : min + 1;

        return new long[] {min, max};
    }

    /**
     * The holder's own range when a variable replaces either end of it, null when neither does.
     * The client takes the two ends as a pair, so the end without a variable repeats the default.
     */
    private long[] resolveOverriddenRange(Room room, int holderId, IntUnaryOperator variableIds) {
        if (!this.usesValueRange()) return null;

        Long min = this.readOverride(
                room,
                this.overrideMinEnabled,
                this.overrideMinTarget,
                localId(this.overrideMinItemId, variableIds),
                holderId);
        Long max = this.readOverride(
                room,
                this.overrideMaxEnabled,
                this.overrideMaxTarget,
                localId(this.overrideMaxItemId, variableIds),
                holderId);

        if (min == null && max == null) return null;

        long[] defaults = this.getDefaultRange();
        return new long[] {(min != null) ? min : defaults[0], (max != null) ? max : defaults[1]};
    }

    /** The id the drawing room knows a chosen variable by; nothing chosen stays nothing. */
    private static int localId(int itemId, IntUnaryOperator variableIds) {
        return itemId > 0 ? variableIds.applyAsInt(itemId) : itemId;
    }

    /**
     * One end of the range from a variable: the holder's own value of it, or a room-wide variable.
     * Null when the option is off, the variable is gone or has no value for this holder.
     */
    private Long readOverride(Room room, boolean enabled, int target, int itemId, int holderId) {
        if (!enabled || itemId <= 0 || room == null) return null;

        if (target == OVERRIDE_TARGET_GLOBAL) {
            return room.getRoomVariableManager().hasVariable(itemId)
                    ? (long) room.getRoomVariableManager().getCurrentValue(itemId)
                    : null;
        }

        if (this.isUserFx()) {
            return room.getUserVariableManager().hasVariable(holderId, itemId)
                    ? (long) room.getUserVariableManager().getCurrentValue(holderId, itemId)
                    : null;
        }

        return room.getFurniVariableManager().hasVariable(holderId, itemId)
                ? (long) room.getFurniVariableManager().getCurrentValue(holderId, itemId)
                : null;
    }

    // ---- wired plumbing --------------------------------------------------------------------

    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString(this.serializeStringParam());
        int[] params = this.officialIntParams();
        message.appendInt(params.length);
        for (int param : params) message.appendInt(param);
        message.appendInt(0);
        message.appendInt(this.getCode());
        message.appendInt(0);
        message.appendInt(0);
    }

    /**
     * The official int params (23): source (official target), visibility, show mode, update mask, show
     * on hover, show duration, style, colour, width, renderer, default min (long), default max (long),
     * override min on, override max on, override min target, override max target (the holder's
     * official target, or global), audience value (long), segments, category extra, stack order.
     */
    int[] officialIntParams() {
        int holder =
                this.source == SOURCE_FURNI ? WiredVariableTargets.OFFICIAL_FURNI : WiredVariableTargets.OFFICIAL_USER;
        return new int[] {
            holder,
            this.visibility,
            this.showMode,
            this.updateMask,
            this.showOnHover ? 1 : 0,
            this.showDurationMs,
            this.styleId,
            this.colorId,
            this.widthId,
            this.rendererParam,
            hiWord(this.defaultMin),
            this.defaultMin,
            hiWord(this.defaultMax),
            this.defaultMax,
            this.overrideMinEnabled ? 1 : 0,
            this.overrideMaxEnabled ? 1 : 0,
            this.overrideMinTarget == OVERRIDE_TARGET_GLOBAL ? WiredVariableTargets.OFFICIAL_GLOBAL : holder,
            this.overrideMaxTarget == OVERRIDE_TARGET_GLOBAL ? WiredVariableTargets.OFFICIAL_GLOBAL : holder,
            hiWord(this.audienceValue),
            this.audienceValue,
            this.segments,
            this.categoryExtra,
            this.stackOrder
        };
    }

    @Override
    public String[] officialVariableIds() {
        int holder =
                this.source == SOURCE_FURNI ? WiredVariableTargets.OFFICIAL_FURNI : WiredVariableTargets.OFFICIAL_USER;
        return WiredVariableIds.list(
                WiredVariableIds.toWire(
                        this.overrideMinTarget == OVERRIDE_TARGET_GLOBAL
                                ? WiredVariableTargets.OFFICIAL_GLOBAL
                                : holder,
                        this.overrideMinItemId),
                WiredVariableIds.toWire(
                        this.overrideMaxTarget == OVERRIDE_TARGET_GLOBAL
                                ? WiredVariableTargets.OFFICIAL_GLOBAL
                                : holder,
                        this.overrideMaxItemId),
                WiredVariableIds.toWire(WiredVariableTargets.OFFICIAL_USER, this.audienceItemId));
    }

    private static int hiWord(int value) {
        return value < 0 ? -1 : 0;
    }

    /** An official long param, clamped to the int range this box keeps. */
    private static int longParam(int[] params, int index, int fallback) {
        if (params == null || index + 1 >= params.length) return fallback;
        long value = ((long) params[index] << 32) | (params[index + 1] & 0xFFFFFFFFL);
        return (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, value));
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        int[] params = settings.getIntParams();
        String[] tokens =
                (settings.getStringParam() != null) ? settings.getStringParam().split(DELIM, -1) : new String[0];

        if (params.length != PARAM_COUNT) {
            return this.saveOfficial(params, tokens, settings.getVariableIds());
        }

        this.source = (param(params, 0, SOURCE_USER) == SOURCE_FURNI) ? SOURCE_FURNI : SOURCE_USER;
        this.visibility =
                clamp(param(params, 1, VISIBILITY_EVERYONE), VISIBILITY_ONLY_USER, VISIBILITY_HAS_VARIABLE_WITH_VALUE);
        this.showMode = clamp(param(params, 2, SHOW_ALWAYS), SHOW_ALWAYS, SHOW_NEVER);
        this.showDurationMs =
                clamp(param(params, 3, SHOW_DURATION_DEFAULT_MS), SHOW_DURATION_MIN_MS, SHOW_DURATION_MAX_MS);
        this.styleId =
                clamp(param(params, 4, 0), 0, Math.max(0, WiredVariableFxStyles.styleCount(this.getCategory()) - 1));
        this.colorId = normalizeColor(param(params, 5, COLOR_NOT_APPLICABLE));
        this.widthId = clamp(param(params, 6, WIDTH_MEDIUM), -1, 100);
        this.segments = clamp(param(params, 7, 0), 0, SEGMENTS_MAX);
        this.defaultMin = param(params, 8, 0);
        this.defaultMax = param(params, 9, DEFAULT_MAX_VALUE);
        this.overrideMinEnabled = param(params, 10, 0) == 1;
        this.overrideMaxEnabled = param(params, 11, 0) == 1;
        this.overrideMinTarget = (param(params, 12, OVERRIDE_TARGET_HOLDER) == OVERRIDE_TARGET_GLOBAL)
                ? OVERRIDE_TARGET_GLOBAL
                : OVERRIDE_TARGET_HOLDER;
        this.overrideMaxTarget = (param(params, 13, OVERRIDE_TARGET_HOLDER) == OVERRIDE_TARGET_GLOBAL)
                ? OVERRIDE_TARGET_GLOBAL
                : OVERRIDE_TARGET_HOLDER;
        this.audienceValue = param(params, 14, 0);
        this.categoryExtra = param(params, 15, 0);
        this.updateMask = UPDATE_MASK_DEFAULT;
        this.rejectPointers(
                customItemId(token(tokens, 0)), customItemId(token(tokens, 1)), customItemId(token(tokens, 2)));
        this.overrideMinItemId = customItemId(token(tokens, 0));
        this.overrideMaxItemId = customItemId(token(tokens, 1));
        this.audienceItemId = customItemId(token(tokens, 2));
        this.icon = normalizeIcon(token(tokens, 3));

        if (this.usesValueRange() && this.defaultMax <= this.defaultMin) {
            throw new WiredSaveException("wiredfurni.params.variablefx.validation.range");
        }

        return true;
    }

    private boolean saveOfficial(int[] params, String[] tokens, String[] variableIds) throws WiredSaveException {
        int holder = param(params, 0, WiredVariableTargets.OFFICIAL_USER);
        int nextDefaultMin = longParam(params, 10, 0);
        int nextDefaultMax = longParam(params, 12, DEFAULT_MAX_VALUE);
        if (this.usesValueRange() && nextDefaultMax <= nextDefaultMin) {
            throw new WiredSaveException("wiredfurni.params.variablefx.validation.range");
        }
        boolean officialTokens = !token(tokens, 0).isEmpty()
                || !token(tokens, 1).isEmpty()
                || !token(tokens, 2).isEmpty();
        this.rejectPointers(
                officialTokens ? customItemId(token(tokens, 0)) : officialId(variableIds, 0),
                officialTokens ? customItemId(token(tokens, 1)) : officialId(variableIds, 1),
                officialTokens ? customItemId(token(tokens, 2)) : officialId(variableIds, 2));

        this.source = (holder == WiredVariableTargets.OFFICIAL_FURNI) ? SOURCE_FURNI : SOURCE_USER;
        this.visibility =
                clamp(param(params, 1, VISIBILITY_EVERYONE), VISIBILITY_ONLY_USER, VISIBILITY_HAS_VARIABLE_WITH_VALUE);
        this.showMode = clamp(param(params, 2, SHOW_ALWAYS), SHOW_ALWAYS, SHOW_NEVER);
        this.updateMask = clamp(param(params, 3, 0), 0, 15);
        this.showOnHover = param(params, 4, 0) == 1;
        this.showDurationMs =
                clamp(param(params, 5, SHOW_DURATION_DEFAULT_MS), SHOW_DURATION_MIN_MS, SHOW_DURATION_MAX_MS);
        this.styleId =
                clamp(param(params, 6, 0), 0, Math.max(0, WiredVariableFxStyles.styleCount(this.getCategory()) - 1));
        this.colorId = normalizeColor(param(params, 7, COLOR_NOT_APPLICABLE));
        this.widthId = clamp(param(params, 8, WIDTH_MEDIUM), -1, 100);
        this.rendererParam = clamp(param(params, 9, 0), -1, 201);
        this.defaultMin = nextDefaultMin;
        this.defaultMax = nextDefaultMax;
        this.overrideMinEnabled = param(params, 14, 0) == 1;
        this.overrideMaxEnabled = param(params, 15, 0) == 1;
        this.overrideMinTarget = param(params, 16, holder) == WiredVariableTargets.OFFICIAL_GLOBAL
                ? OVERRIDE_TARGET_GLOBAL
                : OVERRIDE_TARGET_HOLDER;
        this.overrideMaxTarget = param(params, 17, holder) == WiredVariableTargets.OFFICIAL_GLOBAL
                ? OVERRIDE_TARGET_GLOBAL
                : OVERRIDE_TARGET_HOLDER;
        this.audienceValue = longParam(params, 18, 0);
        this.segments = clamp(param(params, 20, 0), 0, SEGMENTS_MAX);
        this.categoryExtra = param(params, 21, 0);
        this.stackOrder = param(params, 22, 0);

        // Ours: the variable tokens and the icon as text; an official save sends the ids apart.
        boolean hasTokens = !token(tokens, 0).isEmpty()
                || !token(tokens, 1).isEmpty()
                || !token(tokens, 2).isEmpty();
        this.overrideMinItemId = hasTokens ? customItemId(token(tokens, 0)) : officialId(variableIds, 0);
        this.overrideMaxItemId = hasTokens ? customItemId(token(tokens, 1)) : officialId(variableIds, 1);
        this.audienceItemId = hasTokens ? customItemId(token(tokens, 2)) : officialId(variableIds, 2);
        this.icon = normalizeIcon(token(tokens, 3));
        return true;
    }

    /** AIR's override and audience pickers leave out pointers: an fx is drawn without a running stack. */
    private void rejectPointers(int... itemIds) throws WiredSaveException {
        Room room = WiredPlatform.isReady()
                ? WiredPlatform.gameEnvironment().getRoomManager().getRoom(this.getRoomId())
                : null;
        if (room != null && WiredPointerVariableSupport.anyPointer(room, itemIds)) {
            throw new WiredSaveException(WiredPointerVariableSupport.REQUIRES_EXECUTION_CONTEXT_ERROR);
        }
    }

    private static int officialId(String[] variableIds, int index) {
        // The variable list's id (user:12); a bare box id from an older client still reads.
        if (variableIds == null || index >= variableIds.length) return 0;
        return WiredVariableIds.boxIdOf(variableIds[index]);
    }

    @Override
    public String getWiredData() {
        JsonData data = new JsonData(
                this.source,
                this.visibility,
                this.showMode,
                this.showDurationMs,
                this.styleId,
                this.colorId,
                this.widthId,
                this.segments,
                this.defaultMin,
                this.defaultMax,
                this.overrideMinEnabled,
                this.overrideMaxEnabled,
                this.overrideMinTarget,
                this.overrideMaxTarget,
                this.audienceValue,
                this.categoryExtra,
                this.overrideMinItemId,
                this.overrideMaxItemId,
                this.audienceItemId,
                this.icon);
        data.updateMask = this.updateMask;
        data.showOnHover = this.showOnHover;
        data.rendererParam = this.rendererParam;
        data.stackOrder = this.stackOrder;
        return WiredManager.getGson().toJson(data);
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.onPickUp();

        String wiredData = set.getString("wired_data");
        if (wiredData == null || !wiredData.startsWith("{")) return;

        JsonData data = WiredExtraPayloadGuard.fromJson(wiredData, JsonData.class);
        if (data == null) return;

        this.source = (data.source == SOURCE_FURNI) ? SOURCE_FURNI : SOURCE_USER;
        this.visibility = clamp(data.visibility, VISIBILITY_ONLY_USER, VISIBILITY_HAS_VARIABLE_WITH_VALUE);
        this.showMode = clamp(data.showMode, SHOW_ALWAYS, SHOW_NEVER);
        this.showDurationMs = clamp(data.showDurationMs, SHOW_DURATION_MIN_MS, SHOW_DURATION_MAX_MS);
        this.styleId = clamp(data.styleId, 0, Math.max(0, WiredVariableFxStyles.styleCount(this.getCategory()) - 1));
        this.colorId = normalizeColor(data.colorId);
        this.widthId = clamp(data.widthId, -1, 100);
        this.segments = clamp(data.segments, 0, SEGMENTS_MAX);
        this.defaultMin = data.defaultMin;
        this.defaultMax = data.defaultMax;
        this.overrideMinEnabled = data.overrideMinEnabled;
        this.overrideMaxEnabled = data.overrideMaxEnabled;
        this.overrideMinTarget =
                (data.overrideMinTarget == OVERRIDE_TARGET_GLOBAL) ? OVERRIDE_TARGET_GLOBAL : OVERRIDE_TARGET_HOLDER;
        this.overrideMaxTarget =
                (data.overrideMaxTarget == OVERRIDE_TARGET_GLOBAL) ? OVERRIDE_TARGET_GLOBAL : OVERRIDE_TARGET_HOLDER;
        this.audienceValue = data.audienceValue;
        this.categoryExtra = data.categoryExtra;
        this.overrideMinItemId = Math.max(0, data.overrideMinItemId);
        this.overrideMaxItemId = Math.max(0, data.overrideMaxItemId);
        this.audienceItemId = Math.max(0, data.audienceItemId);
        this.icon = normalizeIcon(data.icon);
        this.updateMask = (data.updateMask != null) ? clamp(data.updateMask, 0, 15) : UPDATE_MASK_DEFAULT;
        this.showOnHover = data.showOnHover;
        this.rendererParam = clamp(data.rendererParam, -1, 201);
        this.stackOrder = data.stackOrder;

        if (room != null) WiredVariableFxSupport.ensure(room);
    }

    @Override
    public void onPickUp() {
        this.source = SOURCE_USER;
        this.visibility = VISIBILITY_EVERYONE;
        this.showMode = SHOW_ALWAYS;
        this.showDurationMs = SHOW_DURATION_DEFAULT_MS;
        this.styleId = 0;
        this.colorId = COLOR_NOT_APPLICABLE;
        this.widthId = WIDTH_MEDIUM;
        this.segments = 0;
        this.defaultMin = 0;
        this.defaultMax = DEFAULT_MAX_VALUE;
        this.overrideMinEnabled = false;
        this.overrideMaxEnabled = false;
        this.overrideMinTarget = OVERRIDE_TARGET_HOLDER;
        this.overrideMaxTarget = OVERRIDE_TARGET_HOLDER;
        this.audienceValue = 0;
        this.categoryExtra = 0;
        this.overrideMinItemId = 0;
        this.overrideMaxItemId = 0;
        this.audienceItemId = 0;
        this.icon = "";
        this.updateMask = UPDATE_MASK_DEFAULT;
        this.showOnHover = false;
        this.rendererParam = 0;
        this.stackOrder = 0;
    }

    @Override
    public void onWalk(RoomUnit roomUnit, Room room, Object[] objects) {}

    @Override
    public boolean hasConfiguration() {
        return true;
    }

    private String serializeStringParam() {
        return tokenOf(this.overrideMinItemId)
                + DELIM
                + tokenOf(this.overrideMaxItemId)
                + DELIM
                + tokenOf(this.audienceItemId)
                + DELIM
                + this.icon;
    }

    private static String tokenOf(int itemId) {
        return (itemId > 0) ? CUSTOM_TOKEN_PREFIX + itemId : "";
    }

    private static String token(String[] tokens, int index) {
        return (index < tokens.length && tokens[index] != null) ? tokens[index].trim() : "";
    }

    private static int customItemId(String token) {
        if (token == null || !token.startsWith(CUSTOM_TOKEN_PREFIX)) return 0;
        try {
            return Math.max(0, Integer.parseInt(token.substring(CUSTOM_TOKEN_PREFIX.length())));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String normalizeIcon(String icon) {
        if (icon == null) return "";
        String trimmed = icon.trim();
        if (trimmed.length() > ICON_MAX_LENGTH) trimmed = trimmed.substring(0, ICON_MAX_LENGTH);
        return WiredVariableFxStyles.isKnownIcon(trimmed) ? trimmed : "";
    }

    private static int normalizeColor(int colorId) {
        if (colorId == COLOR_DYNAMIC_LEVELLING || colorId == COLOR_DYNAMIC_TEAM) return colorId;
        return clamp(colorId, COLOR_NOT_APPLICABLE, 1000);
    }

    protected static int param(int[] params, int index, int fallback) {
        return (params != null && index < params.length) ? params[index] : fallback;
    }

    protected static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    static class JsonData {
        int source;
        int visibility;
        int showMode;
        int showDurationMs;
        int styleId;
        int colorId;
        int widthId;
        int segments;
        int defaultMin;
        int defaultMax;
        boolean overrideMinEnabled;
        boolean overrideMaxEnabled;
        int overrideMinTarget;
        int overrideMaxTarget;
        int audienceValue;
        int categoryExtra;
        int overrideMinItemId;
        int overrideMaxItemId;
        int audienceItemId;
        String icon;
        // Official only, set after construction; rows from before them have no update mask.
        Integer updateMask;
        boolean showOnHover;
        int rendererParam;
        int stackOrder;

        JsonData(
                int source,
                int visibility,
                int showMode,
                int showDurationMs,
                int styleId,
                int colorId,
                int widthId,
                int segments,
                int defaultMin,
                int defaultMax,
                boolean overrideMinEnabled,
                boolean overrideMaxEnabled,
                int overrideMinTarget,
                int overrideMaxTarget,
                int audienceValue,
                int categoryExtra,
                int overrideMinItemId,
                int overrideMaxItemId,
                int audienceItemId,
                String icon) {
            this.source = source;
            this.visibility = visibility;
            this.showMode = showMode;
            this.showDurationMs = showDurationMs;
            this.styleId = styleId;
            this.colorId = colorId;
            this.widthId = widthId;
            this.segments = segments;
            this.defaultMin = defaultMin;
            this.defaultMax = defaultMax;
            this.overrideMinEnabled = overrideMinEnabled;
            this.overrideMaxEnabled = overrideMaxEnabled;
            this.overrideMinTarget = overrideMinTarget;
            this.overrideMaxTarget = overrideMaxTarget;
            this.audienceValue = audienceValue;
            this.categoryExtra = categoryExtra;
            this.overrideMinItemId = overrideMinItemId;
            this.overrideMaxItemId = overrideMaxItemId;
            this.audienceItemId = audienceItemId;
            this.icon = icon;
        }
    }
}
