package polaris.messages.incoming.friends;

import polaris.Emulator;
import polaris.hotel.messenger.MessengerBuddy;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.friends.UpdateFriendComposer;
import polaris.plugin.events.users.friends.UserRelationShipEvent;

public class ChangeRelationEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int userId = this.packet.readInt();
        int relationId = this.packet.readInt();

        if (!FriendInputGuard.isPositiveId(userId)) {
            return;
        }

        MessengerBuddy buddy = this.client.getUser().getMessenger().getFriends().get(userId);
        if (buddy != null && FriendInputGuard.isValidRelation(relationId)) {
            UserRelationShipEvent event = new UserRelationShipEvent(this.client.getUser(), buddy, relationId);
            if (Emulator.getPluginManager().fireEvent(event).isCancelled()) return;

            if (!FriendInputGuard.isValidRelation(event.relationShip)) return;

            boolean changed = event.relationShip > 0 && event.relationShip != buddy.getRelation();
            buddy.setRelation(event.relationShip);
            this.client.sendResponse(new UpdateFriendComposer(this.client.getUser(), buddy, 0));
            if (changed) {
                polaris.hotel.quests.QuestProgressEvents.progress(
                        this.client.getUser(), polaris.hotel.quests.QuestGoalType.SET_RELATIONSHIP_STATUS, 1);
            }
        }
    }
}
