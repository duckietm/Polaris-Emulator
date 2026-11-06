package polaris.hotel.items.interactions.wired.effects;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import polaris.WiredPlatform;
import polaris.core.ConfigurationManager;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.items.Item;
import polaris.hotel.items.interactions.InteractionWiredEffect;
import polaris.hotel.items.interactions.wired.WiredSettings;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomTile;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.users.UserItem;
import polaris.hotel.wired.WiredEffectType;
import polaris.hotel.wired.core.WiredContext;
import polaris.hotel.wired.core.WiredManager;
import polaris.hotel.wired.core.WiredMoveCarryHelper;
import polaris.hotel.wired.core.WiredSourceUtil;
import polaris.messages.ServerMessage;
import polaris.messages.incoming.wired.WiredSaveException;

/**
 * Our "move group one step" effect (furni classname {@code wf_act_move_group_step}; the official
 * "move as group" is {@link WiredEffectMoveAsGroup}). Shifts the
 * selected furni one tile in a configured direction by the same vector. Unlike
 * {@link WiredEffectMoveFurniTo} (which moves the triggering item toward selected targets), this
 * moves the selected items themselves.
 *
 * <p>Collision safety: members are moved in descending order of their projection along the move
 * direction (the leading edge first), so a member's destination tile is vacated by the member ahead
 * of it before it moves there — this is what lets a tightly-packed group shift together. Behaviour is
 * best-effort: a member whose destination is genuinely blocked (room edge / non-stackable tile) is
 * skipped rather than aborting the whole move. Moves are reversible and no furni is ever removed, so
 * the effect is safe to run on a live room. (An all-or-nothing pre-check was rejected because it would
 * abort packed groups, whose members' destinations are each other's still-occupied tiles.)</p>
 */
public class WiredEffectMoveFurniAsGroup extends InteractionWiredEffect {
    // Ours (wf_act_move_group_step): the official "move as group" is WiredEffectMoveAsGroup.
    public static final WiredEffectType type = WiredEffectType.MOVE_FURNI_GROUP_STEP;

    private final List<UserItem> items = new ArrayList<>();
    private int direction;
    private int furniSource = WiredSourceUtil.SOURCE_TRIGGER;

    public WiredEffectMoveFurniAsGroup(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectMoveFurniAsGroup(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        Room room = this.getRoom();
        if (room == null) return false;

        this.items.clear();

        // Ours: [direction]; the furni source is the official source slot.
        if (settings.getIntParams().length < 1) throw new WiredSaveException("invalid data");

        this.direction = ((settings.getIntParams()[0] % 8) + 8) % 8;
        this.furniSource = settings.furniSource(0, WiredSourceUtil.SOURCE_TRIGGER);

        int count = settings.getFurniIds().length;

        ConfigurationManager config = WiredPlatform.configuration();
        int cap = (config == null) ? Integer.MAX_VALUE : config.getInt("hotel.wired.furni.selection.count");
        if (count > cap) {
            throw new WiredSaveException("Too many furni selected");
        }

        if (count > 0 && this.furniSource == WiredSourceUtil.SOURCE_TRIGGER) {
            this.furniSource = WiredSourceUtil.SOURCE_SELECTED;
        }

        if (this.furniSource == WiredSourceUtil.SOURCE_SELECTED) {
            for (int i = 0; i < count; i++) {
                UserItem item = room.getUserItem(settings.getFurniIds()[i]);
                if (item != null) {
                    this.items.add(item);
                }
            }
        }

        this.setDelay(settings.getDelay());
        return true;
    }

    @Override
    public WiredEffectType getType() {
        return type;
    }

    @Override
    public void execute(WiredContext ctx) {
        if (ctx == null) return;

        Room room = ctx.room();
        if (room == null || room.getLayout() == null) return;

        List<UserItem> effectiveItems =
                new ArrayList<>(WiredSourceUtil.resolveItems(ctx, this.furniSource, this.items));
        effectiveItems.removeIf(item -> item == null || room.getUserItem(item.getId()) == null);

        if (effectiveItems.isEmpty()) return;

        // Habbo lists a group's moves in pick order, though the leading edge moves first.
        List<Integer> pickOrder = effectiveItems.stream().map(UserItem::getId).toList();
        int firstMovement = WiredMoveCarryHelper.collectedMovementCount();

        // Move the leading edge first so members don't collide with un-moved members.
        int dx = directionDeltaX(this.direction);
        int dy = directionDeltaY(this.direction);
        effectiveItems.sort(Comparator.comparingInt((UserItem i) -> i.getX() * dx + i.getY() * dy)
                .reversed());

        for (UserItem item : effectiveItems) {
            RoomTile current = room.getLayout().getTile(item.getX(), item.getY());
            if (current == null) continue;

            RoomTile target = room.getLayout().getTileInFront(current, this.direction, 1);
            if (target == null || !target.getAllowStack()) continue;

            WiredMoveCarryHelper.moveFurni(room, this, item, target, item.getRotation(), null, false, ctx);
        }

        if (firstMovement >= 0) {
            WiredMoveCarryHelper.orderCollectedFurni(firstMovement, pickOrder);
        }
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public String getWiredData() {
        List<Integer> validIds = this.items.stream()
                .filter(item -> item != null && item.getRoomId() == this.getRoomId())
                .map(UserItem::getId)
                .toList();

        return WiredManager.getGson().toJson(new JsonData(this.direction, this.getDelay(), validIds, this.furniSource));
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
        message.appendInt(1);
        message.appendInt(this.direction);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(this.getDelay());
        message.appendInt(0);
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.items.clear();
        String wiredData = set.getString("wired_data");

        if (wiredData == null || !wiredData.startsWith("{")) return;

        JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
        if (data == null) return;

        this.direction = ((data.direction % 8) + 8) % 8;
        this.setDelay(data.delay);
        this.furniSource = data.furniSource;

        if (data.itemIds != null) {
            for (Integer id : data.itemIds) {
                UserItem item = room.getUserItem(id);
                if (item != null) {
                    this.items.add(item);
                }
            }
        }

        if (this.furniSource == WiredSourceUtil.SOURCE_TRIGGER && !this.items.isEmpty()) {
            this.furniSource = WiredSourceUtil.SOURCE_SELECTED;
        }
    }

    @Override
    public void onPickUp() {
        this.setDelay(0);
        this.items.clear();
        this.direction = 0;
        this.furniSource = WiredSourceUtil.SOURCE_TRIGGER;
    }

    @Override
    protected long requiredCooldown() {
        return COOLDOWN_MOVEMENT;
    }

    private static int directionDeltaX(int direction) {
        return switch (direction) {
            case 1, 2, 3 -> 1;
            case 5, 6, 7 -> -1;
            default -> 0;
        };
    }

    private static int directionDeltaY(int direction) {
        return switch (direction) {
            case 3, 4, 5 -> 1;
            case 0, 1, 7 -> -1;
            default -> 0;
        };
    }

    static class JsonData {
        int direction;
        int delay;
        List<Integer> itemIds;
        int furniSource;

        public JsonData(int direction, int delay, List<Integer> itemIds, int furniSource) {
            this.direction = direction;
            this.delay = delay;
            this.itemIds = itemIds;
            this.furniSource = furniSource;
        }
    }
}
