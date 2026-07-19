package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredTrigger;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;
import com.eu.habbo.messages.outgoing.catalog.PurchaseOKComposer;
import com.eu.habbo.messages.outgoing.inventory.AddHabboItemComposer;
import com.eu.habbo.messages.outgoing.inventory.InventoryRefreshComposer;
import com.eu.habbo.messages.outgoing.inventory.RemoveHabboItemComposer;
import com.eu.habbo.threading.runnables.QueryDeleteHabboItems;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Gives or takes a furni (identified by its catalog/base item id) to/from the resolved user(s).
 * Carries four ints — {@code [baseItemId, quantity, giveOrTake, userSource]} — so it needs a dedicated
 * NEW client dialog {@link WiredEffectType#GIVE_OR_TAKE_FURNI} (91) with a matching Nitro
 * {@code WiredActionLayoutCode.GIVE_OR_TAKE_FURNI} component.
 *
 * <p>The GIVE branch mints fresh inventory copies through {@link com.eu.habbo.habbohotel.items.ItemManager#createItem}
 * — the same channel {@code WiredManager.giveReward} and the catalog purchase path use — and announces each via
 * {@link AddHabboItemComposer} / {@link PurchaseOKComposer} / {@link InventoryRefreshComposer}. The TAKE branch removes
 * matching base items from the inventory with {@code ItemsComponent.getAndRemoveHabboItem} (mirroring
 * {@code RequestInventoryItemsDelete}), notifies the client with {@link RemoveHabboItemComposer} and persists the
 * deletions through {@link QueryDeleteHabboItems}. Quantity is bounded and the base item is validated so a malformed
 * packet can never spawn an unknown item nor loop unbounded.</p>
 */
public class WiredEffectGiveOrTakeFurni extends InteractionWiredEffect {
    public static final WiredEffectType type = WiredEffectType.GIVE_OR_TAKE_FURNI;

    public static final int MODE_GIVE = 0;
    public static final int MODE_TAKE = 1;

    private static final int MIN_QUANTITY = 1;
    private static final int MAX_QUANTITY = 100;
    private static final int DEFAULT_QUANTITY = 1;

    private int baseItemId = 0;
    private int quantity = DEFAULT_QUANTITY;
    private int giveOrTake = MODE_GIVE;
    private int userSource = WiredSourceUtil.SOURCE_TRIGGER;

    public WiredEffectGiveOrTakeFurni(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectGiveOrTakeFurni(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void execute(WiredContext ctx) {
        Room room = ctx.room();
        if (room == null) return;

        Item baseItem = Emulator.getGameEnvironment().getItemManager().getItem(this.baseItemId);
        if (baseItem == null) return;

        for (RoomUnit unit : WiredSourceUtil.resolveUsers(ctx, this.userSource)) {
            Habbo habbo = room.getHabbo(unit);
            if (habbo == null || habbo.getClient() == null) continue;

            if (this.giveOrTake == MODE_TAKE) {
                takeFurni(habbo, baseItem);
            } else {
                giveFurni(habbo, baseItem);
            }
        }
    }

    private void giveFurni(Habbo habbo, Item baseItem) {
        boolean changed = false;

        for (int i = 0; i < this.quantity; i++) {
            HabboItem item = Emulator.getGameEnvironment().getItemManager().createItem(habbo.getHabboInfo().getId(), baseItem, 0, 0, "");
            if (item == null) {
                continue;
            }

            habbo.getClient().sendResponse(new AddHabboItemComposer(item));
            habbo.getInventory().getItemsComponent().addItem(item);
            changed = true;
        }

        if (changed) {
            habbo.getClient().sendResponse(new PurchaseOKComposer(null));
            habbo.getClient().sendResponse(new InventoryRefreshComposer());
        }
    }

    private void takeFurni(Habbo habbo, Item baseItem) {
        HashMap<Integer, HabboItem> toRemove = new HashMap<>();

        for (int i = 0; i < this.quantity; i++) {
            HabboItem item = habbo.getInventory().getItemsComponent().getAndRemoveHabboItem(baseItem);
            if (item == null) {
                break;
            }
            toRemove.put(item.getId(), item);
        }

        if (toRemove.isEmpty()) {
            return;
        }

        for (HabboItem object : toRemove.values()) {
            habbo.getClient().sendResponse(new RemoveHabboItemComposer(object.getGiftAdjustedId()));
        }

        habbo.getClient().sendResponse(new InventoryRefreshComposer());
        Emulator.getThreading().runPersistence(new QueryDeleteHabboItems(toRemove.values()));
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
        message.appendInt(4);
        message.appendInt(this.baseItemId);
        message.appendInt(this.quantity);
        message.appendInt(this.giveOrTake);
        message.appendInt(this.userSource);
        message.appendInt(0);
        message.appendInt(type.code);
        message.appendInt(this.getDelay());

        if (this.requiresTriggeringUser()) {
            List<Integer> invalidTriggers = new ArrayList<>();
            for (InteractionWiredTrigger object : room.getRoomSpecialTypes().getTriggers(this.getX(), this.getY())) {
                if (!object.isTriggeredByRoomUnit()) {
                    invalidTriggers.add(object.getBaseItem().getSpriteId());
                }
            }
            message.appendInt(invalidTriggers.size());
            for (Integer i : invalidTriggers) {
                message.appendInt(i);
            }
        } else {
            message.appendInt(0);
        }
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        int[] params = settings.getIntParams();
        if (params.length < 4) {
            throw new WiredSaveException("Invalid data");
        }

        int nextBaseItemId = params[0];
        if (nextBaseItemId <= 0 || Emulator.getGameEnvironment().getItemManager().getItem(nextBaseItemId) == null) {
            throw new WiredSaveException("Invalid furni");
        }

        int delay = settings.getDelay();
        if (delay > Emulator.getConfig().getInt("hotel.wired.max_delay", 20)) {
            throw new WiredSaveException("Delay too long");
        }

        this.baseItemId = nextBaseItemId;
        this.quantity = clampQuantity(params[1]);
        this.giveOrTake = (params[2] == MODE_TAKE) ? MODE_TAKE : MODE_GIVE;
        this.userSource = params[3];
        this.setDelay(delay);

        return true;
    }

    private static int clampQuantity(int value) {
        return Math.max(MIN_QUANTITY, Math.min(MAX_QUANTITY, value));
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(this.baseItemId, this.quantity, this.giveOrTake, this.userSource, this.getDelay()));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        String wiredData = set.getString("wired_data");

        if (wiredData != null && wiredData.startsWith("{")) {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            this.baseItemId = data.baseItemId;
            this.quantity = clampQuantity(data.quantity);
            this.giveOrTake = (data.giveOrTake == MODE_TAKE) ? MODE_TAKE : MODE_GIVE;
            this.userSource = data.userSource;
            this.setDelay(data.delay);
        } else {
            this.baseItemId = 0;
            this.quantity = DEFAULT_QUANTITY;
            this.giveOrTake = MODE_GIVE;
            this.userSource = WiredSourceUtil.SOURCE_TRIGGER;
            this.setDelay(0);
        }
    }

    @Override
    public void onPickUp() {
        this.baseItemId = 0;
        this.quantity = DEFAULT_QUANTITY;
        this.giveOrTake = MODE_GIVE;
        this.userSource = WiredSourceUtil.SOURCE_TRIGGER;
        this.setDelay(0);
    }

    @Override
    public boolean requiresTriggeringUser() {
        return this.userSource == WiredSourceUtil.SOURCE_TRIGGER;
    }

    @Override
    public WiredEffectType getType() {
        return type;
    }

    static class JsonData {
        int baseItemId;
        int quantity;
        int giveOrTake;
        int userSource;
        int delay;

        public JsonData(int baseItemId, int quantity, int giveOrTake, int userSource, int delay) {
            this.baseItemId = baseItemId;
            this.quantity = quantity;
            this.giveOrTake = giveOrTake;
            this.userSource = userSource;
            this.delay = delay;
        }
    }
}
