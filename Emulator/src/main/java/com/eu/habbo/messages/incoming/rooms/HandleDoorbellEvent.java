package com.eu.habbo.messages.incoming.rooms;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.hotelview.HotelViewComposer;
import com.eu.habbo.messages.outgoing.rooms.HideDoorbellComposer;
import com.eu.habbo.messages.outgoing.rooms.RoomAccessDeniedComposer;

public class HandleDoorbellEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        if (this.client.getHabbo().getHabboInfo().getCurrentRoom() != null
                && this.client.getHabbo().getHabboInfo().getCurrentRoom().hasRights(this.client.getHabbo())) {
            String username = this.packet.readString();
            boolean accepted = this.packet.readBoolean();

            Habbo habbo = Emulator.getGameServer().getGameClientManager().getHabbo(username);

            if (habbo != null
                    && habbo.getHabboInfo().getRoomQueueId()
                            == this.client
                                    .getHabbo()
                                    .getHabboInfo()
                                    .getCurrentRoom()
                                    .getId()) {
                this.client.getHabbo().getHabboInfo().getCurrentRoom().removeFromQueue(habbo);

                if (accepted) {
                    habbo.getClient()
                            .sendResponse(new HideDoorbellComposer(
                                    this.client
                                            .getHabbo()
                                            .getHabboInfo()
                                            .getCurrentRoom()
                                            .getId(),
                                    ""));
                    Emulator.getGameEnvironment()
                            .getRoomManager()
                            .enterRoom(
                                    habbo,
                                    this.client
                                            .getHabbo()
                                            .getHabboInfo()
                                            .getCurrentRoom()
                                            .getId(),
                                    "",
                                    true);
                } else {
                    habbo.getClient()
                            .sendResponse(new RoomAccessDeniedComposer(
                                    this.client
                                            .getHabbo()
                                            .getHabboInfo()
                                            .getCurrentRoom()
                                            .getId(),
                                    ""));
                    habbo.getClient().sendResponse(new HotelViewComposer());
                }
                habbo.getHabboInfo().setRoomQueueId(0);
            }
        }
    }
}
