package com.eu.habbo.habbohotel.items.interactions.wired.chest;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.messages.ServerMessage;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Furni Chest (classnames {@code wf_storage_furni1/2/_starter}). Config-based v1: holds a single furni
 * base-type pool (base item id + quantity), set via its dialog and dispensed by
 * {@code WiredEffectGiveFurniFromChest}. (Multi-type chests are a future extension — the
 * {@link ChestStorage} model already supports many entries.)
 */
public class InteractionWiredChestFurni extends InteractionWiredChest {
    /** Client WiredActionLayoutCode value for the furni-chest dialog. */
    public static final int CODE = 101;

    public InteractionWiredChestFurni(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public InteractionWiredChestFurni(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) {
        int[] params = settings.getIntParams();
        int baseItemId = (params.length > 0) ? params[0] : 0;
        int quantity = (params.length > 1) ? Math.max(0, params[1]) : 0;

        this.contents = new ChestStorage();
        // Only stock a valid, existing base item.
        if (baseItemId > 0 && quantity > 0 && Emulator.getGameEnvironment().getItemManager().getItem(baseItemId) != null) {
            this.contents.add(ChestStorage.KIND_FURNI, baseItemId, quantity);
        }
        return true;
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        int baseItemId = 0;
        int quantity = 0;
        for (ChestStorage.Entry e : this.contents.entries()) {
            if (e.kind == ChestStorage.KIND_FURNI) {
                baseItemId = e.type;
                quantity = e.quantity;
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
        message.appendInt(baseItemId);
        message.appendInt(quantity);
        message.appendInt(0);
        message.appendInt(CODE);
        message.appendInt(0);
        message.appendInt(0);
    }
}
