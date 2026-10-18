package com.eu.habbo.habbohotel.items.interactions.wired.extra;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import com.eu.habbo.habbohotel.wired.core.WiredVariableOperand;
import com.eu.habbo.habbohotel.wired.protocol.WiredOfficialSources;
import com.eu.habbo.habbohotel.wired.protocol.WiredVariableTargets;
import com.eu.habbo.messages.ServerMessage;
import java.lang.ref.WeakReference;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Movement-curve add-on (furni classname {@code wf_xtra_mov_curve}). A configuration-holder
 * add-on in the same family as {@link WiredExtraAnimationTime} / {@link WiredExtraMovePhysics}:
 * it stores the easing curve applied to wired furni movement and exposes it via
 * {@link #getCurveType()} for the movement subsystem to read.
 *
 * <p>Habbo sells this furni as "jump strength": the furni its stack moves hop to their tile in an
 * arc. That is {@link #CURVE_JUMP}, with a signed strength (-1000 to 1000, 100 = one tile high,
 * negative dips); the easing curves are ours on top.
 *
 * <p>The strength is typed or read from a variable when the furni moves, as Habbo's editor offers.
 *
 * <p>Int params, official first: {@code [strength from variable (0/1), strength, variable target
 * (official ids)]}, then ours: {@code [curve, intensity (0-100, easing)]}. String param: the
 * variable token. Sources: the variable's user and furni source in the official slots. Picked furni:
 * the furni the variable is read from when its source is the box's own picks.
 */
public class WiredExtraMovementCurve extends InteractionWiredExtra implements WiredOfficialSources {
    public static final int CODE = 97;

    public static final int CURVE_LINEAR = 0;
    public static final int CURVE_EASE_IN = 1;
    public static final int CURVE_EASE_OUT = 2;
    public static final int CURVE_EASE_IN_OUT = 3;
    public static final int CURVE_BOUNCE = 4;
    public static final int CURVE_ELASTIC = 5;
    public static final int CURVE_DROP = 6;
    public static final int CURVE_JUMP = 7;
    private static final int CURVE_MIN = CURVE_LINEAR;
    private static final int CURVE_MAX = CURVE_JUMP;
    public static final int INTENSITY_MIN = 0;
    public static final int INTENSITY_MAX = 100;
    public static final int INTENSITY_DEFAULT = 100;
    public static final int STRENGTH_MIN = -1000;
    public static final int STRENGTH_MAX = 1000;
    /** Habbo's default jump strength. */
    public static final int STRENGTH_DEFAULT = 80;

    public static final int STRENGTH_VALUE = 0;
    public static final int STRENGTH_VARIABLE = 1;

    private int curveType = CURVE_JUMP;
    private int intensity = INTENSITY_DEFAULT;
    private int strength = STRENGTH_DEFAULT;
    private int strengthMode = STRENGTH_VALUE;
    private int variableTarget = WiredVariableOperand.TARGET_USER;
    private int variableUserSource = WiredSourceUtil.SOURCE_TRIGGER;
    private int variableFurniSource = WiredSourceUtil.SOURCE_TRIGGER;
    private String variableToken = "";
    private final List<HabboItem> variableFurni = new ArrayList<>();
    private WeakReference<WiredContext> cachedCtx;
    private int cachedStrength;

    public WiredExtraMovementCurve(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredExtraMovementCurve(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) {
        // Official layout: [strength from a variable, strength, variable target]; ours follow:
        // [curve, intensity]. The variable token is the text, its sources the official slots.
        int[] params = settings.getIntParams();
        this.strengthMode = (params.length > 0 && params[0] == 1) ? STRENGTH_VARIABLE : STRENGTH_VALUE;
        this.strength = normalizeStrength((params.length > 1) ? params[1] : STRENGTH_DEFAULT);
        this.variableTarget = WiredVariableOperand.normalizeTarget(WiredVariableTargets.fromOfficial(
                (params.length > 2) ? params[2] : WiredVariableTargets.OFFICIAL_USER));
        this.curveType = normalizeCurve((params.length > 3) ? params[3] : CURVE_JUMP);
        this.intensity = normalizeIntensity((params.length > 4) ? params[4] : INTENSITY_DEFAULT);
        int[] users = settings.getUserSourceTypes();
        int[] furni = settings.getFurniSourceTypes();
        this.variableUserSource =
                WiredVariableOperand.normalizeUserSource(users.length > 0 ? users[0] : WiredSourceUtil.SOURCE_TRIGGER);
        this.variableFurniSource = WiredVariableOperand.normalizeFurniSource(
                fromOfficialPicks(furni.length > 0 ? furni[0] : WiredSourceUtil.SOURCE_TRIGGER));
        this.variableToken = WiredVariableOperand.normalizeToken(settings.getStringParam());

        this.variableFurni.clear();
        Room room = settings.getRoom();
        if (room != null && settings.getFurniIds() != null) {
            for (int furniId : settings.getFurniIds()) {
                if (this.variableFurni.size() >= WiredManager.MAXIMUM_FURNI_SELECTION) break;
                HabboItem item = room.getHabboItem(furniId);
                if (item != null) this.variableFurni.add(item);
            }
        }
        return true;
    }

    @Override
    public String getWiredData() {
        JsonData data = new JsonData(this.curveType, this.intensity, this.strength);
        data.strengthMode = this.strengthMode;
        data.variableTarget = this.variableTarget;
        data.variableUserSource = this.variableUserSource;
        data.variableFurniSource = this.variableFurniSource;
        data.variableToken = this.variableToken;
        data.variableFurniIds =
                this.variableFurni.stream().map(HabboItem::getId).toList();
        return WiredManager.getGson().toJson(data);
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        if (room != null) {
            this.variableFurni.removeIf(item -> room.getHabboItem(item.getId()) == null);
        }

        message.appendBoolean(false);
        message.appendInt(WiredManager.MAXIMUM_FURNI_SELECTION);
        message.appendInt(this.variableFurni.size());
        for (HabboItem item : this.variableFurni) {
            message.appendInt(item.getId());
        }
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString(this.variableToken);
        message.appendInt(5);
        message.appendInt(this.strengthMode);
        message.appendInt(this.strength);
        message.appendInt(WiredVariableTargets.toOfficial(this.variableTarget));
        message.appendInt(this.curveType);
        message.appendInt(this.intensity);
        message.appendInt(0);
        message.appendInt(CODE);
        message.appendInt(0);
        message.appendInt(0);
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.onPickUp();

        String wiredData = set.getString("wired_data");
        if (wiredData == null || wiredData.isEmpty()) {
            return;
        }

        if (wiredData.startsWith("{")) {
            JsonData data = WiredExtraPayloadGuard.fromJson(wiredData, JsonData.class);
            this.curveType = normalizeCurve((data != null) ? data.curveType : CURVE_LINEAR);
            this.intensity =
                    normalizeIntensity((data != null && data.intensity != null) ? data.intensity : INTENSITY_DEFAULT);
            this.strength =
                    normalizeStrength((data != null && data.strength != null) ? data.strength : STRENGTH_DEFAULT);
            if (data != null) {
                this.strengthMode = (data.strengthMode == STRENGTH_VARIABLE) ? STRENGTH_VARIABLE : STRENGTH_VALUE;
                this.variableTarget = WiredVariableOperand.normalizeTarget(data.variableTarget);
                this.variableUserSource = WiredVariableOperand.normalizeUserSource(data.variableUserSource);
                this.variableFurniSource = WiredVariableOperand.normalizeFurniSource(data.variableFurniSource);
                this.variableToken = WiredVariableOperand.normalizeToken(data.variableToken);
                if (room != null && data.variableFurniIds != null) {
                    for (Integer itemId : data.variableFurniIds) {
                        HabboItem item = (itemId != null) ? room.getHabboItem(itemId) : null;
                        if (item != null) this.variableFurni.add(item);
                    }
                }
            }
            return;
        }

        try {
            this.curveType = normalizeCurve(Integer.parseInt(wiredData));
        } catch (NumberFormatException ignored) {
            this.curveType = CURVE_LINEAR;
        }
    }

    @Override
    public void onPickUp() {
        this.curveType = CURVE_JUMP;
        this.intensity = INTENSITY_DEFAULT;
        this.strength = STRENGTH_DEFAULT;
        this.strengthMode = STRENGTH_VALUE;
        this.variableTarget = WiredVariableOperand.TARGET_USER;
        this.variableUserSource = WiredSourceUtil.SOURCE_TRIGGER;
        this.variableFurniSource = WiredSourceUtil.SOURCE_TRIGGER;
        this.variableToken = "";
        this.variableFurni.clear();
    }

    @Override
    public void onWalk(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {}

    @Override
    public boolean hasConfiguration() {
        return true;
    }

    @Override
    public int[] officialFurniSources() {
        return new int[] {this.variableFurniSource};
    }

    @Override
    public int[] officialUserSources() {
        return new int[] {this.variableUserSource};
    }

    public int getCurveType() {
        return this.curveType;
    }

    public int getIntensity() {
        return this.intensity;
    }

    public int getStrength() {
        return this.strength;
    }

    /** What the move-style packet carries as its intensity: the strength for a jump. */
    public int getStyleIntensity() {
        return this.getStyleIntensity(null);
    }

    /**
     * The same for one move: a strength read from a variable comes from whoever holds it in this
     * firing; nobody holding it leaves the typed strength.
     */
    public int getStyleIntensity(WiredContext ctx) {
        if (this.curveType != CURVE_JUMP) {
            return this.intensity;
        }

        if (this.strengthMode == STRENGTH_VARIABLE && ctx != null) {
            // Read once per firing, not once per moved furni.
            synchronized (this) {
                if (this.cachedCtx != null && this.cachedCtx.get() == ctx) {
                    return this.cachedStrength;
                }
            }

            Integer value = WiredVariableOperand.read(
                    ctx,
                    ctx.room(),
                    this.variableTarget,
                    this.variableToken,
                    this.variableUserSource,
                    this.variableFurniSource,
                    this.variableFurni);
            int resolved = (value != null) ? normalizeStrength(value) : this.strength;

            synchronized (this) {
                this.cachedCtx = new WeakReference<>(ctx);
                this.cachedStrength = resolved;
            }

            return resolved;
        }

        return this.strength;
    }

    private static int normalizeCurve(int value) {
        return Math.max(CURVE_MIN, Math.min(CURVE_MAX, value));
    }

    private static int normalizeIntensity(int value) {
        return Math.max(INTENSITY_MIN, Math.min(INTENSITY_MAX, value));
    }

    private static int normalizeStrength(int value) {
        return Math.max(STRENGTH_MIN, Math.min(STRENGTH_MAX, value));
    }

    static class JsonData {
        int curveType;
        // Wrapper so payloads saved before the intensity field existed load as "default", not 0.
        Integer intensity;
        Integer strength;
        int strengthMode;
        int variableTarget;
        int variableUserSource;
        int variableFurniSource;
        String variableToken;
        List<Integer> variableFurniIds;

        JsonData(int curveType, int intensity, int strength) {
            this.curveType = curveType;
            this.intensity = intensity;
            this.strength = strength;
        }
    }

    /** The official editor names this box's picks 100; the box keeps them as its secondary picks (101). */
    private static int fromOfficialPicks(int source) {
        return source == WiredSourceUtil.SOURCE_SELECTED ? 101 : source;
    }
}
