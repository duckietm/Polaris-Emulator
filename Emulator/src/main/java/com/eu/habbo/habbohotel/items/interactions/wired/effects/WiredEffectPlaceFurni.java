package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.FurnitureType;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.FurnitureMovementError;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Spawns ("builds") a fresh floor furni into the room when the stack fires — the in-room analogue of
 * {@link WiredEffectGiveOrTakeFurni} (which mints into a user's inventory). The furni is a freshly minted,
 * persisted item owned by the ROOM OWNER, placed either on this effect furni's own tile or at a stored x/y.
 *
 * <p>Carries six ints — {@code [baseItemId, quantity, placementMode, storedX, storedY, rotation]} — and so needs the
 * matching Nitro {@code WiredActionLayoutCode.PLACE_FURNI} (106) dialog. Each fire is capped (quantity 1..10) and the
 * room's furniture ceiling is honoured so a repeater trigger + place loop cannot flood/OOM the room.</p>
 */
public class WiredEffectPlaceFurni extends InteractionWiredEffect {
    public static final WiredEffectType type = WiredEffectType.PLACE_FURNI;

    public static final int MODE_THIS_TILE = 0;
    public static final int MODE_STORED_XY = 1;

    private static final int MIN_QUANTITY = 1;
    private static final int MAX_QUANTITY = 10;
    private static final int DEFAULT_QUANTITY = 1;

    private int baseItemId = 0;
    private int quantity = DEFAULT_QUANTITY;
    private int placementMode = MODE_THIS_TILE;
    private int storedX = 0;
    private int storedY = 0;
    private int rotation = 0;

    public WiredEffectPlaceFurni(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectPlaceFurni(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void execute(WiredContext ctx) {
        Room room = ctx.room();
        if (room == null || room.getLayout() == null || this.baseItemId <= 0) return;

        Item baseItem = Emulator.getGameEnvironment().getItemManager().getItem(this.baseItemId);
        if (baseItem == null || baseItem.getType() != FurnitureType.FLOOR) return;

        RoomTile tile = (this.placementMode == MODE_STORED_XY)
                ? room.getLayout().getTile((short) this.storedX, (short) this.storedY)
                : room.getLayout().getTile(this.getX(), this.getY());

        if (tile == null) return;

        int ceiling = Emulator.getConfig().getInt("hotel.rooms.max.furniture", 2500);
        Habbo owner = Emulator.getGameEnvironment().getHabboManager().getHabbo(room.getOwnerId());

        for (int i = 0; i < this.quantity; i++) {
            if (room.getFloorItems().size() >= ceiling) break;

            HabboItem item =
                    Emulator.getGameEnvironment().getItemManager().createItem(room.getOwnerId(), baseItem, 0, 0, "");
            if (item == null) continue;

            FurnitureMovementError error = room.placeFloorFurniAt(item, tile, this.rotation, owner);

            if (error != FurnitureMovementError.NONE) {
                // Placement failed (no fit / blocked) — destroy the orphan instead of leaking a roomless item.
                Emulator.getGameEnvironment().getItemManager().deleteItem(item);
            }
        }
    }

    @Deprecated
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
        message.appendString("");
        message.appendInt(6);
        message.appendInt(this.baseItemId);
        message.appendInt(this.quantity);
        message.appendInt(this.placementMode);
        message.appendInt(this.storedX);
        message.appendInt(this.storedY);
        message.appendInt(this.rotation);
        message.appendInt(0);
        message.appendInt(type.code);
        message.appendInt(this.getDelay());
        message.appendInt(0);
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        int[] params = settings.getIntParams();
        if (params.length < 6) {
            throw new WiredSaveException("Invalid data");
        }

        int nextBaseItemId = params[0];
        Item base = Emulator.getGameEnvironment().getItemManager().getItem(nextBaseItemId);
        if (nextBaseItemId <= 0 || base == null || base.getType() != FurnitureType.FLOOR) {
            throw new WiredSaveException("Invalid furni");
        }

        int delay = settings.getDelay();
        if (delay > Emulator.getConfig().getInt("hotel.wired.max_delay", 20)) {
            throw new WiredSaveException("Delay too long");
        }

        this.baseItemId = nextBaseItemId;
        this.quantity = clampQuantity(params[1]);
        this.placementMode = (params[2] == MODE_STORED_XY) ? MODE_STORED_XY : MODE_THIS_TILE;
        this.storedX = Math.max(0, params[3]);
        this.storedY = Math.max(0, params[4]);
        this.rotation = ((params[5] % 8) + 8) % 8;
        this.setDelay(delay);

        return true;
    }

    private static int clampQuantity(int value) {
        return Math.max(MIN_QUANTITY, Math.min(MAX_QUANTITY, value));
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson()
                .toJson(new JsonData(
                        this.baseItemId,
                        this.quantity,
                        this.placementMode,
                        this.storedX,
                        this.storedY,
                        this.rotation,
                        this.getDelay()));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        String wiredData = set.getString("wired_data");

        if (wiredData != null && wiredData.startsWith("{")) {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            this.baseItemId = data.baseItemId;
            this.quantity = clampQuantity(data.quantity);
            this.placementMode = (data.placementMode == MODE_STORED_XY) ? MODE_STORED_XY : MODE_THIS_TILE;
            this.storedX = Math.max(0, data.storedX);
            this.storedY = Math.max(0, data.storedY);
            this.rotation = ((data.rotation % 8) + 8) % 8;
            this.setDelay(data.delay);
        } else {
            this.baseItemId = 0;
            this.quantity = DEFAULT_QUANTITY;
            this.placementMode = MODE_THIS_TILE;
            this.storedX = 0;
            this.storedY = 0;
            this.rotation = 0;
            this.setDelay(0);
        }
    }

    @Override
    public void onPickUp() {
        this.baseItemId = 0;
        this.quantity = DEFAULT_QUANTITY;
        this.placementMode = MODE_THIS_TILE;
        this.storedX = 0;
        this.storedY = 0;
        this.rotation = 0;
        this.setDelay(0);
    }

    @Override
    public WiredEffectType getType() {
        return type;
    }

    static class JsonData {
        int baseItemId;
        int quantity;
        int placementMode;
        int storedX;
        int storedY;
        int rotation;
        int delay;

        public JsonData(
                int baseItemId, int quantity, int placementMode, int storedX, int storedY, int rotation, int delay) {
            this.baseItemId = baseItemId;
            this.quantity = quantity;
            this.placementMode = placementMode;
            this.storedX = storedX;
            this.storedY = storedY;
            this.rotation = rotation;
            this.delay = delay;
        }
    }
}
