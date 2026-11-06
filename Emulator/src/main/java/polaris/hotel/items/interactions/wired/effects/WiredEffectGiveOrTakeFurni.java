package polaris.hotel.items.interactions.wired.effects;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import polaris.Emulator;
import polaris.WiredPlatform;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.items.Item;
import polaris.hotel.items.interactions.InteractionWiredEffect;
import polaris.hotel.items.interactions.InteractionWiredTrigger;
import polaris.hotel.items.interactions.wired.WiredInputGuard;
import polaris.hotel.items.interactions.wired.WiredRewardPolicy;
import polaris.hotel.items.interactions.wired.WiredSettings;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.users.User;
import polaris.hotel.users.UserItem;
import polaris.hotel.wired.WiredEffectType;
import polaris.hotel.wired.core.WiredContext;
import polaris.hotel.wired.core.WiredManager;
import polaris.hotel.wired.core.WiredSourceUtil;
import polaris.messages.ServerMessage;
import polaris.messages.incoming.wired.WiredSaveException;
import polaris.messages.outgoing.catalog.PurchaseOKComposer;
import polaris.messages.outgoing.inventory.AddUserItemComposer;
import polaris.messages.outgoing.inventory.InventoryRefreshComposer;
import polaris.messages.outgoing.inventory.RemoveUserItemComposer;
import polaris.threading.runnables.QueryDeleteUserItems;

/**
 * Gives or takes a furni (identified by its catalog/base item id) to/from the resolved user(s).
 * Carries four ints — {@code [baseItemId, quantity, giveOrTake, userSource]} — so it needs a dedicated
 * NEW client dialog {@link WiredEffectType#GIVE_OR_TAKE_FURNI} (91) with a matching Nitro
 * {@code WiredActionLayoutCode.GIVE_OR_TAKE_FURNI} component.
 *
 * <p>The GIVE branch mints fresh inventory copies through {@link polaris.hotel.items.ItemManager#createItem}
 * — the same channel {@code WiredManager.giveReward} and the catalog purchase path use — and announces each via
 * {@link AddUserItemComposer} / {@link PurchaseOKComposer} / {@link InventoryRefreshComposer}. The TAKE branch removes
 * matching base items from the inventory with {@code ItemsComponent.getAndRemoveUserItem} (mirroring
 * {@code RequestInventoryItemsDelete}), notifies the client with {@link RemoveUserItemComposer} and persists the
 * deletions through {@link QueryDeleteUserItems}. Quantity is bounded and the base item is validated so a malformed
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

    public WiredEffectGiveOrTakeFurni(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void execute(WiredContext ctx) {
        Room room = ctx.room();
        // An unconfigured box holds no base item, and must return before reaching for the item
        // manager - the same guard its sibling WiredEffectPlaceFurni has always had.
        if (room == null || this.baseItemId <= 0) return;

        Item baseItem = Emulator.getGameEnvironment().getItemManager().getItem(this.baseItemId);
        if (baseItem == null) return;

        for (RoomUnit unit : WiredSourceUtil.resolveUsers(ctx, this.userSource)) {
            User user = room.getUser(unit);
            if (user == null || user.getClient() == null) continue;

            if (this.giveOrTake == MODE_TAKE) {
                takeFurni(user, baseItem);
            } else {
                giveFurni(user, baseItem);
            }
        }
    }

    private void giveFurni(User user, Item baseItem) {
        boolean changed = false;

        for (int i = 0; i < this.quantity; i++) {
            UserItem item = Emulator.getGameEnvironment()
                    .getItemManager()
                    .createItem(user.getUserInfo().getId(), baseItem, 0, 0, "");
            if (item == null) {
                continue;
            }

            user.getClient().sendResponse(new AddUserItemComposer(item));
            user.getInventory().getItemsComponent().addItem(item);
            changed = true;
        }

        if (changed) {
            user.getClient().sendResponse(new PurchaseOKComposer(null));
            user.getClient().sendResponse(new InventoryRefreshComposer());
        }
    }

    private void takeFurni(User user, Item baseItem) {
        HashMap<Integer, UserItem> toRemove = new HashMap<>();

        for (int i = 0; i < this.quantity; i++) {
            UserItem item = user.getInventory().getItemsComponent().getAndRemoveUserItem(baseItem);
            if (item == null) {
                break;
            }
            toRemove.put(item.getId(), item);
        }

        if (toRemove.isEmpty()) {
            return;
        }

        for (UserItem object : toRemove.values()) {
            user.getClient().sendResponse(new RemoveUserItemComposer(object.getGiftAdjustedId()));
        }

        user.getClient().sendResponse(new InventoryRefreshComposer());
        Emulator.getThreading().runPersistence(new QueryDeleteUserItems(toRemove.values()));
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
        message.appendInt(3);
        message.appendInt(this.baseItemId);
        message.appendInt(this.quantity);
        message.appendInt(this.giveOrTake);
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
        // Value out of nothing: the amount cap bounds one firing, not a room full of them.
        if (!WiredRewardPolicy.canConfigure(gameClient)) {
            return false;
        }

        int[] params = settings.getIntParams();
        if (params.length < 3) {
            throw new WiredSaveException("Invalid data");
        }

        int nextMode = (params[2] == MODE_TAKE) ? MODE_TAKE : MODE_GIVE;

        int nextBaseItemId = params[0];
        if (nextBaseItemId <= 0
                || Emulator.getGameEnvironment().getItemManager().getItem(nextBaseItemId) == null) {
            throw new WiredSaveException("Invalid furni");
        }

        int delay = settings.getDelay();
        int maxDelay = WiredPlatform.configuration() == null
                ? 20
                : WiredPlatform.configuration().getInt("hotel.wired.max_delay", WiredInputGuard.DEFAULT_MAX_DELAY);
        if (delay > maxDelay) {
            throw new WiredSaveException("Delay too long");
        }

        this.baseItemId = nextBaseItemId;
        this.quantity = clampQuantity(params[1]);
        this.giveOrTake = nextMode;
        // Ours: [base item, quantity, give or take]; the user source is the official source slot.
        this.userSource = settings.userSource(0, WiredSourceUtil.SOURCE_TRIGGER);
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
                        this.baseItemId, this.quantity, this.giveOrTake, this.userSource, this.getDelay()));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        String wiredData = set.getString("wired_data");

        // The guard answers null for anything it cannot parse, truncated documents included,
        // so the defaults below cover a corrupt row instead of the load failing on it.
        JsonData data = WiredEffectPayloadGuard.fromJson(wiredData, JsonData.class);
        if (data != null) {
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
