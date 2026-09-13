package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.WiredPlatform;
import com.eu.habbo.core.ConfigurationManager;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredMoveCarryHelper;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Move-furni-as-group effect (furni classname {@code wf_act_move_furni_as_group}). Shifts every
 * selected furni by the same vector: either one tile along a chosen direction, or by an arbitrary
 * X/Y offset, which is what the official box offers. Unlike
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
    /** Shift the group one tile along {@link #direction}. */
    private static final int MODE_DIRECTION = 0;

    /** Shift the group by {@link #offsetX} / {@link #offsetY}, as the official box does. */
    private static final int MODE_OFFSET = 1;

    /** Bound of the official {@code wiredfurni.params.place_furni.offsets.x} / {@code .y} inputs. */
    private static final int MAXIMUM_OFFSET = 64;

    public static final WiredEffectType type = WiredEffectType.MOVE_FURNI_AS_GROUP;

    private final List<HabboItem> items = new ArrayList<>();
    private int direction;
    private int furniSource = WiredSourceUtil.SOURCE_TRIGGER;
    private int mode = MODE_DIRECTION;
    private int offsetX;
    private int offsetY;

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

        if (settings.getIntParams().length < 2) throw new WiredSaveException("invalid data");

        this.direction = ((settings.getIntParams()[0] % 8) + 8) % 8;
        this.furniSource = settings.getIntParams()[1];

        // Older clients save two params; those boxes keep shifting one tile along the direction.
        if (settings.getIntParams().length > 4) {
            this.mode = (settings.getIntParams()[2] == MODE_OFFSET) ? MODE_OFFSET : MODE_DIRECTION;
            this.offsetX = clampOffset(settings.getIntParams()[3]);
            this.offsetY = clampOffset(settings.getIntParams()[4]);
        } else {
            this.mode = MODE_DIRECTION;
            this.offsetX = 0;
            this.offsetY = 0;
        }

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
                HabboItem item = room.getHabboItem(settings.getFurniIds()[i]);
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

        List<HabboItem> effectiveItems =
                new ArrayList<>(WiredSourceUtil.resolveItems(ctx, this.furniSource, this.items));
        effectiveItems.removeIf(item -> item == null || room.getHabboItem(item.getId()) == null);

        if (effectiveItems.isEmpty()) return;

        int dx = this.moveDeltaX();
        int dy = this.moveDeltaY();

        // An offset of zero asks for no movement at all.
        if (dx == 0 && dy == 0) return;

        // Move the leading edge first so members don't collide with un-moved members.
        effectiveItems.sort(Comparator.comparingInt((HabboItem i) -> i.getX() * dx + i.getY() * dy)
                .reversed());

        for (HabboItem item : effectiveItems) {
            RoomTile current = room.getLayout().getTile(item.getX(), item.getY());
            if (current == null) continue;

            RoomTile target = (this.mode == MODE_OFFSET)
                    ? room.getLayout().getTile((short) (item.getX() + dx), (short) (item.getY() + dy))
                    : room.getLayout().getTileInFront(current, this.direction, 1);
            if (target == null || !target.getAllowStack()) continue;

            WiredMoveCarryHelper.moveFurni(room, this, item, target, item.getRotation(), null, false, ctx);
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
                .map(HabboItem::getId)
                .toList();

        return WiredManager.getGson()
                .toJson(new JsonData(
                        this.direction,
                        this.getDelay(),
                        validIds,
                        this.furniSource,
                        this.mode,
                        this.offsetX,
                        this.offsetY));
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        List<HabboItem> snapshot = new ArrayList<>(this.items);
        snapshot.removeIf(item ->
                item == null || item.getRoomId() != this.getRoomId() || room.getHabboItem(item.getId()) == null);

        message.appendBoolean(false);
        message.appendInt(WiredManager.MAXIMUM_FURNI_SELECTION);
        message.appendInt(snapshot.size());
        for (HabboItem item : snapshot) message.appendInt(item.getId());
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString("");
        message.appendInt(5);
        message.appendInt(this.direction);
        message.appendInt(this.furniSource);
        message.appendInt(this.mode);
        message.appendInt(this.offsetX);
        message.appendInt(this.offsetY);
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
        this.mode = (data.mode == MODE_OFFSET) ? MODE_OFFSET : MODE_DIRECTION;
        this.offsetX = clampOffset(data.offsetX);
        this.offsetY = clampOffset(data.offsetY);

        if (data.itemIds != null) {
            for (Integer id : data.itemIds) {
                HabboItem item = room.getHabboItem(id);
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
        this.mode = MODE_DIRECTION;
        this.offsetX = 0;
        this.offsetY = 0;
    }

    @Override
    protected long requiredCooldown() {
        return COOLDOWN_MOVEMENT;
    }

    private static int clampOffset(int value) {
        return Math.max(-MAXIMUM_OFFSET, Math.min(MAXIMUM_OFFSET, value));
    }

    /** The vector every group member is shifted by, whichever mode configured it. */
    private int moveDeltaX() {
        return (this.mode == MODE_OFFSET) ? this.offsetX : directionDeltaX(this.direction);
    }

    private int moveDeltaY() {
        return (this.mode == MODE_OFFSET) ? this.offsetY : directionDeltaY(this.direction);
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
        int mode;
        int offsetX;
        int offsetY;

        public JsonData(
                int direction, int delay, List<Integer> itemIds, int furniSource, int mode, int offsetX, int offsetY) {
            this.direction = direction;
            this.delay = delay;
            this.itemIds = itemIds;
            this.furniSource = furniSource;
            this.mode = mode;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
        }
    }
}
