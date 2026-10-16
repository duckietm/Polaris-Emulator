package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.WiredPlatform;
import com.eu.habbo.core.ConfigurationManager;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.FurnitureType;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWired;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.FurnitureMovementError;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.rooms.WiredVariableDefinitionInfo;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import com.eu.habbo.habbohotel.wired.core.WiredValueVariable;
import com.eu.habbo.habbohotel.wired.protocol.WiredOfficialSources;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertComposer;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The official "place furni" action (AIR code 55, furni {@code wf_act_place_furni}): puts temporary
 * copies of the picked furni in the room. The box keeps a snapshot of the picks taken when it is saved
 * (type, tile, altitude, rotation, state), so the originals need not stay. The copies have no owner and
 * no database row: picking one up removes it, "remove furni" takes it away, and they are gone when the
 * room unloads.
 *
 * <p>Int params {@code [targetIsUser, location, altitude, offsetX, offsetY, offsetAltitude,
 * spawnWithVariable, valueIsVariable, value, valueTarget]}: location 0 where the furni stood when saved,
 * 1 at the custom target (a user, or the furni of the second slot); altitude 0 on top of the target
 * tile, 1 the saved altitude, 2 the custom target's; offsets -64..64 tiles and -8000..8000 hundredths.
 * At a custom location the first furni of the snapshot lands on the target and the rest keep their
 * places relative to it (as turbo-cloud reads the official text).
 *
 * <p>Spawning with a variable gives every copy a furni variable. Its value is the number in the box, or
 * another variable's, read once per firing on the first holder of the value target: furni (the third
 * furni slot), user (the second user slot), global or context. The variables travel in the string
 * param, as our other variable boxes send them: {@code "spawnVariableId\tvalueVariableId"}.
 */
public class WiredEffectPlaceTemporaryFurni extends InteractionWiredEffect implements WiredOfficialSources {
    private static final String PLACEMENT_FAILURE_TYPE = "wired.error";
    private static final String PLACEMENT_FAILURE_IMAGE = "${image.library.url}album1584/WIRD2.png";
    private static final String PLACEMENT_FAILURE_MESSAGE = "${room.error.wired.placement_failure}";
    private static final long PLACEMENT_FAILURE_INTERVAL_MS = 5000L;
    private static final ConcurrentHashMap<Integer, Long> PLACEMENT_FAILURE_SHOWN = new ConcurrentHashMap<>();

    public static final WiredEffectType type = WiredEffectType.PLACE_FURNI;

    static final int LOCATION_SOURCE = 0;
    static final int LOCATION_CUSTOM = 1;
    static final int ALTITUDE_ON_TOP = 0;
    static final int ALTITUDE_SOURCE = 1;
    static final int ALTITUDE_CUSTOM = 2;
    static final int MAX_OFFSET = 64;
    static final int MAX_OFFSET_ALTITUDE = 8000;
    /** Copies one firing may place, over and above the room's furniture ceiling. */
    static final int MAX_SNAPSHOT = 20;

    private static final double HEIGHT_EPSILON = 0.001;

    private List<Snapshot> snapshot = new ArrayList<>();
    private boolean targetIsUser = false;
    private int location = LOCATION_SOURCE;
    private int altitude = ALTITUDE_ON_TOP;
    private int offsetX = 0;
    private int offsetY = 0;
    private int offsetAltitude = 0;
    private boolean spawnWithVariable = false;
    private boolean valueIsVariable = false;
    private int value = 0;
    private int valueTarget = WiredValueVariable.TARGET_FURNI;
    private int spawnVariableId = 0;
    private int valueVariableId = 0;
    private int targetFurniSource = WiredSourceUtil.SOURCE_TRIGGER;
    private int valueFurniSource = WiredSourceUtil.SOURCE_TRIGGER;
    private int targetUserSource = WiredSourceUtil.SOURCE_TRIGGER;
    private int valueUserSource = WiredSourceUtil.SOURCE_TRIGGER;

