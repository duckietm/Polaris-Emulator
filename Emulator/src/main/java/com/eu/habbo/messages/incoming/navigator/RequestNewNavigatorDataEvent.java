package com.eu.habbo.messages.incoming.navigator;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.GameEnvironment;
import com.eu.habbo.habbohotel.rooms.RoomCategory;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.navigator.NewNavigatorCategoryUserCountComposer;
import com.eu.habbo.messages.outgoing.navigator.NewNavigatorCollapsedCategoriesComposer;
import com.eu.habbo.messages.outgoing.navigator.NewNavigatorEventCategoriesComposer;
import com.eu.habbo.messages.outgoing.navigator.NewNavigatorLiftedRoomsComposer;
import com.eu.habbo.messages.outgoing.navigator.NewNavigatorMetaDataComposer;
import com.eu.habbo.messages.outgoing.navigator.NewNavigatorSavedSearchesComposer;
import com.eu.habbo.messages.outgoing.navigator.NewNavigatorSettingsComposer;
import java.util.ArrayList;
import java.util.List;

public class RequestNewNavigatorDataEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        GameEnvironment environment = Emulator.getGameEnvironment();
        this.client.sendResponse(
                new NewNavigatorSettingsComposer(this.client.getHabbo().getHabboStats().navigatorWindowSettings));
        this.client.sendResponse(new NewNavigatorMetaDataComposer());
        this.client.sendResponse(new NewNavigatorLiftedRoomsComposer(
                environment.getNavigatorManager().getLiftedRooms()));
        this.client.sendResponse(new NewNavigatorCollapsedCategoriesComposer());
        this.client.sendResponse(new NewNavigatorSavedSearchesComposer(
                this.client.getHabbo().getHabboInfo().getSavedSearches()));
        this.client.sendResponse(new NewNavigatorEventCategoriesComposer());

        List<RoomCategory> categories =
                new ArrayList<>(environment.getRoomManager().roomCategoriesForHabbo(this.client.getHabbo()));
        this.client.sendResponse(new NewNavigatorCategoryUserCountComposer(categories));
    }
}
