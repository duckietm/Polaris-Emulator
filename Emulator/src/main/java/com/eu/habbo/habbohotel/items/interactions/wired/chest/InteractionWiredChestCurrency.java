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
    public static final int CURRENCY_CREDITS = -1;

    public InteractionWiredChestCurrency(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public InteractionWiredChestCurrency(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
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
