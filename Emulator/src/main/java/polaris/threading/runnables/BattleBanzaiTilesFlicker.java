package polaris.threading.runnables;

import java.util.Set;
import polaris.Emulator;
import polaris.hotel.games.GameTeamColors;
import polaris.hotel.items.interactions.games.battlebanzai.InteractionBattleBanzaiSphere;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.UserItem;
import polaris.messages.outgoing.rooms.items.ItemsDataUpdateComposer;

public class BattleBanzaiTilesFlicker implements Runnable {
    private final Set<UserItem> items;
    private final GameTeamColors color;
    private final Room room;

    private boolean on = false;
    private int count = 0;

    public BattleBanzaiTilesFlicker(Set<UserItem> items, GameTeamColors color, Room room) {
        this.items = items;
        this.color = color;
        this.room = room;
    }

    @Override
    public void run() {
        if (this.items == null || this.room == null) return;

        int state = 0;
        if (this.on) {
            state = (this.color.type * 3) + 2;
            this.on = false;
        } else {
            this.on = true;
        }

        for (UserItem item : this.items) {
            item.setExtradata(state + "");
        }

        this.room.sendComposer(new ItemsDataUpdateComposer(this.items).compose());

        if (this.count == 9) {
            for (UserItem item : this.room.getRoomSpecialTypes().getItemsOfType(InteractionBattleBanzaiSphere.class)) {
                item.setExtradata("0");
                this.room.updateItemState(item);
            }
            return;
        }

        this.count++;

        Emulator.getThreading().run(this, 500);
    }
}
