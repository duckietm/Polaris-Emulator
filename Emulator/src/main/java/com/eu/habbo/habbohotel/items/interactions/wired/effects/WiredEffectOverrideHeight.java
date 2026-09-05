package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.Emulator;
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
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * Puts the selected furni at a height you type, or gives them their stacking height back.
 *
 * <p>The fork could already match a furni's altitude to a snapshot ({@code wf_act_match_to_sshot_height});
 * what it had no way to say was "sit at 2.400". This is the official OVERRIDE_HEIGHT, whose dialog is a
 * two-way choice - use the height, or release it - and a slider over the same 0..8000 thousandths the
 * official uses, so 8000 reads as 8.000 tiles.
 *
 * <p>Movement goes through {@link WiredMoveCarryHelper}, the same path the match-furni effect uses, so
 * riders and anything stacked on top travel with the furni instead of being left hanging.
 */
public class WiredEffectOverrideHeight extends InteractionWiredEffect {
    public static final WiredEffectType type = WiredEffectType.OVERRIDE_HEIGHT;

    /** Official slider bounds: thousandths of a tile, 0.000 to 8.000. */
    public static final int MIN_HEIGHT = 0;

    public static final int MAX_HEIGHT = 8000;

    private static final int MODE_SET = 0;
    private static final int MODE_RELEASE = 1;
    private static final double THOUSANDTHS = 1000.0D;

    private final HashSet<HabboItem> items;
    private int height = 0;
    private int mode = MODE_SET;
    private int furniSource = WiredSourceUtil.SOURCE_TRIGGER;

    public WiredEffectOverrideHeight(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
        this.items = new HashSet<>();
    }

    public WiredEffectOverrideHeight(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
        this.items = new HashSet<>();
    }

    @Override
    public void execute(WiredContext ctx) {
        Room room = ctx.room();
        if (room == null || room.getLayout() == null) {
            return;
        }

        this.refresh(room);

        for (HabboItem item : WiredSourceUtil.resolveItems(ctx, this.furniSource, this.items)) {
            if (item == null) {
                continue;
            }

            RoomTile tile = room.getLayout().getTile(item.getX(), item.getY());
            if (tile == null) {
                continue;
            }

            double targetZ = (this.mode == MODE_RELEASE) ? tile.getStackHeight() : this.height / THOUSANDTHS;
            if (BigDecimal.valueOf(item.getZ()).compareTo(BigDecimal.valueOf(targetZ)) == 0) {
                continue;
            }

            WiredMoveCarryHelper.moveFurni(room, this, item, tile, item.getRotation(), targetZ, null, true, ctx);
        }
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) {
        int[] params = settings.getIntParams();
        // Official slot order: the height first, then the two-way choice.
        this.height = normalizeHeight((params.length > 0) ? params[0] : 0);
        this.mode = ((params.length > 1) && params[1] == MODE_RELEASE) ? MODE_RELEASE : MODE_SET;
        this.furniSource = normalizeFurniSource((params.length > 2) ? params[2] : WiredSourceUtil.SOURCE_TRIGGER);

        int count = settings.getFurniIds().length;
        if (count > Emulator.getConfig().getInt("hotel.wired.furni.selection.count")) {
            return false;
        }

        if (count > 0 && this.furniSource == WiredSourceUtil.SOURCE_TRIGGER) {
            this.furniSource = WiredSourceUtil.SOURCE_SELECTED;
        }

        this.items.clear();

        if (this.furniSource == WiredSourceUtil.SOURCE_SELECTED) {
            Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());
            if (room == null) {
                return false;
            }

            for (int itemId : settings.getFurniIds()) {
                HabboItem item = room.getHabboItem(itemId);
                if (item != null) {
                    this.items.add(item);
                }
            }
        }

        this.setDelay(settings.getDelay());
        return true;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson()
                .toJson(new JsonData(
                        this.height,
                        this.mode,
                        this.furniSource,
                        this.getDelay(),
                        this.items.stream().map(HabboItem::getId).toList()));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.resetSettings();

        JsonData data = WiredEffectPayloadGuard.fromJson(set.getString("wired_data"), JsonData.class);
        if (data == null) {
            return;
        }

        this.height = normalizeHeight(data.height);
        this.mode = (data.mode == MODE_RELEASE) ? MODE_RELEASE : MODE_SET;
        this.furniSource = normalizeFurniSource(data.furniSource);
        this.setDelay(WiredEffectPayloadGuard.delay(data.delay));

        if (data.itemIds == null) {
            return;
        }

        for (Integer id : data.itemIds) {
            if (id == null) {
                continue;
            }

            HabboItem item = room.getHabboItem(id);
            if (item != null) {
                this.items.add(item);
            }
        }
    }

    @Override
    public void onPickUp() {
        this.resetSettings();
    }

    @Override
    public WiredEffectType getType() {
        return type;
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
        message.appendInt(3);
        message.appendInt(this.height);
        message.appendInt(this.mode);
        message.appendInt(this.furniSource);
        message.appendInt(0);
        message.appendInt(type.code);
        message.appendInt(this.getDelay());
        message.appendInt(0);
    }

    @Override
    public boolean requiresTriggeringUser() {
        return false;
    }

    private void resetSettings() {
        this.items.clear();
        this.height = 0;
        this.mode = MODE_SET;
        this.furniSource = WiredSourceUtil.SOURCE_TRIGGER;
        this.setDelay(0);
    }

    private void refresh(Room room) {
        List<HabboItem> gone = new ArrayList<>();

        for (HabboItem item : this.items) {
            if (room.getHabboItem(item.getId()) == null) {
                gone.add(item);
            }
        }

        this.items.removeAll(gone);
    }

    static int normalizeHeight(int value) {
        return Math.max(MIN_HEIGHT, Math.min(MAX_HEIGHT, value));
    }

    private static int normalizeFurniSource(int value) {
        return switch (value) {
            case WiredSourceUtil.SOURCE_SELECTED,
                    WiredSourceUtil.SOURCE_SELECTOR,
                    WiredSourceUtil.SOURCE_SIGNAL,
                    WiredSourceUtil.SOURCE_TRIGGER -> value;
            default -> WiredSourceUtil.SOURCE_TRIGGER;
        };
    }

    static class JsonData {
        int height;
        int mode;
        int furniSource;
        int delay;
        List<Integer> itemIds;

        public JsonData(int height, int mode, int furniSource, int delay, List<Integer> itemIds) {
            this.height = height;
            this.mode = mode;
            this.furniSource = furniSource;
            this.delay = delay;
            this.itemIds = itemIds;
        }
    }
}
