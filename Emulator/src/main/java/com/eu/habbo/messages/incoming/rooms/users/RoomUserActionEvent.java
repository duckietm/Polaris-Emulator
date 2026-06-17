package com.eu.habbo.messages.incoming.rooms.users;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUserAction;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.wired.WiredUserActionType;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserActionComposer;
import com.eu.habbo.plugin.events.users.UserIdleEvent;

public class RoomUserActionEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        Room room = this.client.getHabbo().getHabboInfo().getCurrentRoom();
        if (room != null) {
            Habbo habbo = this.client.getHabbo();

            if (this.client.getHabbo().getRoomUnit().getCacheable().get("control") != null) {
                habbo = (Habbo) this.client.getHabbo().getRoomUnit().getCacheable().get("control");

                if (habbo.getHabboInfo().getCurrentRoom() != room) {
                    habbo.getRoomUnit().getCacheable().remove("controller");
                    this.client.getHabbo().getRoomUnit().getCacheable().remove("control");
                    habbo = this.client.getHabbo();
                }
            }

            int action = this.packet.readInt();
            if (!RoomUserInputGuard.isValidAction(action))
                return;

            int wiredAction = 0;

            if (action == 5) {
                UserIdleEvent event = new UserIdleEvent(this.client.getHabbo(), UserIdleEvent.IdleReason.ACTION, true);
                Emulator.getPluginManager().fireEvent(event);

                if (!event.isCancelled()) {
                    if (event.idle) {
                        room.idle(habbo);
                        wiredAction = WiredUserActionType.RELAX;
                    } else {
                        room.unIdle(habbo);
                        wiredAction = WiredUserActionType.AWAKE;
                    }
                }
            } else {
                UserIdleEvent event = new UserIdleEvent(this.client.getHabbo(), UserIdleEvent.IdleReason.ACTION, false);
                Emulator.getPluginManager().fireEvent(event);

                if (!event.isCancelled()) {
                    if (!event.idle) {
                        room.unIdle(habbo);
                    }
                }

            }

            room.sendComposer(new RoomUserActionComposer(habbo.getRoomUnit(), RoomUserAction.fromValue(action)).compose());

            if (wiredAction == 0) {
                switch (action) {
                    case 1:
                        wiredAction = WiredUserActionType.WAVE;
                        break;
                    case 2:
                        wiredAction = WiredUserActionType.BLOW_KISS;
                        break;
                    case 3:
                        wiredAction = WiredUserActionType.LAUGH;
                        break;
                    case 7:
                        wiredAction = WiredUserActionType.THUMB_UP;
                        break;
                    default:
                        break;
                }
            }

            if (wiredAction != 0) {
                WiredManager.triggerUserPerformsAction(room, habbo.getRoomUnit(), wiredAction, -1);
            }
        }
    }
}
