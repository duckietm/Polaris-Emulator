package com.eu.habbo.habbohotel.items.interactions.wired.chest;

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

    public InteractionWiredChestFurni(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    /**
     * Player-first: clicking the furni chest opens the player Scrigno UI (item grid + withdraw) instead of
     * the wired-config dialog. Anyone can open if accessOpen; withdraw is gated to room rights server-side
     * (see ChestWithdrawFurniEvent).
     */
    @Override
    public void onClick(GameClient client, Room room, Object[] objects) throws Exception {
        if (client == null || room == null) return;
        if (!this.getContents().isAccessOpen() && !room.hasRights(client.getHabbo())) return;
        ChestOpenHelper.open(client, this, room);
    }

    /**
     * A furni chest has a lid: its sprite is closed or open, and the owner decides which.
     *
     * <p>The default mode opens it while someone is looking inside, which is why the base class tracks
     * who has the window open.
     */
    @Override
    protected int visualState() {
        return this.showsOpen() ? 1 : 0;
    }

    @Override
    protected int storedCount() {
        return this.getContents().furniItemCount();
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) {
        // A chest is filled by deposits only (AIR model). The wired editor used to be able to set its
        // contents outright, which created coins and furni out of nothing; it no longer touches them.
        return true;
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        int baseItemId = 0;
        int quantity = 0;
        for (ChestStorage.Entry e : this.getContents().entries()) {
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
