package com.eu.habbo.messages.incoming.navigator;

import com.eu.habbo.Emulator;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.navigator.PrivateRoomsComposer;

public class RequestPopularRoomsEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        this.packet.readString(); // search query
        this.packet.readInt(); // ad index
        this.client.sendResponse(new PrivateRoomsComposer(Emulator.getGameEnvironment().getRoomManager().getActiveRooms(-1)));
    }
}
