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
        if (this.result <= 0)
            this.result = (Emulator.getRandom().nextInt(this.maxNumber) + 1);

        this.item.setExtradata(this.result + "");
        this.item.needsUpdate(true);
        Emulator.getThreading().run(this.item);

        this.room.updateItem(this.item);

        if (this.item instanceof InteractionDice) {
            WiredManager.triggerDiceRolled(this.room, this.item);
        }

        if (this.item instanceof InteractionColorWheel) {
            ((InteractionColorWheel) this.item).clearRunnable();
        }
    }
}
