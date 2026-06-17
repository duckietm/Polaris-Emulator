package com.eu.habbo.messages.incoming.rooms.users;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.interactions.InteractionVoteCounter;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnitStatus;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredUserActionType;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.plugin.events.users.UserSignEvent;

public class RoomUserSignEvent extends MessageHandler {
    private static final int MIN_SIGN_ID = 0;
    private static final int MAX_SIGN_ID = 10;

    @Override
    public void handle() throws Exception {
        int signId = this.packet.readInt();

        if (signId < MIN_SIGN_ID || signId > MAX_SIGN_ID)
            return;

        Room room = this.client.getHabbo().getHabboInfo().getCurrentRoom();

        if (room == null)
            return;

        UserSignEvent event = new UserSignEvent(this.client.getHabbo(), signId);
        if (!Emulator.getPluginManager().fireEvent(event).isCancelled()) {
            this.client.getHabbo().getRoomUnit().setStatus(RoomUnitStatus.SIGN, event.sign + "");
            this.client.getHabbo().getHabboInfo().getCurrentRoom().unIdle(this.client.getHabbo());
            WiredManager.triggerUserPerformsAction(room, this.client.getHabbo().getRoomUnit(), WiredUserActionType.SIGN, event.sign);

            int userId = this.client.getHabbo().getHabboInfo().getId();
            for (HabboItem item : room.getRoomSpecialTypes().getItemsOfType(InteractionVoteCounter.class)) {
                if (item instanceof InteractionVoteCounter) {
                    ((InteractionVoteCounter)item).vote(room, userId, signId);
                }
            }
        }
    }
}