    public WiredEffectPlaceTemporaryFurni(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectPlaceTemporaryFurni(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        Room room = this.getRoom();
        if (room == null) return false;

        int[] p = settings.getIntParams();
        this.targetIsUser = param(p, 0, 0) == 1;
        this.location = param(p, 1, LOCATION_SOURCE) == LOCATION_CUSTOM ? LOCATION_CUSTOM : LOCATION_SOURCE;
        int altitudeParam = param(p, 2, ALTITUDE_ON_TOP);
        this.altitude = (altitudeParam == ALTITUDE_SOURCE || altitudeParam == ALTITUDE_CUSTOM)
                ? altitudeParam
                : ALTITUDE_ON_TOP;
        this.offsetX = clamp(param(p, 3, 0), MAX_OFFSET);
        this.offsetY = clamp(param(p, 4, 0), MAX_OFFSET);
        this.offsetAltitude = clamp(param(p, 5, 0), MAX_OFFSET_ALTITUDE);
        this.spawnWithVariable = param(p, 6, 0) == 1;
        this.valueIsVariable = param(p, 7, 0) == 1;
        this.value = param(p, 8, 0);
        this.valueTarget = WiredValueVariable.normalizeTarget(param(p, 9, WiredValueVariable.TARGET_FURNI));

        String[] variables = settings.getStringParam() == null
                ? new String[0]
                : settings.getStringParam().split("\t", -1);
        this.spawnVariableId = variables.length > 0 ? WiredValueVariable.parseId(variables[0]) : 0;
        this.valueVariableId = variables.length > 1 ? WiredValueVariable.parseId(variables[1]) : 0;
        if (this.spawnWithVariable) {
            WiredVariableDefinitionInfo spawn = room.getFurniVariableManager().getDefinitionInfo(this.spawnVariableId);
            if (spawn == null || spawn.isReadOnly()) {
                throw new WiredSaveException("wiredfurni.params.variables.validation.invalid_variable");
            }
            if (spawn.hasValue() && this.valueIsVariable && this.valueVariableId <= 0) {
                throw new WiredSaveException("wiredfurni.params.variables.validation.missing_variable");
            }
        }

        int[] furniSources = settings.getFurniSourceTypes();
        int[] userSources = settings.getUserSourceTypes();
        this.targetFurniSource = furniSources.length > 1 ? furniSources[1] : WiredSourceUtil.SOURCE_TRIGGER;
        this.valueFurniSource = furniSources.length > 2 ? furniSources[2] : WiredSourceUtil.SOURCE_TRIGGER;
        this.targetUserSource = userSources.length > 0 ? userSources[0] : WiredSourceUtil.SOURCE_TRIGGER;
        this.valueUserSource = userSources.length > 1 ? userSources[1] : WiredSourceUtil.SOURCE_TRIGGER;

        int[] picks = settings.getFurniIds();
        ConfigurationManager config = WiredPlatform.configuration();
        int cap = (config == null)
                ? MAX_SNAPSHOT
                : Math.min(MAX_SNAPSHOT, config.getInt("hotel.wired.furni.selection.count"));
        if (picks.length > cap) {
            throw new WiredSaveException("Too many furni selected");
        }

        // The snapshot: what each pick is and where it stands now.
        List<Snapshot> next = new ArrayList<>();
        for (int id : picks) {
            HabboItem item = room.getHabboItem(id);
            // Wired boxes are not copied: a copy would save its settings to a row it does not have.
            if (item == null
                    || item instanceof InteractionWired
                    || item.getBaseItem().getType() != FurnitureType.FLOOR) {
                continue;
            }
            next.add(new Snapshot(
                    item.getId(),
                    item.getBaseItem().getId(),
                    item.getX(),
                    item.getY(),
                    item.getZ(),
                    item.getRotation(),
                    item.getExtradata()));
        }
        this.snapshot = next;

        this.setDelay(settings.getDelay());
        return true;
    }

    @Override
    public void execute(WiredContext ctx) {
        if (ctx == null) return;
        Room room = ctx.room();
        if (room == null || room.getLayout() == null || this.snapshot.isEmpty()) return;

        double[] target = null;
        if (this.location == LOCATION_CUSTOM || this.altitude == ALTITUDE_CUSTOM) {
            target = this.customTarget(ctx);
            if (target == null) return;
        }

        int dx = this.offsetX;
        int dy = this.offsetY;
        if (this.location == LOCATION_CUSTOM) {
            dx += (int) target[0] - this.snapshot.get(0).x;
            dy += (int) target[1] - this.snapshot.get(0).y;
        }

        double altitudeOffset = this.offsetAltitude / 100.0;
        Integer spawnValue = this.spawnValue(ctx, room);
        ConfigurationManager config = WiredPlatform.configuration();
        int ceiling = (config == null) ? 2500 : config.getInt("hotel.rooms.max.furniture", 2500);

        boolean failed = false;

        for (Snapshot source : this.snapshot) {
            if (room.getFloorItems().size() >= ceiling) {
                failed = true;
                break;
            }

            Item base = WiredPlatform.gameEnvironment() == null
                    ? null
                    : WiredPlatform.gameEnvironment().getItemManager().getItem(source.baseItemId);
            if (base == null || base.getType() != FurnitureType.FLOOR) continue;

            RoomTile tile = room.getLayout().getTile((short) (source.x + dx), (short) (source.y + dy));
            if (tile == null) continue;

            HabboItem copy =
                    WiredPlatform.gameEnvironment().getItemManager().createTemporaryItem(base, source.extradata);
            if (copy == null) continue;

            copy.markTemporary(this.getUserId());

            if (room.placeFloorFurniAt(copy, tile, source.rotation, null) != FurnitureMovementError.NONE) {
                failed = true;
                continue;
            }

            double z =
                    switch (this.altitude) {
                        case ALTITUDE_SOURCE -> source.z;
                        case ALTITUDE_CUSTOM -> target[2];
                        default -> copy.getZ();
                    };
            z += altitudeOffset;
            // Heights are hundredths of a tile; anything closer than that is the same height.
            if (Math.abs(z - copy.getZ()) >= HEIGHT_EPSILON) {
                room.moveFurniTo(copy, tile, source.rotation, Math.max(0, z), null, true);
            }

            if (this.spawnWithVariable) {
                room.getFurniVariableManager().assignVariable(copy, this.spawnVariableId, spawnValue, true);
            }
        }

        if (failed) {
            notifyPlacementFailure(room);
        }
    }

    /**
     * Habbo shows everybody in the room, visitors too, a "placement failed" notice when Place Furni
     * could not place its copies, at most once per user every 5 seconds. The rest of the stack runs on.
     */
    private static void notifyPlacementFailure(Room room) {
        long now = System.currentTimeMillis();
        Map<String, String> keys = new HashMap<>();
        keys.put("image", PLACEMENT_FAILURE_IMAGE);
        keys.put("message", PLACEMENT_FAILURE_MESSAGE);

        for (Habbo habbo : room.getHabbos()) {
            if (habbo == null || habbo.getClient() == null) continue;

            int habboId = habbo.getHabboInfo().getId();
            Long shown = PLACEMENT_FAILURE_SHOWN.get(habboId);

            if (shown != null && (now - shown) < PLACEMENT_FAILURE_INTERVAL_MS) continue;

            PLACEMENT_FAILURE_SHOWN.put(habboId, now);
            habbo.getClient().sendResponse(new BubbleAlertComposer(PLACEMENT_FAILURE_TYPE, keys));
        }

        // Old entries are only worth a few seconds; drop them now and then.
        if (PLACEMENT_FAILURE_SHOWN.size() > 1000) {
            PLACEMENT_FAILURE_SHOWN.values().removeIf(shown -> (now - shown) >= PLACEMENT_FAILURE_INTERVAL_MS);
        }
    }

    /**
     * The value every copy's variable gets: the box's number, or the value variable on the first holder
     * of the value target that has it (0 when none has). Null for a variable without a value.
     */
    private Integer spawnValue(WiredContext ctx, Room room) {
        if (!this.spawnWithVariable) return null;
        WiredVariableDefinitionInfo spawn = room.getFurniVariableManager().getDefinitionInfo(this.spawnVariableId);
        if (spawn == null || !spawn.hasValue()) return null;
        if (!this.valueIsVariable) return this.value;

        Integer read = WiredValueVariable.read(
                ctx, room, this.valueTarget, this.valueVariableId, this.valueFurniSource, this.valueUserSource);
        return read == null ? 0 : read;
    }

    /** The custom target {x, y, z}: the first target user, or the first furni of the second slot. */
    private double[] customTarget(WiredContext ctx) {
        if (this.targetIsUser) {
            List<RoomUnit> users = WiredSourceUtil.resolveUsers(ctx, this.targetUserSource);
            if (users.isEmpty()) return null;
            RoomUnit unit = users.get(0);
            return new double[] {unit.getX(), unit.getY(), unit.getZ()};
        }
        List<HabboItem> items = WiredSourceUtil.resolveItems(ctx, this.targetFurniSource, Collections.emptyList());
        if (items.isEmpty()) return null;
        HabboItem item = items.get(0);
        return new double[] {item.getX(), item.getY(), item.getZ()};
    }

    @Override
    public int[] officialFurniSources() {
        return new int[] {WiredSourceUtil.SOURCE_SELECTED, this.targetFurniSource, this.valueFurniSource};
    }

    @Override
    public int[] officialUserSources() {
        return new int[] {this.targetUserSource, this.valueUserSource};
    }

    @Override
    public WiredEffectType getType() {
        return type;
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(MAX_SNAPSHOT);
        message.appendInt(this.snapshot.size());
        for (Snapshot source : this.snapshot) message.appendInt(source.itemId);
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString(this.spawnVariableId + "\t" + this.valueVariableId);
        message.appendInt(10);
        message.appendInt(this.targetIsUser ? 1 : 0);
        message.appendInt(this.location);
        message.appendInt(this.altitude);
        message.appendInt(this.offsetX);
        message.appendInt(this.offsetY);
        message.appendInt(this.offsetAltitude);
        message.appendInt(this.spawnWithVariable ? 1 : 0);
        message.appendInt(this.valueIsVariable ? 1 : 0);
        message.appendInt(this.value);
        message.appendInt(this.valueTarget);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(this.getDelay());
        message.appendInt(0);
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(this));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.onPickUp();
        String wiredData = set.getString("wired_data");
        if (wiredData == null || !wiredData.startsWith("{")) return;

        // A box saved by our earlier "spawn by base item id" element under this name has no snapshot
        // and starts from the official defaults.
        JsonData data = WiredMovementPayloadGuard.fromJson(wiredData, JsonData.class);
        if (data == null) return;

        if (data.snapshot != null) {
            for (Snapshot source : data.snapshot) {
                if (source != null && this.snapshot.size() < MAX_SNAPSHOT) this.snapshot.add(source);
            }
        }
        this.targetIsUser = data.targetIsUser;
        this.location = data.location == LOCATION_CUSTOM ? LOCATION_CUSTOM : LOCATION_SOURCE;
        this.altitude = (data.altitude == ALTITUDE_SOURCE || data.altitude == ALTITUDE_CUSTOM)
                ? data.altitude
                : ALTITUDE_ON_TOP;
        this.offsetX = clamp(data.offsetX, MAX_OFFSET);
        this.offsetY = clamp(data.offsetY, MAX_OFFSET);
        this.offsetAltitude = clamp(data.offsetAltitude, MAX_OFFSET_ALTITUDE);
        this.spawnWithVariable = data.spawnWithVariable;
        this.valueIsVariable = data.valueIsVariable;
        this.value = data.value;
        this.valueTarget = WiredValueVariable.normalizeTarget(data.valueTarget);
        this.spawnVariableId = Math.max(0, data.spawnVariableId);
        this.valueVariableId = Math.max(0, data.valueVariableId);
        this.targetFurniSource = data.targetFurniSource;
        this.valueFurniSource = data.valueFurniSource;
        this.targetUserSource = data.targetUserSource;
        this.valueUserSource = data.valueUserSource;
        this.setDelay(data.delay);
    }

