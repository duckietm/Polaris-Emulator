package com.eu.habbo.habbohotel.items.interactions.wired.chest;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.messages.ServerMessage;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Credit Chest (furni classnames {@code wf_storage_coins1} / {@code wf_storage_coins2}). Holds a single
 * currency pool, configured via its dialog (currency type + amount) and dispensed by
 * {@code WiredEffectGiveCurrencyFromChest}. Currency type convention: {@code -1} = credits
 * ({@link com.eu.habbo.habbohotel.users.Habbo#giveCredits}); {@code >= 0} = a points type
 * ({@code Habbo.givePoints(type, amount)} — e.g. 0 duckets, 5 diamonds).
 */
public class InteractionWiredChestCurrency extends InteractionWiredChest {
    /** Client WiredActionLayoutCode value for the chest dialog. */
    public static final int CODE = 100;

    /**
     * The coin chest's sprite states are one axis, not two: zero is the closed chest, and one to four
     * are the open chest with progressively more gold in it. Being open and being full are not
     * separate questions for this furni.
     */
    private static final int CLOSED = 0;

    private static final int OPEN_EMPTY = 1;
    private static final int OPEN_FULLEST = 4;

    public static final int CURRENCY_CREDITS = -1;

    public InteractionWiredChestCurrency(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public InteractionWiredChestCurrency(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    /**
     * Player-first: clicking the chest opens the player Scrigno UI (balance + deposit/withdraw) instead
     * of the wired-config dialog. The contents stay a {@link ChestStorage} so contracts / give-from-chest
     * keep working. Anyone can open; withdraw is gated to room rights server-side (see ChestWithdrawEvent).
     */
    @Override
    public void onClick(GameClient client, Room room, Object[] objects) throws Exception {
        if (client == null || room == null) return;
        // "Tutti possono aprire" toggle: anyone if accessOpen, otherwise room rights only.
        if (!this.contents.isAccessOpen() && !room.hasRights(client.getHabbo())) return;
        ChestOpenHelper.open(client, this, room);
    }

    /**
     * Closed unless the chest should be showing open, and then open at whatever the gold inside comes
     * to. An empty open chest is its own state, so a chest with nothing in it still opens its lid
     * rather than reading as shut.
     */
    @Override
    protected int visualState() {
        if (!this.showsOpen()) return CLOSED;

        int stored = this.storedCount();
        if (stored <= 0) return OPEN_EMPTY;

        // Three fuller sprites share the ceiling between them, so a chest filled to the brim shows
        // the fullest one rather than stopping a step short.
        int ceiling = Math.max(1, this.contents.getCapacity());
        int level = OPEN_EMPTY + 1 + (int) ((long) (stored - 1) * 3 / ceiling);
        return Math.min(level, OPEN_FULLEST);
    }

    @Override
    protected int storedCount() {
        return this.contents.total(ChestStorage.KIND_CURRENCY);
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) {
        int[] params = settings.getIntParams();
        int currencyType = (params.length > 0) ? params[0] : CURRENCY_CREDITS;
        int amount = (params.length > 1) ? Math.max(0, params[1]) : 0;

        // Re-configuring the chest replaces its pool.
        this.contents = new ChestStorage();
        if (amount > 0) {
            this.contents.add(ChestStorage.KIND_CURRENCY, currencyType, amount);
        }
        return true;
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        int currencyType = CURRENCY_CREDITS;
        int amount = 0;
        for (ChestStorage.Entry e : this.contents.entries()) {
            if (e.kind == ChestStorage.KIND_CURRENCY) {
                currencyType = e.type;
                amount = e.quantity;
                break;
            }
        }

        message.appendBoolean(false);
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString("");
        message.appendInt(2);
        message.appendInt(currencyType);
        message.appendInt(amount);
        message.appendInt(0);
        message.appendInt(CODE);
        message.appendInt(0);
        message.appendInt(0);
    }
}
