package polaris.messages.incoming.rooms.items.lovelock;

import polaris.hotel.items.interactions.InteractionLoveLock;
import polaris.hotel.rooms.BuildersClubItemIds;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.User;
import polaris.hotel.users.UserItem;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.incoming.rooms.items.RoomItemInputGuard;
import polaris.messages.outgoing.rooms.items.lovelock.LoveLockFurniFinishedComposer;
import polaris.messages.outgoing.rooms.items.lovelock.LoveLockFurniFriendConfirmedComposer;

public class LoveLockStartConfirmEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int itemId = BuildersClubItemIds.fromWire(this.currentRoom(), this.packet.readInt());

        if (!RoomItemInputGuard.isPositiveId(itemId)) return;

        boolean confirmed = this.packet.readBoolean();

        Room room = this.client.getUser().getUserInfo().getCurrentRoom();
        if (room == null) return;

        UserItem item = room.getUserItem(itemId);
        if (!(item instanceof InteractionLoveLock loveLock)) return;

        User self = this.client.getUser();
        if (self == null) return;

        if (!confirmed) {
            loveLock.cancel(self);
            return;
        }

        int selfId = self.getUserInfo().getId();
        int partnerId = 0;

        if (loveLock.userOneId == selfId) {
            loveLock.userOneConfirmed = true;
            partnerId = loveLock.userTwoId;
        } else if (loveLock.userTwoId == selfId) {
            loveLock.userTwoConfirmed = true;
            partnerId = loveLock.userOneId;
        } else {
            return;
        }

        if (partnerId <= 0) return;

        User partner = room.getUser(partnerId);
        if (partner == null || partner.getClient() == null) return;

        if (loveLock.userOneConfirmed && loveLock.userTwoConfirmed) {
            User userOne = room.getUser(loveLock.userOneId);
            User userTwo = room.getUser(loveLock.userTwoId);

            if (userOne != null && userTwo != null && loveLock.lock(userOne, userTwo, room)) {
                userOne.getClient().sendResponse(new LoveLockFurniFinishedComposer(loveLock));
                userTwo.getClient().sendResponse(new LoveLockFurniFinishedComposer(loveLock));
                polaris.hotel.quests.QuestProgressEvents.progress(
                        userOne, polaris.hotel.quests.QuestGoalType.CLOSE_LOVE_LOCK, 1);
                polaris.hotel.quests.QuestProgressEvents.progress(
                        userTwo, polaris.hotel.quests.QuestGoalType.CLOSE_LOVE_LOCK, 1);
            }

            loveLock.resetSession();
            return;
        }

        partner.getClient().sendResponse(new LoveLockFurniFriendConfirmedComposer(loveLock));
    }
}