    @Override
    public void onPickUp() {
        this.snapshot = new ArrayList<>();
        this.targetIsUser = false;
        this.location = LOCATION_SOURCE;
        this.altitude = ALTITUDE_ON_TOP;
        this.offsetX = 0;
        this.offsetY = 0;
        this.offsetAltitude = 0;
        this.spawnWithVariable = false;
        this.valueIsVariable = false;
        this.value = 0;
        this.valueTarget = WiredValueVariable.TARGET_FURNI;
        this.spawnVariableId = 0;
        this.valueVariableId = 0;
        this.targetFurniSource = WiredSourceUtil.SOURCE_TRIGGER;
        this.valueFurniSource = WiredSourceUtil.SOURCE_TRIGGER;
        this.targetUserSource = WiredSourceUtil.SOURCE_TRIGGER;
        this.valueUserSource = WiredSourceUtil.SOURCE_TRIGGER;
        this.setDelay(0);
    }

    private static int param(int[] params, int index, int fallback) {
        return params != null && params.length > index ? params[index] : fallback;
    }

    private static int clamp(int value, int bound) {
        return Math.max(-bound, Math.min(bound, value));
    }

    /** One picked furni as it was when the box was saved. */
    static class Snapshot {
        int itemId;
        int baseItemId;
        int x;
        int y;
        double z;
        int rotation;
        String extradata;

