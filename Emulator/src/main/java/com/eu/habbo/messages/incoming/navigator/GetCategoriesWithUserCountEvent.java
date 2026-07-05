package com.eu.habbo.messages.incoming.navigator;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.rooms.RoomCategory;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.navigator.NewNavigatorCategoryUserCountComposer;

import java.util.ArrayList;
import java.util.List;

/** Client requests per-category visitor counts (header 3782). */
public class GetCategoriesWithUserCountEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        List<RoomCategory> categories = new ArrayList<>(
                Emulator.getGameEnvironment().getRoomManager().roomCategoriesForHabbo(this.client.getHabbo()));
        this.client.sendResponse(new NewNavigatorCategoryUserCountComposer(categories));
    }
}
