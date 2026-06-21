package com.eu.habbo.threading.runnables;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.games.GameTeamColors;
import com.eu.habbo.habbohotel.items.interactions.games.battlebanzai.InteractionBattleBanzaiSphere;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.outgoing.rooms.items.ItemsDataUpdateComposer;

import java.util.Set;

public class BattleBanzaiTilesFlicker implements Runnable {
    private final Set<HabboItem> items;
    private final GameTeamColors color;
    private final Room room;

    private boolean on = false;
    private int count = 0;

    public BattleBanzaiTilesFlicker(Set<HabboItem> items, GameTeamColors color, Room room) {
        this.items = items;
        this.color = color;
        this.room = room;
    }

    @Override
    public void run() {
        if (this.items == null || this.room == null)
            return;

        int state = 0;
        if (this.on) {
            state = (this.color.type * 3) + 2;
            this.on = false;
        } else {
            this.on = true;
        }

        for (HabboItem item : this.items) {
            item.setExtradata(state + "");
        }

        this.room.sendComposer(new ItemsDataUpdateComposer(this.items).compose());

        if (this.count == 9) {
            for (HabboItem item : this.room.getRoomSpecialTypes().getItemsOfType(InteractionBattleBanzaiSphere.class)) {
                item.setExtradata("0");
                this.room.updateItemState(item);
            }
            return;
        }

        this.count++;

        Emulator.getThreading().run(this, 500);
    }
}
