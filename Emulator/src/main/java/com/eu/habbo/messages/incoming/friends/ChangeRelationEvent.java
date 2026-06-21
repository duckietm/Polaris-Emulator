package com.eu.habbo.messages.incoming.friends;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.messenger.MessengerBuddy;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.friends.UpdateFriendComposer;
import com.eu.habbo.plugin.events.users.friends.UserRelationShipEvent;

public class ChangeRelationEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int userId = this.packet.readInt();
        int relationId = this.packet.readInt();

        MessengerBuddy buddy = this.client.getHabbo().getMessenger().getFriends().get(userId);
        if (buddy != null && FriendInputGuard.isValidRelation(relationId)) {
            UserRelationShipEvent event = new UserRelationShipEvent(this.client.getHabbo(), buddy, relationId);
            if (Emulator.getPluginManager().fireEvent(event).isCancelled())
                return;

            if (!FriendInputGuard.isValidRelation(event.relationShip))
                return;

            buddy.setRelation(event.relationShip);
            this.client.sendResponse(new UpdateFriendComposer(this.client.getHabbo(), buddy, 0));
        }
    }
}
