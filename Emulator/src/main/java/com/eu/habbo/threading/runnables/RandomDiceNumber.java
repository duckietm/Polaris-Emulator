package com.eu.habbo.threading.runnables;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.interactions.InteractionColorWheel;
import com.eu.habbo.habbohotel.items.interactions.InteractionDice;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredManager;

public class RandomDiceNumber implements Runnable {
    private final HabboItem item;
    private final Room room;
    private final int maxNumber;
    private int result;

    public RandomDiceNumber(HabboItem item, Room room, int maxNumber) {
        this.item = item;
        this.room = room;
        this.maxNumber = maxNumber;
        this.result = -1;
    }

    public RandomDiceNumber(Room room, HabboItem item, int result) {
        this.item = item;
        this.room = room;
        this.maxNumber = -1;
        this.result = result;
    }

    @Override
    public void run() {
        // A dice picked up or moved to another room while it rolled is left alone: the result used to be written
        // to it and broadcast to a room it was no longer in.
        if (this.room == null || this.room.getHabboItem(this.item.getId()) != this.item) {
            if (this.item instanceof InteractionColorWheel) {
                ((InteractionColorWheel) this.item).clearRunnable();
            }
            return;
        }

        // A face count of 0 or less made nextInt throw and the dice stayed rolling.
        if (this.result <= 0) this.result = (Emulator.getRandom().nextInt(Math.max(1, this.maxNumber)) + 1);

        this.item.setExtradata(this.result + "");
        this.item.needsUpdate(true);
        Emulator.getThreading().run(this.item);

        this.room.updateItem(this.item);

        WiredManager.triggerFurniStateUpdated(this.room, null, this.item, false);

        if (this.item instanceof InteractionDice) {
            WiredManager.triggerDiceRolled(this.room, this.item);
        }

        if (this.item instanceof InteractionColorWheel) {
            ((InteractionColorWheel) this.item).clearRunnable();
        }
    }
}
