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

    /** The coin chest sprite draws four progressively fuller piles above empty. */
    private static final int FILL_LEVELS = 4;

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
     * A coin chest has no lid. Its sprite states are fill levels -- the asset draws a different pile
     * for each -- so the state is how full it is rather than whether anyone is looking.
     *
     * <p>Empty is state zero; anything at all shows the first pile, so a chest with one coin in it does
     * not read as empty from across the room.
     */
    @Override
    protected int visualState() {
        int stored = this.storedCount();
        if (stored <= 0) return 0;

        int ceiling = Math.max(1, this.contents.getCapacity());
        int level = 1 + (int) ((long) stored * (FILL_LEVELS - 1) / ceiling);
        return Math.min(level, FILL_LEVELS);
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