        Snapshot(int itemId, int baseItemId, int x, int y, double z, int rotation, String extradata) {
            this.itemId = itemId;
            this.baseItemId = baseItemId;
            this.x = x;
            this.y = y;
            this.z = z;
            this.rotation = rotation;
            this.extradata = extradata;
        }
    }

    static class JsonData {
        List<Snapshot> snapshot;
        boolean targetIsUser;
        int location;
        int altitude;
        int offsetX;
        int offsetY;
        int offsetAltitude;
        boolean spawnWithVariable;
        boolean valueIsVariable;
        int value;
        int valueTarget;
        int spawnVariableId;
        int valueVariableId;
        int targetFurniSource;
        int valueFurniSource;
        int targetUserSource;
        int valueUserSource;
        int delay;

        JsonData(WiredEffectPlaceTemporaryFurni box) {
            this.snapshot = box.snapshot;
            this.targetIsUser = box.targetIsUser;
            this.location = box.location;
            this.altitude = box.altitude;
            this.offsetX = box.offsetX;
            this.offsetY = box.offsetY;
            this.offsetAltitude = box.offsetAltitude;
            this.spawnWithVariable = box.spawnWithVariable;
            this.valueIsVariable = box.valueIsVariable;
            this.value = box.value;
            this.valueTarget = box.valueTarget;
            this.spawnVariableId = box.spawnVariableId;
            this.valueVariableId = box.valueVariableId;
            this.targetFurniSource = box.targetFurniSource;
            this.valueFurniSource = box.valueFurniSource;
            this.targetUserSource = box.targetUserSource;
            this.valueUserSource = box.valueUserSource;
            this.delay = box.getDelay();
        }
    }
}
