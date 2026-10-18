package com.eu.habbo.messages.incoming.rooms.items;

import com.eu.habbo.habbohotel.items.interactions.wired.chest.ChestCapacity;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.ChestStorage;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.InteractionWiredChest;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.rooms.items.ChestUpgradeResultComposer;

/**
 * Buy capacity levels (AIR {@code WiredChestUpgradeConfirmationView}): {@code int chestId, int
 * levels}. Each level costs {@link #COST_CREDITS} credits and {@link #COST_DIAMONDS} diamonds and adds
 * one AIR capacity step ({@link ChestCapacity}). All or nothing.
 *
 * <p>Answers {@code ChestUpgradeResult} with the official codes: 0 done, 2 past the top level,
 * 4 chest gone, 5 not enough credits, 6 not enough diamonds, 7 not the owner, 10 starter chest. The
 * new capacity level reaches the room with the furni.
 */
public class ChestUpgradeCapacityEvent extends MessageHandler {
    public static final int COST_CREDITS = 10;
    public static final int COST_DIAMONDS = 10;
    public static final int DIAMOND_TYPE = 5;

    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() throws Exception {
        Habbo habbo = this.client.getHabbo();
        if (habbo == null) return;

        Room room = habbo.getHabboInfo().getCurrentRoom();
        if (room == null) return;

        int itemId = this.packet.readInt();
        int levels = this.packet.readInt();

        HabboItem item = room.getHabboItem(itemId);
        if (!(item instanceof InteractionWiredChest chest)) {
            this.refuse(itemId, ChestUpgradeResultComposer.RESULT_CHEST_GONE);
            return;
        }
        if (habbo.getHabboInfo().getId() != chest.getUserId()) {
            this.refuse(itemId, ChestUpgradeResultComposer.RESULT_NOT_THE_OWNER);
            return;
        }
        if (chest.isStarterChest()) {
            this.refuse(itemId, ChestUpgradeResultComposer.RESULT_STARTER_CHEST);
            return;
        }

        ChestStorage c = chest.getContents();
        int kind = chest.chestKind();
        int totalCredits = COST_CREDITS * Math.max(0, levels);
        int totalDiamonds = COST_DIAMONDS * Math.max(0, levels);

        int result;
        synchronized (habbo) {
            synchronized (c) {
                if (levels <= 0 || c.getCapacityLevel() + levels > ChestCapacity.maxLevel(kind, false)) {
                    result = ChestUpgradeResultComposer.RESULT_AT_CAPACITY;
                } else if (habbo.getHabboInfo().getCredits() < totalCredits) {
                    result = ChestUpgradeResultComposer.RESULT_NOT_ENOUGH_CREDITS;
                } else if (habbo.getHabboInfo().getCurrencyAmount(DIAMOND_TYPE) < totalDiamonds) {
                    result = ChestUpgradeResultComposer.RESULT_NOT_ENOUGH_DIAMONDS;
                } else {
                    habbo.giveCredits(-totalCredits);
                    habbo.givePoints(DIAMOND_TYPE, -totalDiamonds);
                    c.upgradeCapacity(levels, kind, false);
                    result = ChestUpgradeResultComposer.RESULT_OK;
                }
            }
        }

        if (result != ChestUpgradeResultComposer.RESULT_OK) {
            this.refuse(itemId, result);
            return;
        }

        chest.persistContents();
        chest.publishSettings(room);
        this.client.sendResponse(new ChestUpgradeResultComposer(itemId, ChestUpgradeResultComposer.RESULT_OK));
    }

    private void refuse(int itemId, int resultCode) {
        this.client.sendResponse(new ChestUpgradeResultComposer(itemId, resultCode));
    }
}
