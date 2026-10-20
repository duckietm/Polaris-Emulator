package com.eu.habbo.habbohotel.items.interactions.wired.extra;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.FurnitureDirections;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.rooms.RoomUserRotation;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredProjectileDirections;
import com.eu.habbo.habbohotel.wired.core.WiredProjectileFlight;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import com.eu.habbo.habbohotel.wired.core.WiredUserMovementHelper;
import com.eu.habbo.habbohotel.wired.core.WiredVariableOperand;
import com.eu.habbo.habbohotel.wired.protocol.WiredOfficialVariableIds;
import com.eu.habbo.habbohotel.wired.protocol.WiredVariableIds;
import com.eu.habbo.messages.ServerMessage;
import java.lang.ref.WeakReference;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The projectile add-on, sold as {@code wf_xtra_rotate_to_dir}: it dresses up the moves its stack
 * makes with the furni picked as projectiles (every furni the stack moves when none are picked).
 *
 * <p>The first nineteen int params are the editor's, in turbo-cloud's order; five more hold where
 * the two variables are read and who the shooter is. Variable ids {@code [time variable, distance variable]} (AIR's
 * {@code [timeVar, distanceVar]}), no string; older clients sent the two tokens tab separated as the string (still
 * read).
 *
 * <p>Animation time, when scaled with the distance: the distance is the largest of the axes the
 * box measures (x and y when none is ticked, height in tiles), each tile flown takes the time per
 * tile minus the speed increase for every tile already behind it (never under 1 ms), a part tile
 * its share, and the flight is kept between {@link #MIN_FLIGHT_MS} and {@link #MAX_FLIGHT_MS}.
 */
public class WiredExtraProjectile extends InteractionWiredExtra implements WiredOfficialVariableIds {
    public static final int CODE = 136;

    public static final int PARAM_COUNT = 19;
    public static final int PARAM_ROTATE = 0;
    public static final int PARAM_DIRECTIONAL_SYSTEM = 1;
    public static final int PARAM_SCALE_TIME = 2;
    public static final int PARAM_TIME_IS_VARIABLE = 3;
    public static final int PARAM_TIME_PER_TILE = 4;
    public static final int PARAM_TIME_TARGET = 5;
    public static final int PARAM_DISTANCE_BY_X = 6;
    public static final int PARAM_DISTANCE_BY_Y = 7;
    public static final int PARAM_DISTANCE_BY_HEIGHT = 8;
    public static final int PARAM_SPEED_INCREASE = 9;
    public static final int PARAM_ROTATION_OFFSET = 10;
    public static final int PARAM_VARIABLES_MASK = 11;
    public static final int PARAM_CHANGE_SHOOTER_DIRECTION = 12;
    public static final int PARAM_BUNNY_HOP = 13;
    public static final int PARAM_DISTANCE_MODE = 14;
    public static final int PARAM_DISTANCE_IS_VARIABLE = 15;
    public static final int PARAM_DISTANCE_TILES = 16;
    public static final int PARAM_DISTANCE_TARGET = 17;
    public static final int PARAM_CURVE_STRENGTH = 18;
    public static final int PARAM_TIME_USER_SOURCE = 19;
    public static final int PARAM_TIME_FURNI_SOURCE = 20;
    public static final int PARAM_DISTANCE_USER_SOURCE = 21;
    public static final int PARAM_DISTANCE_FURNI_SOURCE = 22;
    public static final int PARAM_SHOOTER_SOURCE = 23;
    public static final int TOTAL_PARAM_COUNT = 24;

    public static final int DISTANCE_NORMAL = 0;
    public static final int DISTANCE_OVERSHOOT = 1;
    public static final int DISTANCE_FIXED = 2;

    public static final int TIME_PER_TILE_DEFAULT = 500;
    public static final int MIN_FLIGHT_MS = 50;
    public static final int MAX_FLIGHT_MS = 60_000;
    public static final int BUNNY_HOP_STRENGTH = 30;
    public static final int BUNNY_HOP_MS = 300;

    private static final int MIN_TILE_MS = 1;
    private static final int MAX_TIMED_TILES = 256;
    /** 3: the internal variables mask in AIR's bit order (see {@link WiredProjectileFlight#KEYS}). */
    private static final int DATA_VERSION = 3;

    private static final int MASK_IN_AIR_ORDER_VERSION = 3;

    /** The mask's bit order before AIR's: x, y, altitude, is traveling, tiles traveled, furni and user collisions. */
    private static final List<String> LEGACY_MASK_KEYS = List.of(
            WiredProjectileFlight.POSITION_X,
            WiredProjectileFlight.POSITION_Y,
            WiredProjectileFlight.POSITION_ALTITUDE,
            WiredProjectileFlight.IS_TRAVELING,
            WiredProjectileFlight.TILES_TRAVELED,
            WiredProjectileFlight.FURNI_COLLISIONS,
            WiredProjectileFlight.USER_COLLISIONS);

    private static final String TOKEN_SEPARATOR = "\t";

    /** The bounds of the editor's inputs, per param; a value outside them is clamped on save. */
    private static final int[][] PARAM_BOUNDS = {
        {0, 1}, // rotate in moving direction
        {0, 3}, // directional system
        {0, 1}, // scale animation time with distance
        {0, 1}, // time per tile is a variable
        {1, 100_000}, // time per tile (ms)
        {0, 3}, // time per tile variable target
        {0, 1}, // distance by x
        {0, 1}, // distance by y
        {0, 1}, // distance by height
        {0, 100_000}, // speed increase (ms)
        {0, 7}, // rotation offset (eighth turns)
        {0, 127}, // internal variables mask
        {0, 1}, // change the shooter's direction
        {0, 1}, // bunny hop
        {0, 2}, // distance mode: normal, overshoot, fixed
        {0, 1}, // distance tiles is a variable
        {-64, 64}, // distance tiles
        {0, 3}, // distance tiles variable target
        {-1000, 1000} // curve strength
    };

    private final Set<HabboItem> items = new LinkedHashSet<>();
    private int[] params = defaultParams();
    private String timeToken = "";
    private String distanceToken = "";
    private WeakReference<WiredContext> resolvedCtx;
    private Integer resolvedTimePerTile;
    private Integer resolvedDistanceTiles;
    private WeakReference<WiredContext> aimedCtx;

    public WiredExtraProjectile(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredExtraProjectile(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) {
        Room room = settings.getRoom();

        if (room == null || settings.getFurniIds().length > WiredManager.MAXIMUM_FURNI_SELECTION) {
            return false;
        }

        this.params = normalizeParams(settings.getIntParams());
        String[] tokens = WiredVariableIds.present(settings.getVariableIds())
                ? new String[] {
                    WiredVariableIds.tokenAt(settings.getVariableIds(), 0),
                    WiredVariableIds.tokenAt(settings.getVariableIds(), 1)
                }
                : splitTokens(settings.getStringParam());
        this.timeToken = tokens[0];
        this.distanceToken = tokens[1];
        this.items.clear();
        this.forgetResolved();

        for (int itemId : settings.getFurniIds()) {
            HabboItem item = room.getHabboItem(itemId);

            if (item != null) {
                this.items.add(item);
            }
        }

        return true;
    }

    @Override
    public String getWiredData() {
        JsonData data = new JsonData(
                this.params.clone(), this.items.stream().map(HabboItem::getId).collect(Collectors.toList()));
        data.version = DATA_VERSION;
        data.timeToken = this.timeToken;
        data.distanceToken = this.distanceToken;
        return WiredManager.getGson().toJson(data);
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        this.refresh(room);

        message.appendBoolean(false);
        message.appendInt(WiredManager.MAXIMUM_FURNI_SELECTION);
        message.appendInt(this.items.size());

        for (HabboItem item : this.items) {
            message.appendInt(item.getId());
        }

        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString("");
        message.appendInt(TOTAL_PARAM_COUNT);

        for (int param : this.params) {
            message.appendInt(param);
        }

        message.appendInt(0);
        message.appendInt(CODE);
        message.appendInt(0);
        message.appendInt(0);
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.onPickUp();

        String wiredData = set.getString("wired_data");
        if (wiredData == null || !wiredData.startsWith("{")) {
            return;
        }

        JsonData data = WiredExtraPayloadGuard.fromJson(wiredData, JsonData.class);
        if (data == null) {
            return;
        }

        this.params = normalizeParams(data.params);
        if (data.version < MASK_IN_AIR_ORDER_VERSION) {
            this.params[PARAM_VARIABLES_MASK] = fromLegacyMask(this.params[PARAM_VARIABLES_MASK]);
        }
        this.timeToken = WiredVariableOperand.normalizeToken(data.timeToken);
        this.distanceToken = WiredVariableOperand.normalizeToken(data.distanceToken);

        if (data.itemIds != null && room != null) {
            for (Integer itemId : data.itemIds) {
                HabboItem item = (itemId == null) ? null : room.getHabboItem(itemId);

                if (item != null) {
                    this.items.add(item);
                }
            }
        }
    }

    @Override
    public void onPickUp() {
        this.items.clear();
        this.params = defaultParams();
        this.timeToken = "";
        this.distanceToken = "";
        this.forgetResolved();
    }

    @Override
    public void onWalk(RoomUnit roomUnit, Room room, Object[] objects) {}

    @Override
    public boolean hasConfiguration() {
        return true;
    }

    public boolean appliesTo(HabboItem movingItem) {
        if (movingItem == null) {
            return false;
        }

        if (this.items.isEmpty()) {
            return true;
        }

        for (HabboItem item : this.items) {
            if (item.getId() == movingItem.getId()) {
                return true;
            }
        }

        return false;
    }

    /**
     * The rotation a projectile furni lands on. With the furni's directions from its asset, a
     * heading it has is taken as it is and one it lacks lands it on its furnidata defaultdir, else
     * it keeps its rotation (as in Habbo); without them, {@link #resolveRotation(int, int, int, int,
     * int)}.
     */
    public int resolveRotation(HabboItem movingItem, int fromX, int fromY, int toX, int toY, int fallbackRotation) {
        FurnitureDirections directions = FurnitureDirections.of(movingItem);
        if (!directions.isKnown()) {
            return this.resolveRotation(fromX, fromY, toX, toY, fallbackRotation);
        }
        if (this.params[PARAM_ROTATE] == 0) {
            return fallbackRotation;
        }

        int facing = WiredProjectileDirections.resolve(this.params[PARAM_DIRECTIONAL_SYSTEM], toX - fromX, toY - fromY);

        if (facing < 0) {
            return fallbackRotation;
        }

        Integer landed =
                directions.heading(WiredProjectileDirections.rotate(facing, this.params[PARAM_ROTATION_OFFSET]));
        return landed == null ? fallbackRotation : landed;
    }

    public int resolveRotation(int fromX, int fromY, int toX, int toY, int fallbackRotation) {
        if (this.params[PARAM_ROTATE] == 0) {
            return fallbackRotation;
        }

        int facing = WiredProjectileDirections.resolve(this.params[PARAM_DIRECTIONAL_SYSTEM], toX - fromX, toY - fromY);

        if (facing < 0) {
            return fallbackRotation;
        }

        int rotation = WiredProjectileDirections.rotate(facing, this.params[PARAM_ROTATION_OFFSET]);
        // Floor furni take no diagonal heading: an odd facing keeps the furni's own rotation, as in Habbo.
        // Behaviour observed in plus-emulator/PlusEMU 6a768f8d, reimplemented.
        return (rotation % 2 != 0) ? fallbackRotation : rotation;
    }

    /** The animation time of one flight; the stack's own time when the box does not scale it. */
    public int resolveDuration(WiredContext ctx, Room room, int dx, int dy, double dz, int baseDuration) {
        if (this.params[PARAM_SCALE_TIME] == 0) {
            return baseDuration;
        }

        int timePerTile = this.params[PARAM_TIME_PER_TILE];

        if (this.params[PARAM_TIME_IS_VARIABLE] == 1) {
            Integer value = this.resolved(ctx, room).timePerTile();

            if (value != null) {
                timePerTile = clampParam(PARAM_TIME_PER_TILE, value);
            }
        }

        return flightDuration(timePerTile, this.params[PARAM_SPEED_INCREASE], this.measureDistance(dx, dy, dz));
    }

    /**
     * Tiles the animation flies past its target, negative to fall short; 0 flies to the target.
     * A fixed distance is the tiles still missing from it. A distance variable nobody holds
     * leaves the flight as long as the move.
     */
    public int resolveOvershoot(WiredContext ctx, Room room, int dx, int dy) {
        int mode = this.params[PARAM_DISTANCE_MODE];

        if (mode == DISTANCE_NORMAL) {
            return 0;
        }

        int tiles = this.params[PARAM_DISTANCE_TILES];

        if (this.params[PARAM_DISTANCE_IS_VARIABLE] == 1) {
            Integer value = this.resolved(ctx, room).distanceTiles();

            if (value == null) {
                return 0;
            }

            tiles = clampParam(PARAM_DISTANCE_TILES, value);
        }

        int overshoot = (mode == DISTANCE_FIXED) ? tiles - Math.max(Math.abs(dx), Math.abs(dy)) : tiles;
        return clampParam(PARAM_DISTANCE_TILES, overshoot);
    }

    public int getCurveStrength() {
        return this.params[PARAM_CURVE_STRENGTH];
    }

    public int getVariablesMask() {
        return this.params[PARAM_VARIABLES_MASK];
    }

    /**
     * Turns the shooter to face the way the projectile flies, once per firing, with a little hop
     * when the box asks for one and the shooter had to turn.
     */
    public void aimShooter(Room room, WiredContext ctx, int dx, int dy) {
        if (this.params[PARAM_CHANGE_SHOOTER_DIRECTION] == 0 || room == null || ctx == null) {
            return;
        }

        int facing = WiredProjectileDirections.resolve(this.params[PARAM_DIRECTIONAL_SYSTEM], dx, dy);

        if (facing < 0) {
            return;
        }

        synchronized (this) {
            if (this.aimedCtx != null && this.aimedCtx.get() == ctx) {
                return;
            }

            this.aimedCtx = new WeakReference<>(ctx);
        }

        RoomUnit shooter = null;

        for (RoomUnit unit : WiredSourceUtil.resolveUsers(ctx, this.params[PARAM_SHOOTER_SOURCE])) {
            if (unit != null && unit.getRoom() == room) {
                shooter = unit;
                break;
            }
        }

        if (shooter == null) {
            return;
        }

        RoomUserRotation rotation = RoomUserRotation.fromValue(facing);
        boolean turns = shooter.getBodyRotation() != rotation;

        if (turns && this.params[PARAM_BUNNY_HOP] == 1 && !shooter.isWalking()) {
            WiredUserMovementHelper.hopInPlace(room, shooter, rotation, rotation, BUNNY_HOP_STRENGTH, BUNNY_HOP_MS);
            return;
        }

        if (turns || shooter.getHeadRotation() != rotation) {
            WiredUserMovementHelper.updateUserDirection(room, shooter, rotation, rotation);
        }
    }

    public int[] getParams() {
        return this.params.clone();
    }

    @Override
    public String[] officialVariableIds() {
        return WiredVariableIds.list(
                this.params[PARAM_TIME_IS_VARIABLE] == 1
                        ? WiredVariableIds.toWireForHolder(this.params[PARAM_TIME_TARGET], this.timeToken)
                        : "",
                this.params[PARAM_DISTANCE_IS_VARIABLE] == 1
                        ? WiredVariableIds.toWireForHolder(this.params[PARAM_DISTANCE_TARGET], this.distanceToken)
                        : "");
    }

    public String getStringParam() {
        if (this.timeToken.isEmpty() && this.distanceToken.isEmpty()) {
            return "";
        }

        return this.timeToken + TOKEN_SEPARATOR + this.distanceToken;
    }

    public Set<HabboItem> getItems() {
        return this.items;
    }

    /** The largest of the measured axes, in tiles; x and y when the box measures none. */
    double measureDistance(int dx, int dy, double dz) {
        boolean byX = this.params[PARAM_DISTANCE_BY_X] == 1;
        boolean byY = this.params[PARAM_DISTANCE_BY_Y] == 1;
        boolean byHeight = this.params[PARAM_DISTANCE_BY_HEIGHT] == 1;

        if (!byX && !byY && !byHeight) {
            byX = true;
            byY = true;
        }

        double distance = 0;
        if (byX) distance = Math.max(distance, Math.abs(dx));
        if (byY) distance = Math.max(distance, Math.abs(dy));
        if (byHeight) distance = Math.max(distance, Math.abs(dz));
        return distance;
    }

    static int flightDuration(int timePerTile, int speedIncrease, double distance) {
        double tiles = Math.max(0d, Math.min(MAX_TIMED_TILES, distance));
        int whole = (int) Math.floor(tiles);
        double total = 0;

        for (int tile = 0; tile < whole; tile++) {
            total += tileTime(timePerTile, speedIncrease, tile);
        }

        total += (tiles - whole) * tileTime(timePerTile, speedIncrease, whole);

        return (int) Math.max(MIN_FLIGHT_MS, Math.min(MAX_FLIGHT_MS, Math.round(total)));
    }

    private static long tileTime(int timePerTile, int speedIncrease, int tile) {
        return Math.max(MIN_TILE_MS, timePerTile - ((long) speedIncrease * tile));
    }

    private record Resolved(Integer timePerTile, Integer distanceTiles) {}

    /** The two variables, read once per firing rather than once per projectile. */
    private Resolved resolved(WiredContext ctx, Room room) {
        if (ctx == null) {
            return new Resolved(null, null);
        }

        synchronized (this) {
            if (this.resolvedCtx != null && this.resolvedCtx.get() == ctx) {
                return new Resolved(this.resolvedTimePerTile, this.resolvedDistanceTiles);
            }
        }

        Room source = (room != null) ? room : ctx.room();
        Integer time = (this.params[PARAM_TIME_IS_VARIABLE] == 1)
                ? WiredVariableOperand.read(
                        ctx,
                        source,
                        this.params[PARAM_TIME_TARGET],
                        this.timeToken,
                        this.params[PARAM_TIME_USER_SOURCE],
                        this.params[PARAM_TIME_FURNI_SOURCE],
                        this.items)
                : null;
        Integer distance = (this.params[PARAM_DISTANCE_IS_VARIABLE] == 1)
                ? WiredVariableOperand.read(
                        ctx,
                        source,
                        this.params[PARAM_DISTANCE_TARGET],
                        this.distanceToken,
                        this.params[PARAM_DISTANCE_USER_SOURCE],
                        this.params[PARAM_DISTANCE_FURNI_SOURCE],
                        this.items)
                : null;

        synchronized (this) {
            this.resolvedCtx = new WeakReference<>(ctx);
            this.resolvedTimePerTile = time;
            this.resolvedDistanceTiles = distance;
        }

        return new Resolved(time, distance);
    }

    private synchronized void forgetResolved() {
        this.resolvedCtx = null;
        this.resolvedTimePerTile = null;
        this.resolvedDistanceTiles = null;
        this.aimedCtx = null;
    }

    private void refresh(Room room) {
        if (room == null) {
            return;
        }

        this.items.removeIf(item -> room.getHabboItem(item.getId()) == null);
    }

    private static int[] defaultParams() {
        int[] defaults = new int[TOTAL_PARAM_COUNT];
        defaults[PARAM_ROTATE] = 1;
        defaults[PARAM_TIME_PER_TILE] = TIME_PER_TILE_DEFAULT;
        return defaults;
    }

    private static int clampParam(int index, int value) {
        return Math.max(PARAM_BOUNDS[index][0], Math.min(PARAM_BOUNDS[index][1], value));
    }

    static int[] normalizeParams(int[] raw) {
        int[] normalized = defaultParams();

        if (raw == null) {
            return normalized;
        }

        for (int index = 0; index < PARAM_COUNT && index < raw.length; index++) {
            normalized[index] = clampParam(index, raw[index]);
        }

        // Before the time was applied the editor wrote 0 here, which no longer means anything.
        if (raw.length > PARAM_TIME_PER_TILE && raw[PARAM_TIME_PER_TILE] <= 0) {
            normalized[PARAM_TIME_PER_TILE] = TIME_PER_TILE_DEFAULT;
        }

        normalized[PARAM_TIME_TARGET] = WiredVariableOperand.normalizeTarget(normalized[PARAM_TIME_TARGET]);
        normalized[PARAM_DISTANCE_TARGET] = WiredVariableOperand.normalizeTarget(normalized[PARAM_DISTANCE_TARGET]);

        if (raw.length > PARAM_TIME_USER_SOURCE) {
            normalized[PARAM_TIME_USER_SOURCE] = WiredVariableOperand.normalizeUserSource(raw[PARAM_TIME_USER_SOURCE]);
        }
        if (raw.length > PARAM_TIME_FURNI_SOURCE) {
            normalized[PARAM_TIME_FURNI_SOURCE] =
                    WiredVariableOperand.normalizeFurniSource(raw[PARAM_TIME_FURNI_SOURCE]);
        }
        if (raw.length > PARAM_DISTANCE_USER_SOURCE) {
            normalized[PARAM_DISTANCE_USER_SOURCE] =
                    WiredVariableOperand.normalizeUserSource(raw[PARAM_DISTANCE_USER_SOURCE]);
        }
        if (raw.length > PARAM_DISTANCE_FURNI_SOURCE) {
            normalized[PARAM_DISTANCE_FURNI_SOURCE] =
                    WiredVariableOperand.normalizeFurniSource(raw[PARAM_DISTANCE_FURNI_SOURCE]);
        }
        if (raw.length > PARAM_SHOOTER_SOURCE) {
            normalized[PARAM_SHOOTER_SOURCE] = WiredVariableOperand.normalizeUserSource(raw[PARAM_SHOOTER_SOURCE]);
        }

        return normalized;
    }

    /** A mask a box stored in the bit order before AIR's, in AIR's order. */
    static int fromLegacyMask(int legacyMask) {
        int mask = 0;
        for (int bit = 0; bit < LEGACY_MASK_KEYS.size(); bit++) {
            if ((legacyMask & (1 << bit)) != 0) {
                mask |= 1 << WiredProjectileFlight.KEYS.indexOf(LEGACY_MASK_KEYS.get(bit));
            }
        }
        return mask;
    }

    static String[] splitTokens(String raw) {
        if (raw == null || raw.isEmpty()) {
            return new String[] {"", ""};
        }

        String[] parts = raw.split(TOKEN_SEPARATOR, -1);
        return new String[] {
            WiredVariableOperand.normalizeToken(parts[0]),
            WiredVariableOperand.normalizeToken((parts.length > 1) ? parts[1] : "")
        };
    }

    static final class JsonData {
        int version;
        int[] params;
        List<Integer> itemIds;
        String timeToken;
        String distanceToken;

        JsonData(int[] params, List<Integer> itemIds) {
            this.params = params;
            this.itemIds = itemIds;
        }
    }
}
