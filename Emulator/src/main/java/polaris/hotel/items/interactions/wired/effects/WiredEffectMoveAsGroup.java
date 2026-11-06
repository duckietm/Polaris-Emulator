package polaris.hotel.items.interactions.wired.effects;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import polaris.WiredPlatform;
import polaris.core.ConfigurationManager;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.items.Item;
import polaris.hotel.items.interactions.InteractionWiredEffect;
import polaris.hotel.items.interactions.wired.WiredSettings;
import polaris.hotel.rooms.FurnitureMovementError;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomTile;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.users.UserItem;
import polaris.hotel.wired.WiredEffectType;
import polaris.hotel.wired.core.WiredContext;
import polaris.hotel.wired.core.WiredManager;
import polaris.hotel.wired.core.WiredMoveCarryHelper;
import polaris.hotel.wired.core.WiredSourceUtil;
import polaris.hotel.wired.protocol.WiredOfficialSources;
import polaris.messages.ServerMessage;
import polaris.messages.incoming.wired.WiredSaveException;

/**
 * The official "move as group" action (AIR {@code MoveAsGroup}, code 57, furni
 * {@code wf_act_move_furni_as_group}): moves the furni of the first slot together, keeping their
 * layout, to a target location (a user, or the furni of the second slot) plus an offset of -64..64.
 *
 * <p>Int params {@code [targetIsUser, offsetX, offsetY]}; furni sources {@code [group, target furni]},
 * user sources {@code [target user]}. The group's first furni lands on the target tile plus the offset
 * and every other furni moves by the same distance (as turbo-cloud reads the official usage text).
 * When any of them would leave the room none moves; the furni furthest along moves first, and a
 * blocked one stops the ones behind it.
 */
public class WiredEffectMoveAsGroup extends InteractionWiredEffect implements WiredOfficialSources {
    public static final WiredEffectType type = WiredEffectType.MOVE_FURNI_AS_GROUP;

    static final int MAX_OFFSET = 64;

    private final List<UserItem> items = new ArrayList<>();
    private boolean targetIsUser = true;
    private int offsetX = 0;
    private int offsetY = 0;
    private int furniSource = WiredSourceUtil.SOURCE_SELECTED;
    private int targetFurniSource = WiredSourceUtil.SOURCE_TRIGGER;
    private int userSource = WiredSourceUtil.SOURCE_TRIGGER;

