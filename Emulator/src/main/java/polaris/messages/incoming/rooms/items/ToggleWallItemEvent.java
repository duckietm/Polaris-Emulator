package polaris.messages.incoming.rooms.items;

import polaris.Emulator;
import polaris.hotel.commands.FurniDataCommand;
import polaris.hotel.rooms.BuildersClubItemIds;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomPlayTestSupport;
import polaris.hotel.users.UserItem;
import polaris.hotel.wired.core.WiredManager;
import polaris.messages.incoming.MessageHandler;
import polaris.plugin.Event;
import polaris.plugin.events.furniture.FurnitureToggleEvent;

public class ToggleWallItemEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 100;
    }

    @Override
    public void handle() throws Exception {
        Room room = this.client.getUser().getUserInfo().getCurrentRoom();

        if (room == null) return;

        int itemId = BuildersClubItemIds.fromWire(this.currentRoom(), this.packet.readInt());
        int state = this.packet.readInt();

        if (!RoomItemInputGuard.isPositiveId(itemId)) return;

        UserItem item = room.getUserItem(itemId);

        if (item == null) return;

        if (FurniDataCommand.isInspecting(this.client.getUser())) {
            this.client.getUser().alert(FurniDataCommand.buildItemInfo(item, state));
            return;
        }

        // AIR playtest mode: only furni that everybody may use (usage policy 2) can be used.
        if (!RoomPlayTestSupport.mayUse(this.client.getUser(), item)) return;

        WiredManager.cancelPendingUserClicksFurni(room, this.client.getUser().getRoomUnit(), item);

        Event furnitureToggleEvent = new FurnitureToggleEvent(item, this.client.getUser(), state);
        Emulator.getPluginManager().fireEvent(furnitureToggleEvent);

        if (furnitureToggleEvent.isCancelled()) return;

        if (item.getBaseItem().getName().equalsIgnoreCase("poster")) return;

        item.needsUpdate(true);
        String previousState = item.getExtradata();
        item.onClick(this.client, room, new Object[] {state});
        if (!java.util.Objects.equals(previousState, item.getExtradata())) {
            polaris.hotel.quests.QuestProgressEvents.progress(
                    this.client.getUser(), polaris.hotel.quests.QuestGoalType.USE_FURNI, 1);
        }
        room.updateItem(item);
        Emulator.getThreading().run(item);
    }
}
