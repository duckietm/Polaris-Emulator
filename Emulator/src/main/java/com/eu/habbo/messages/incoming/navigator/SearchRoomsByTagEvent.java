package com.eu.habbo.messages.incoming.navigator;

import com.eu.habbo.Emulator;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.navigator.PrivateRoomsComposer;

public class SearchRoomsByTagEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        String tag = NavigatorInputGuard.normalizeSearch(this.packet.readString());

        this.client.sendResponse(new PrivateRoomsComposer(Emulator.getGameEnvironment().getRoomManager().getRoomsWithTag(tag)));
    }
}