    public WiredEffectMoveAsGroup(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectMoveAsGroup(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        Room room = this.getRoom();
        if (room == null) return false;

        int[] params = settings.getIntParams();
        this.targetIsUser = params.length < 1 || params[0] == 1;
        this.offsetX = params.length > 1 ? clampOffset(params[1]) : 0;
        this.offsetY = params.length > 2 ? clampOffset(params[2]) : 0;

        int[] furniSources = settings.getFurniSourceTypes();
        int[] userSources = settings.getUserSourceTypes();
        this.furniSource = furniSources.length > 0 ? furniSources[0] : WiredSourceUtil.SOURCE_SELECTED;
        this.targetFurniSource = furniSources.length > 1 ? furniSources[1] : WiredSourceUtil.SOURCE_TRIGGER;
        this.userSource = userSources.length > 0 ? userSources[0] : WiredSourceUtil.SOURCE_TRIGGER;

        int count = settings.getFurniIds().length;
        ConfigurationManager config = WiredPlatform.configuration();
        int cap = (config == null) ? Integer.MAX_VALUE : config.getInt("hotel.wired.furni.selection.count");
        if (count > cap) {
            throw new WiredSaveException("Too many furni selected");
        }

        this.items.clear();
        for (int id : settings.getFurniIds()) {
            UserItem item = room.getUserItem(id);
            if (item != null) this.items.add(item);
        }

        this.setDelay(settings.getDelay());
        return true;
    }

    @Override
    public void execute(WiredContext ctx) {
        if (ctx == null) return;

        Room room = ctx.room();
        if (room == null || room.getLayout() == null) return;

        List<UserItem> group = new ArrayList<>(WiredSourceUtil.resolveItems(ctx, this.furniSource, this.items));
        group.removeIf(item -> item == null || room.getUserItem(item.getId()) == null);
        if (group.isEmpty()) return;

        int[] target = this.targetTile(ctx);
        if (target == null) return;

        int dx = target[0] + this.offsetX - group.get(0).getX();
        int dy = target[1] + this.offsetY - group.get(0).getY();
        if (dx == 0 && dy == 0) return;

        // As a group: when any of them would leave the room, none of them moves.
        for (UserItem item : group) {
            if (room.getLayout().getTile((short) (item.getX() + dx), (short) (item.getY() + dy)) == null) return;
        }

        // The furni furthest along the way goes first, so each steps onto a tile the one before it left.
        int sx = Integer.signum(dx);
        int sy = Integer.signum(dy);
        group.sort(Comparator.comparingInt((UserItem i) -> i.getX() * sx + i.getY() * sy)
                .reversed());

        for (UserItem item : group) {
            RoomTile tile = room.getLayout().getTile((short) (item.getX() + dx), (short) (item.getY() + dy));
            FurnitureMovementError error =
                    WiredMoveCarryHelper.moveFurni(room, this, item, tile, item.getRotation(), null, false, ctx);
            // A blocked furni stops the ones behind it too: they would run into it.
            if (error != FurnitureMovementError.NONE) break;
        }
    }

    /** The target tile: the first target user, or the first furni of the second slot. */
    private int[] targetTile(WiredContext ctx) {
        if (this.targetIsUser) {
            List<RoomUnit> users = WiredSourceUtil.resolveUsers(ctx, this.userSource);
            return users.isEmpty()
                    ? null
                    : new int[] {users.get(0).getX(), users.get(0).getY()};
        }
        List<UserItem> targets = WiredSourceUtil.resolveItems(ctx, this.targetFurniSource, Collections.emptyList());
        return targets.isEmpty()
                ? null
                : new int[] {targets.get(0).getX(), targets.get(0).getY()};
    }

    @Override
    public int[] officialFurniSources() {
        return new int[] {this.furniSource, this.targetFurniSource};
    }

    @Override
    public int[] officialUserSources() {
        return new int[] {this.userSource};
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
    public String getWiredData() {
        List<Integer> ids = this.items.stream()
                .filter(item -> item != null && item.getRoomId() == this.getRoomId())
                .map(UserItem::getId)
                .toList();
        return WiredManager.getGson()
                .toJson(new JsonData(
                        this.targetIsUser,
                        this.offsetX,
                        this.offsetY,
                        ids,
                        this.furniSource,
                        this.targetFurniSource,
                        this.userSource,
                        this.getDelay()));
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        List<UserItem> snapshot = new ArrayList<>(this.items);
        snapshot.removeIf(
                item -> item == null || item.getRoomId() != this.getRoomId() || room.getUserItem(item.getId()) == null);

        message.appendBoolean(false);
        message.appendInt(WiredManager.MAXIMUM_FURNI_SELECTION);
        message.appendInt(snapshot.size());
        for (UserItem item : snapshot) message.appendInt(item.getId());
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString("");
        message.appendInt(3);
        message.appendInt(this.targetIsUser ? 1 : 0);
        message.appendInt(this.offsetX);
        message.appendInt(this.offsetY);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(this.getDelay());
        message.appendInt(0);
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.onPickUp();
        String wiredData = set.getString("wired_data");
        if (wiredData == null || !wiredData.startsWith("{")) return;

        // A box saved by the old step-in-a-direction element keeps its picks and furni source; the
        // official settings start from their defaults.
        JsonData data = WiredMovementPayloadGuard.fromJson(wiredData, JsonData.class);
        if (data == null) return;

        this.targetIsUser = data.targetIsUser == null || data.targetIsUser;
        this.offsetX = clampOffset(data.offsetX);
        this.offsetY = clampOffset(data.offsetY);
        if (data.furniSource != null) this.furniSource = data.furniSource;
        if (data.targetFurniSource != null) this.targetFurniSource = data.targetFurniSource;
        if (data.userSource != null) this.userSource = data.userSource;
        this.setDelay(data.delay);

        if (data.itemIds != null && room != null) {
            for (Integer id : data.itemIds) {
                if (id == null) continue;
                UserItem item = room.getUserItem(id);
                if (item != null) this.items.add(item);
            }
        }
    }

    @Override
    public void onPickUp() {
        this.items.clear();
        this.targetIsUser = true;
        this.offsetX = 0;
        this.offsetY = 0;
        this.furniSource = WiredSourceUtil.SOURCE_SELECTED;
        this.targetFurniSource = WiredSourceUtil.SOURCE_TRIGGER;
        this.userSource = WiredSourceUtil.SOURCE_TRIGGER;
        this.setDelay(0);
    }

    @Override
    protected long requiredCooldown() {
        return COOLDOWN_MOVEMENT;
    }

    static int clampOffset(int value) {
        return Math.max(-MAX_OFFSET, Math.min(MAX_OFFSET, value));
    }

    static class JsonData {
        Boolean targetIsUser;
        int offsetX;
        int offsetY;
        List<Integer> itemIds;
        Integer furniSource;
        Integer targetFurniSource;
        Integer userSource;
        int delay;

        JsonData(
                boolean targetIsUser,
                int offsetX,
                int offsetY,
                List<Integer> itemIds,
                int furniSource,
                int targetFurniSource,
                int userSource,
                int delay) {
            this.targetIsUser = targetIsUser;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.itemIds = itemIds;
            this.furniSource = furniSource;
            this.targetFurniSource = targetFurniSource;
            this.userSource = userSource;
            this.delay = delay;
        }
    }
}
