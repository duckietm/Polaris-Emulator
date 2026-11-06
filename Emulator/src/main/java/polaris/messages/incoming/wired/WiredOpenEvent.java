package polaris.messages.incoming.wired;

import polaris.hotel.items.interactions.InteractionWiredCondition;
import polaris.hotel.items.interactions.InteractionWiredEffect;
import polaris.hotel.items.interactions.InteractionWiredTrigger;
import polaris.hotel.rooms.BuildersClubItemIds;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.UserItem;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.wired.WiredConditionDataComposer;
import polaris.messages.outgoing.wired.WiredEffectDataComposer;
import polaris.messages.outgoing.wired.WiredTriggerDataComposer;

/**
 * The client asking for the wired box it was told to open. It is the second half of a conversation
 * the server starts: placing a wired box announces it, and this is the client coming back for the
 * settings to fill the window with.
 *
 * <p>The rights are checked here as well as there, because a packet can arrive on its own.
 */
public class WiredOpenEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 250;
    }

    @Override
    public void handle() throws Exception {
        Room room = this.client.getUser().getUserInfo().getCurrentRoom();

        if (room == null || !room.canInspectWired(this.client.getUser())) return;

        UserItem item = room.getUserItem(BuildersClubItemIds.fromWire(this.currentRoom(), this.packet.readInt()));

        if (item == null) return;

        if (item instanceof InteractionWiredTrigger trigger) {
            this.client.sendResponse(new WiredTriggerDataComposer(trigger, room));
        } else if (item instanceof InteractionWiredEffect effect) {
            this.client.sendResponse(new WiredEffectDataComposer(effect, room));
        } else if (item instanceof InteractionWiredCondition condition) {
            this.client.sendResponse(new WiredConditionDataComposer(condition, room));
        }
    }
}
